package com.sk.bds.ticket.api.service;

import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZUser;
import com.sk.bds.ticket.api.data.model.freshdesk.*;
import com.sk.bds.ticket.api.data.model.ibm.*;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.exception.AppInternalErrorReason;
import com.sk.bds.ticket.api.util.*;
import com.softlayer.api.ApiClient;
import com.softlayer.api.ApiException;
import com.softlayer.api.RestApiClient;
import com.softlayer.api.service.Account;
import com.softlayer.api.service.Hardware;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.container.utility.file.Attachment;
import com.softlayer.api.service.ticket.Update;
import com.softlayer.api.service.virtual.DedicatedHost;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class IbmService implements CspTicketHandler {
    final AppConfig config = AppConfig.getInstance();
    final TicketOperator operator;
    final Queue<String> creationOperatingQueue = new ConcurrentLinkedQueue<>();
    final Queue<String> statusOperatingQueue = new ConcurrentLinkedQueue<>();
    TicketPropertyMapper propertyMapper;

    public IbmService(TicketOperator operator) {
        this.operator = operator;
        initTicketPropertyMapper();
    }

    private void initTicketPropertyMapper() {
        propertyMapper = new TicketPropertyMapper();
        propertyMapper.init(new CloudZCspApiInfo(config.getIbmAccountId(), config.getIbmApiKey()));
    }

    public boolean isLocalStage() {
        return config.getServiceStage().equalsIgnoreCase(DeployStage.local.name());
    }

    public boolean isStagingStage() {
        return config.getServiceStage().equalsIgnoreCase(DeployStage.staging.name());
    }

    public boolean isProductionStage() {
        return config.getServiceStage().equalsIgnoreCase(DeployStage.production.name());
    }

    private boolean isSyncEnabled() {
        return config.isSyncEnabled();
    }

    private boolean isReverseSyncEnabled() {
        return config.isReverseSyncEnabled();
    }

    private boolean isSlaReportEnabled() {
        return config.isSlaReportEnabled();
    }

    private long getTicketSyncTargetTime() {
        return config.getTicketSyncTargetTime();
    }

    private boolean isEscalationCheckEnabled() {
        return config.isEscalationCheckEnabled();
    }

    private boolean isBetaTestEnabled() {
        return config.isBetaTestEnabled();
    }

    private boolean isBetaTester(String email) {
        return config.isBetaTester(email);
    }

    //////////////////////////////
    /////  CspTicketHandler
    //////////////////////////////
    @Override
    public ProcessResult createCspTicket(JSONObject freshdeskTicketData) {
        ProcessResult processResult = ProcessResult.base();
        if (freshdeskTicketData == null) {
            log.error("freshdeskTicketData is null. abort.");
            processResult.addError(AppInternalError.missingParameters("Empty freshdesk ticket data."));
            processResult.onAborted();
            return processResult;
        }
        if (!freshdeskTicketData.has(FreshdeskTicketField.CustomFields)) {
            log.error("freshdeskTicketData not contains custom fields. abort.");
            processResult.addError(AppInternalError.missingParametersByFields(FreshdeskTicketField.CustomFields).note(true));
            processResult.onAborted();
            return processResult;
        }
        JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
        String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
        String cspTicketId = customData.optString(FreshdeskTicketField.CfCspCaseId);
        String csp = customData.optString(FreshdeskTicketField.CfCsp);

        if (!AppConstants.CSP_NAME.equals(csp)) {
            log.error("This ticket is not IBM ticket. aborted.");
            processResult.addError(AppInternalError.conflict("This ticket is not IBM ticket.").note(true));
            processResult.onAborted();
            return processResult;
        }

        log.info("Creating ticket Freshdesk ==> IBM. {} - {} ", freshdeskTicketId, freshdeskTicketData.optString(FreshdeskTicketField.Subject));
        if (!creationOperatingQueue.contains(freshdeskTicketId)) {
            creationOperatingQueue.offer(freshdeskTicketId);
            log.info("Freshdesk ticket's cspTicketId:{}", cspTicketId);

            if (cspTicketId == null || "".equals(cspTicketId)) {
                String createAt = freshdeskTicketData.optString(FreshdeskTicketField.CreatedAt);
                String escalation = customData.optString(FreshdeskTicketField.CfEscalation);
                String cspAccount = customData.optString(FreshdeskTicketField.CfCspAccount);
                if (!TicketUtil.isValidEscalationField(escalation)) {
                    log.error("This ticket is not escalated. " + freshdeskTicketId);
                    processResult.addError(AppInternalError.notEscalated("This ticket is not escalated.").note(true));
                    processResult.onAborted();
                    creationOperatingQueue.remove(freshdeskTicketId);
                    return processResult;
                }
                FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(cspAccount);
                if (!accountField.isValid()) {
                    log.error("This ticket {} has invalid csp account field {}. Aborted.", freshdeskTicketId, cspAccount);
                    processResult.addError(AppInternalError.invalidCspAccount("This ticket has invalid csp account field.<br>Csp Account: " + cspAccount).note(true));
                    processResult.onAborted();
                    creationOperatingQueue.remove(freshdeskTicketId);
                    return processResult;
                }

                /*
                //Disabled. betatester and ticket time were checked before to call this method. (ticket initializer, ticket service)
                if (isBetaTestEnabled()) {
                    if (!isBetaTester(accountField.getEmail())) {
                        log.error("[Beta test mode]: This ticket {} creator is not a beta tester {}. Aborted.", freshdeskTicketId, cspAccount);
                        processResult.addError(AppInternalError.notBetaTester("[Beta test mode]<br>This ticket creator is not a beta tester.<br>Csp Account: " + cspAccount).note(true));
                        processResult.onAborted();
                        creationOperatingQueue.remove(freshdeskTicketId);
                        return processResult;
                    }
                }
                long ticketCreatedTime = TicketUtil.getTimeByFreshdeskTime(createAt);
                long limitTime = getTicketSyncTargetTime();
                if (ticketCreatedTime < limitTime) {
                    log.error("This ticket {} was created before the sync limit time. Aborted.", freshdeskTicketId);
                    processResult.addError(AppInternalError.outOfSyncTargetTimeRange("This ticket was created before the sync limit time.<br>Limit time: " + TicketUtil.getLocalTimeString(new Date(limitTime))).note(true));
                    processResult.onAborted();
                    creationOperatingQueue.remove(freshdeskTicketId);
                    return processResult;
                }
                */

                CloudZCspApiInfo cspApiInfo = null;
                try {
                    cspApiInfo = getCspApiInfoByAccountField(accountField);
                    if (cspApiInfo == null) {
                        log.error("Not found available csp account {} of ticket {}.", accountField.getAccountId(), freshdeskTicketId);
                        processResult.addError(AppInternalError.invalidCspAccount("Not found available csp account. " + accountField.getAccountId()).note(true));
                        processResult.onAborted();
                        creationOperatingQueue.remove(freshdeskTicketId);
                        return processResult;
                    }
                } catch (AppInternalError error) {
                    log.error("Can not get csp api info. {}", error);
                    processResult.addError(error.note(true));
                    processResult.onAborted();
                    creationOperatingQueue.remove(freshdeskTicketId);
                    return processResult;
                }
                ApiClient ibmClient = cspApiInfo.buildApiClient();
                if (ibmClient == null) {
                    log.error("Cannot build ApiClient. ticket freshdeskTicketId:{}, accountId:{}, accessKey:{}", freshdeskTicketId, cspApiInfo.getApiId(), cspApiInfo.coveredKey());
                    processResult.addError(AppInternalError.invalidCspAccount("Unavailable csp account.").note(true));
                    processResult.onAborted();
                    creationOperatingQueue.remove(freshdeskTicketId);
                    return processResult;
                }
                Account.Service accountService = Account.service(ibmClient);
                Ticket ticket = new Ticket();
                long subjectId = propertyMapper.getSubjectIdByOffering(customData.optString(FreshdeskTicketField.CfIbmL2));

                Long accountId = null;
                try {
                    accountId = accountService.getCurrentUser().getAccountId(); // SoftLayer.API를 통한 사용자 조회
                } catch (com.softlayer.api.ApiException e) {
                    log.error("Invalid IP Address for API Key. {}", e); // Invalid IP Address for API key
                    processResult.addError(AppInternalError.cspApiError(e).note(true)); // 에러노트 작성
                    creationOperatingQueue.remove(freshdeskTicketId); // 작업대기열에서 제거
                    return processResult;
                }

                ticket.setAccountId(accountId);
                ticket.setAssignedUser(accountService.getCurrentUser());
                ticket.setSubjectId(subjectId);
                ticket.setTitle(freshdeskTicketData.optString(FreshdeskTicketField.Subject));
                ticket.setNotifyUserOnUpdateFlag(true);

                String localTimeString = TicketUtil.convertFreshdeskTimeToLocalTimeString(createAt);
                if (localTimeString == null) {
                    log.error("Freshdesk conversation time format error. {}", createAt);
                    localTimeString = createAt;
                }

                final String managementTag = String.format("%s[%s:%s,%s]", AppConstants.CSP_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_FRESHDESK, freshdeskTicketId, localTimeString);
                final int managementTagBytes = managementTag.getBytes(StandardCharsets.UTF_8).length;

                String bodyContent = freshdeskTicketData.optString(FreshdeskTicketField.DescriptionHtml);
                //before unescapeHtml4      //<td>                   &amp;lt;label class=&amp;quot; required control-label cf_l1_866474-label &amp;quot; for=&amp;quot;helpdesk_ticket_cf_l1_866474&amp;quot;&amp;gt;대상 서비스 L1&amp;lt;/label&amp;gt;</td>
                //one time unescapeHtml4    //<td>                   &lt;label class=&quot; required control-label cf_l1_866474-label &quot; for=&quot;helpdesk_ticket_cf_l1_866474&quot;&gt;대상 서비스 L1&lt;/label&gt;</td>
                //two time unescapeHtml4    //<td>                   <label class=" required control-label cf_l1_866474-label " for="helpdesk_ticket_cf_l1_866474">대상 서비스 L1</label></td>
                //one time unescapeHtml4 결과가 실제 사용자가 입력한 값과 일치함.
                //실제 화면에 제대로 표시하기 위해서는 unescapeHtml4를 2번 해야함.
                bodyContent = StringEscapeUtils.unescapeHtml4(bodyContent);
                //operates unescapeHtml4 one more time to make complete tag cause
                //bodyContent = StringEscapeUtils.unescapeHtml4(bodyContent);
                bodyContent = TicketUtil.removeDivTag(bodyContent);
                bodyContent = TicketUtil.removeSpanTag(bodyContent);
                bodyContent = TicketUtil.replaceBrStyleTag(bodyContent);
                bodyContent = bodyContent.replaceAll(AppConstants.FRESHDESK_EMPTY_LINE, AppConstants.CSP_LINEFEED);
                bodyContent = bodyContent.replaceAll(AppConstants.FRESHDESK_LINEFEED, AppConstants.CSP_LINEFEED);

                byte[] bodyContentBytes = bodyContent.getBytes(StandardCharsets.UTF_8);
                boolean contentSizeExceeded = (bodyContentBytes.length + managementTagBytes > config.getIbmUpdateBodyMaxLength());
                final int availableContentSize = config.getIbmUpdateBodyMaxLength() - managementTagBytes;
                String ticketBody;
                if (contentSizeExceeded) {
                    String contentPart = Util.substringByBytes(bodyContent, availableContentSize);
                    ticketBody = String.format("%s%s", contentPart, managementTag);
                    bodyContent = bodyContent.substring(contentPart.length()); //티켓 생성 후 나머지 부분은 대화로 작성.
                } else {
                    ticketBody = String.format("%s%s", bodyContent, managementTag);
                }

                Ticket.Service ticketService = Ticket.service(ibmClient);

                try {
                    ticket = ticketService.createStandardTicket(ticket, ticketBody, null, null, null, null, null, null);
                    ticketService = ticket.asService(ibmClient);
                    operator.onLinkedTicketId(freshdeskTicketId, String.valueOf(ticket.getId()));
                    if (contentSizeExceeded) {
                        //티켓 생성 후 나머지 부분은 대화로 작성.
                        boolean hasRemainsContent;
                        String contentPool = bodyContent;
                        do {
                            log.debug("contentPool - size:{} - {}", contentPool.length(), contentPool);
                            byte[] contentBytes = contentPool.getBytes(StandardCharsets.UTF_8);
                            String contentPart;
                            hasRemainsContent = (contentBytes.length > availableContentSize);
                            if (hasRemainsContent) {
                                contentPart = Util.substringByBytes(contentPool, availableContentSize);
                                contentPool = contentPool.substring(contentPart.length());
                            } else {
                                contentPart = contentPool;
                            }

                            String conversationBody = String.format("%s%s", contentPart, managementTag);
                            Update caseUpdate = new Update();
                            caseUpdate.setEntry(conversationBody);
                            try {
                                ticketService.addUpdate(caseUpdate, null);
                            } catch (com.softlayer.api.ApiException e) {
                                log.error("Failed to adding ibm update for remains ticket content. {}", e);
                                processResult.addError(AppInternalError.cspApiError(e).note(true));
                                //hasRemainsContent = false;
                                //break;
                                creationOperatingQueue.remove(freshdeskTicketId);
                                return processResult;
                            }
                        } while (hasRemainsContent);
                    }
                } catch (com.softlayer.api.ApiException e) {
                    //Exception 발생. ErrorNote 작성 필요.
                    log.error(TicketUtil.cspErrorText("Failed to create IBM Ticket. {}"), e);
                    processResult.addError(AppInternalError.cspApiError(e).note(true));
                    creationOperatingQueue.remove(freshdeskTicketId);
                    return processResult;
                }

                //2. Add Attachment
                JSONArray attachmentsArray = freshdeskTicketData.getJSONArray(FreshdeskTicketField.Attachments);
                if (attachmentsArray != null && attachmentsArray.length() > 0) {
                    try {
                        List<Attachment> attachments = buildCspAttachmentsByFreshdeskAttachment(attachmentsArray);
                        if (attachments != null && attachments.size() > 0) {
                            for (Attachment attachment : attachments) {
                                log.info("Freshdesk ==> IBM file attach {}", attachment.getFilename());
                                try {
                                    com.softlayer.api.service.ticket.attachment.File result = ticketService.addAttachedFile(attachment);
                                } catch (com.softlayer.api.ApiException e) {
                                    log.error(TicketUtil.cspErrorText("addAttachedFile failed.{}"), e);
                                    processResult.addError(AppInternalError.cspApiError("CSP api exception occurred.", e).note(true));
                                }
                            }
                        }
                    } catch (AppInternalError e) {
                        log.error("failed to build attachment. {}", e);
                        processResult.addError(e.note(true));
                    }
                }

                //3. Add VirtualGuest/Hardware/DedicatedHost
                String cspDeviceText = customData.optString(FreshdeskTicketField.CfCspDevice);
                if (cspDeviceText != null && cspDeviceText.trim().length() > 2) {
                    String[] deviceInfoArray = cspDeviceText.split(AppConstants.FRESHDESK_CSP_DEVICE_DELIMITER);
                    for (String deviceInfo : deviceInfoArray) {
                        //int count = StringUtils.countMatches(deviceInfo, IbmDevice.ValueDivider);
                        if (deviceInfo.contains(IbmDevice.AttributeDelimiter)) {
                            try {
                                IbmDevice device = new IbmDevice(deviceInfo);
                                log.info("Freshdesk ==> IBM Attachment Device {}", device);
                                if (device.isVirtualGuest()) {
                                    log.info("Freshdesk ==> IBM addAttachedVirtualGuest");
                                    ticketService.addAttachedVirtualGuest(device.getId(), false);
                                } else if (device.isHardware()) {
                                    log.info("Freshdesk ==> IBM addAttachedHardware");
                                    ticketService.addAttachedHardware(device.getId());
                                } else if (device.isDedicatedHost()) {
                                    log.info("Freshdesk ==> IBM addAttachedDedicatedHost");
                                    ticketService.addAttachedDedicatedHost(device.getId());
                                } else if (device.isUnknownType()) {
                                    //Attempts to attach by every device types.
                                    log.info("Unknown device type. Attempts to attach by every device types.");
                                    try {
                                        log.info("Freshdesk ==> IBM addAttachedVirtualGuest");
                                        ticketService.addAttachedVirtualGuest(device.getId(), false);
                                    } catch (com.softlayer.api.ApiException e1) {
                                        log.error(TicketUtil.cspErrorText("addAttachedVirtualGuest failed.{}"), e1);
                                        try {
                                            log.info("Freshdesk ==> IBM addAttachedHardware");
                                            ticketService.addAttachedHardware(device.getId());
                                        } catch (com.softlayer.api.ApiException e2) {
                                            log.error(TicketUtil.cspErrorText("addAttachedHardware failed.{}"), e2);
                                            try {
                                                log.info("Freshdesk ==> IBM addAttachedDedicatedHost");
                                                ticketService.addAttachedDedicatedHost(device.getId());
                                            } catch (com.softlayer.api.ApiException e3) {
                                                log.error(TicketUtil.cspErrorText("Failed to attach the device. Undefined device type. Failed on all device types."));
                                                processResult.addError(AppInternalError.cspApiError("Failed to attach the device.\nUndefined device type.\nFailed on all device types.").note(true));
                                            }
                                        }
                                    }
                                } else {
                                    log.error("Undefined device type.", device.getType());
                                }
                            } catch (IllegalArgumentException e) {
                                log.error("Invalid device information. {} - error: {}", deviceInfo, e);
                                processResult.addError(AppInternalError.conflict("Invalid device information. " + deviceInfo).note(true));
                            } catch (com.softlayer.api.ApiException e) {
                                log.error(TicketUtil.cspErrorText("Failed to attach the device. {}"), e);
                                processResult.addError(AppInternalError.cspApiError("Failed to attach the device.", e).note(true));
                            }
                        } else {
                            log.error("Invalid device information. {}", deviceInfo);
                        }
                    }
                }

                //4.Attach Email
                //Deactivate CC sync Freshdesk -> IBM. some emails are not exists on ibm side.
                /*JSONArray emailArray = ticketObj.optJSONArray(FreshdeskTicketField.CcEmails);
                if (emailArray != null && emailArray.length() > 0) {
                    ArrayList<String> emailList = new ArrayList<>();
                    for (Object data : emailArray) {
                        emailList.add(data.toString());
                    }
                    ticketService.addAttachedAdditionalEmails(emailList);
                }*/
                try {
                    operator.updateEscalationInfo(freshdeskTicketData, String.valueOf(ticket.getId()), ticket.getServiceProviderResourceId());
                } catch (AppInternalError e) {
                    log.error("Failed to update ticket {} escalation information.", freshdeskTicketId);
                    processResult.addError(e.note(true));
                }

                customData.put(FreshdeskTicketField.CfCspCaseId, String.valueOf(ticket.getId()));
                TicketUtil.checkAndReplaceBrandEmail(freshdeskTicketData);
                TicketMetadata ticketMetadata = TicketMetadata.build(freshdeskTicketData, true);
                if (ticketMetadata != null) {
                    operator.addMonitoringTicket(freshdeskTicketId, ticketMetadata);
                    operator.startInstantTicketSync(freshdeskTicketData);
                }
            }
            creationOperatingQueue.remove(freshdeskTicketId);
        } else {
            log.info("Skip ticket creating Freshdesk ==> IBM. Already operating. {} - {} ", freshdeskTicketId, freshdeskTicketData.optString(FreshdeskTicketField.Subject));
        }
        return processResult;
    }

    @Override
    public void createCspConversation(Ticket.Service ibmTicketService, JSONObject freshdeskConversation, TicketMetadata ticketMetadata) throws AppInternalError {
        String conversationId = freshdeskConversation.optString(FreshdeskTicketField.Id);
        String conversationCreateAt = freshdeskConversation.optString(FreshdeskTicketField.CreatedAt);
        String conversationTime = conversationCreateAt;
        String localTimeString = TicketUtil.convertFreshdeskTimeToLocalTimeString(conversationCreateAt);
        if (localTimeString != null) {
            conversationTime = localTimeString;
        }

        final String managementTag = String.format("%s[%s:%s,%s]", AppConstants.CSP_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_FRESHDESK, conversationId, conversationTime);
        final int managementTagBytes = managementTag.getBytes(StandardCharsets.UTF_8).length;

        String bodyContent = freshdeskConversation.optString(FreshdeskTicketField.ConversationBodyHtml);
        bodyContent = StringEscapeUtils.unescapeHtml4(bodyContent);
        bodyContent = TicketUtil.removeDivTag(bodyContent);
        bodyContent = TicketUtil.removeSpanTag(bodyContent);
        bodyContent = TicketUtil.replaceBrStyleTag(bodyContent);
        bodyContent = bodyContent.replaceAll(AppConstants.FRESHDESK_EMPTY_LINE, AppConstants.CSP_LINEFEED);
        bodyContent = bodyContent.replaceAll(AppConstants.FRESHDESK_LINEFEED, AppConstants.CSP_LINEFEED);

        byte[] bodyContentBytes = bodyContent.getBytes(StandardCharsets.UTF_8);
        boolean contentSizeExceeded = (bodyContentBytes.length + managementTagBytes > config.getIbmUpdateBodyMaxLength());
        final int availableContentSize = config.getIbmUpdateBodyMaxLength() - managementTagBytes;
        String conversationBody;
        if (contentSizeExceeded) {
            String contentPart = Util.substringByBytes(bodyContent, availableContentSize);
            conversationBody = String.format("%s%s", contentPart, managementTag);
            bodyContent = bodyContent.substring(contentPart.length()); //나머지 부분.
        } else {
            conversationBody = String.format("%s%s", bodyContent, managementTag);
        }

        Update caseUpdate = new Update();
        caseUpdate.setEntry(conversationBody);
        try {
            log.info("Freshdesk ==> IBM addUpdate - conversationId: {}, conversationTime:{}", conversationId, conversationTime);
            ibmTicketService.addUpdate(caseUpdate, null);
            if (contentSizeExceeded) {
                boolean hasRemainsContent;
                String contentPool = bodyContent;

                do {
                    log.debug("contentPool - size:{} - {}", contentPool.length(), contentPool);
                    byte[] contentBytes = contentPool.getBytes(StandardCharsets.UTF_8);
                    String contentPart;
                    hasRemainsContent = (contentBytes.length > availableContentSize);
                    if (hasRemainsContent) {
                        contentPart = Util.substringByBytes(contentPool, availableContentSize);
                        contentPool = contentPool.substring(contentPart.length());
                    } else {
                        contentPart = contentPool;
                    }

                    conversationBody = String.format("%s%s", contentPart, managementTag);
                    caseUpdate = new Update();
                    caseUpdate.setEntry(conversationBody);
                    ibmTicketService.addUpdate(caseUpdate, null);
                } while (hasRemainsContent);
            }

            if (ticketMetadata != null) {
                long conversationCreatedTime = TicketUtil.getTimeByFreshdeskTime(conversationCreateAt);
                ticketMetadata.onFreshdeskConversationSynced(conversationId, conversationCreatedTime);
            }
        } catch (com.softlayer.api.ApiException e) {
            log.error("Failed to adding ibm update for freshdesk conversation {} of ticket {}. {}", conversationId, ticketMetadata.getFreshdeskTicketId(), e);
            throw AppInternalError.cspApiError("Failed to adding ibm update for freshdesk conversation " + conversationId, e);
        }

        JSONArray attachmentsArray = freshdeskConversation.optJSONArray(FreshdeskTicketField.Attachments);
        if (attachmentsArray != null && attachmentsArray.length() > 0) {
            List<Attachment> attachments = null;
            try {
                attachments = buildCspAttachmentsByFreshdeskAttachment(attachmentsArray);
            } catch (AppInternalError e) {
                log.error(TicketUtil.internalErrorText("Failed to build attachments for IBM. {}"), e);
                throw AppInternalError.cspApiError("Failed to build attachments for IBM", e);
            }
            if (attachments != null && attachments.size() > 0) {
                for (Attachment attachment : attachments) {
                    log.info("Freshdesk ==> IBM file attach - {}", attachment.getFilename());
                    try {
                        com.softlayer.api.service.ticket.attachment.File result = ibmTicketService.addAttachedFile(attachment);
                    } catch (com.softlayer.api.ApiException e) {
                        log.error(TicketUtil.cspErrorText("Failed to addAttachedFile. {}"), e);
                        throw AppInternalError.cspApiError("Failed to addAttachedFile", e);
                    }
                }
            }
        }
    }

    @Override
    public ProcessResult closeCspTicket(CloudZCspApiInfo cspApiInfo, JSONObject freshdeskTicketData, String cspTicketId) {
        ProcessResult processResult = ProcessResult.base();
        if (cspApiInfo == null || freshdeskTicketData == null || cspTicketId == null) {
            log.error("invalid parameter. aborted.");
            processResult.onAborted();
            processResult.addError(AppInternalError.missingParameters());
            return processResult;
        }
        String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
        ApiClient ibmClient = cspApiInfo.buildApiClient();
        if (ibmClient == null) {
            log.error("Cannot build ApiClient. ticket freshdeskTicketId:{}, accountId:{}, accessKey:{}", freshdeskTicketId, cspApiInfo.getApiId(), cspApiInfo.coveredKey());
            processResult.addError(AppInternalError.invalidCspAccount("Unavailable csp account.").note(true));
            processResult.onAborted();
            return processResult;
        }
        if (!statusOperatingQueue.contains(freshdeskTicketId)) {
            statusOperatingQueue.offer(freshdeskTicketId);
            Ticket.Service service = Ticket.service(ibmClient, Long.valueOf(cspTicketId));
            // ibm Status check
            com.softlayer.api.service.ticket.Status status = service.getStatus();
            log.debug("check status : {}", status);
            if (!(status != null && IbmTicketStatus.valueOf(status.getName()).isClosed())) {
                boolean closeNoteExists = false;
                Update last = service.getLastUpdate();
                if (last != null && last.getEntry() != null) {
                    String lastBody = last.getEntry();
                    closeNoteExists = lastBody.contains(AppConstants.CLOSE_NOTE_MESSAGE);
                }
                if (!closeNoteExists) {
                    Update update = new Update();
                    try {
                        JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
                        String resolution = customData.optString(FreshdeskTicketField.CfSolveReason);
                        String localTimeString = TicketUtil.getLocalTimeString(new Date());
                        String bodyContent = String.format("%s%s[%s%s]", AppConstants.CLOSE_NOTE_MESSAGE, AppConstants.CSP_LINEFEED, AppConstants.CLOSE_NOTE_RESOLUTION_PREFIX, resolution);
                        bodyContent = String.format("%s%s[%s:%s]", bodyContent, AppConstants.CSP_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_FRESHDESK, localTimeString);
                        update.setEntry(bodyContent);
                    } catch (IllegalArgumentException | JSONException e) {
                        log.error(TicketUtil.internalErrorText("Failed to build close note for ticket {}. {}"), freshdeskTicketId, e);
                        statusOperatingQueue.remove(freshdeskTicketId);
                        processResult.addError(AppInternalError.internalProcessingError("Failed to build close note", e));
                        return processResult;
                    }
                    try {
                        service.addUpdate(update, null);
                        operator.removeMonitoringTicket(freshdeskTicketId);
                    } catch (com.softlayer.api.ApiException e) {
                        log.error(TicketUtil.cspErrorText("Failed to register to update for close note. {}"), e);
                        statusOperatingQueue.remove(freshdeskTicketId);
                        processResult.addError(AppInternalError.cspApiError(e));
                        return processResult;
                    }
                } else {
                    operator.removeMonitoringTicket(freshdeskTicketId);
                }
            }
        } else {
            log.debug("Operating already. aborted.", freshdeskTicketId);
        }
        return processResult;
    }


    @Override
    public ProcessResult synchronizeTicket(JSONObject freshdeskTicketData, OperationBreaker breaker) {
        //Note.
        //대화 조회(getUpdates)시 Brand Account로 조회할 때와 일반 사용자 Account로 조회할 때의 조회 결과가 다름.
        //파일 첨부시 생성되는 Update "A new file attachment has been added"가 일반 계정에서는 조회되지 않지만 Brand 계정에서는 조회됨.
        //Customer에게 노출되면 안되는 대화도 Brand 계정에는 조회되므로 대화 동기화는 일반 사용자 계정으로 진행.
        ProcessResult processResult = ProcessResult.base();
        if (freshdeskTicketData == null) {
            String errorMessage = String.format("Ticket information is null.");
            processResult.onAborted();
            processResult.addError(AppInternalError.internalProcessingError(errorMessage));
            return processResult;
        }
        String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
        log.info("Attempt to synchronizing. {}", freshdeskTicketId);

        if (!TicketUtil.isValidCsp(freshdeskTicketData)) {
            String errorMessage = String.format("Ticket ticket %s is not IBM Ticket.", freshdeskTicketId);
            log.error(errorMessage);
            processResult.onAborted();
            processResult.addError(AppInternalError.internalProcessingError(errorMessage));
            return processResult;
        }
        TicketUtil.checkAndReplaceBrandEmail(freshdeskTicketData);
        TicketMetadata ticketMetadata = TicketMetadata.build(freshdeskTicketData, true); //operator.getMonitoringTicket(freshdeskTicketId);
        if (ticketMetadata == null) {
            log.error("\n----------------------\nCannot build ticket metadata.");
            processResult.onAborted();
            processResult.addError(AppInternalError.internalProcessingError("Invalid ticket metadata").note(true));
            return processResult;
        }

        log.info("\n----------------------\nticket:{}", ticketMetadata.getFreshdeskTicketId());
        CloudZCspApiInfo cspApiInfo = CloudZService.getCspApiInfo(ticketMetadata.getCspAccountEmail(), ticketMetadata.getCspAccountId());

        if (cspApiInfo == null) {
            log.error("Not found available csp account {} of ticket {}.", ticketMetadata.getCspAccountId(), freshdeskTicketId);
            processResult.onAborted();
            processResult.addError(AppInternalError.invalidCspAccount("Not found available csp account. " + ticketMetadata.getCspAccountId()).note(true));
            return processResult;
        }

        ApiClient ibmClient = cspApiInfo.buildApiClient();
        if (ibmClient == null) {
            log.error("Cannot build ApiClient. ticket freshdeskTicketId:{}, accountId:{}, accessKey:{}", freshdeskTicketId, cspApiInfo.getApiId(), cspApiInfo.coveredKey());
            processResult.addError(AppInternalError.invalidCspAccount("Unavailable csp account.").note(true));
            processResult.onAborted();
            return processResult;
        }
        Ticket.Service ibmTicketService = Ticket.service(ibmClient, Long.valueOf(ticketMetadata.getCspTicketId()));

        // 동기화중, 조회 권한이 없는 경우 (@2022-06-14, TicketId: 41253) 에러노트 작성 기능 추가
        Update ibmCaseBody = null;
        try {
            ibmCaseBody = ibmTicketService.getFirstUpdate(); // com.softlayer.api.ApiException$Unauthorized: Access Denied
        } catch (com.softlayer.api.ApiException e) {
            log.error(TicketUtil.cspErrorText(e.getMessage()), e);
            processResult.addError(AppInternalError.cspApiError(e.getMessage(), e).note(true));
            processResult.onAborted();
            return processResult;
        }

        if (ibmCaseBody == null) {
            log.error("Empty ibm ticket body. {}", ticketMetadata.getCspTicketId());
            processResult.onAborted();
            processResult.addError(AppInternalError.cannotReadTicket("Empty IBM ticket body.").note(true));
            return processResult;
        }

        long ibmCaseBodyId = ibmCaseBody.getId();
        long ibmCaseBodyCreatedTime = ibmCaseBody.getCreateDate().getTimeInMillis();
        if (!isSyncEnabled()) {
            log.warn("Ticket Synchronization changed to disable. cancel synchronization.");
            processResult.onCanceled();
            return processResult;
        }
        if (isOperationCanceled(breaker, processResult)) {
            log.info("synchronization operation is canceled. {}.", freshdeskTicketId);
            return processResult;
        }

        Map<Long, com.softlayer.api.service.ticket.attachment.File> cspAttachedFiles = new ConcurrentHashMap<>(); //IBM 티켓에 등록된 첨부파일 목록. 중복 차단을 위해 File Id를 Key로 맵 생성
        for (com.softlayer.api.service.ticket.attachment.File attachedFile : ibmTicketService.getAttachedFiles()) {
            if (!isAttachedFileFromFreshdesk(attachedFile)) { //Freshdesk에서 첨부된 파일 제외.
                cspAttachedFiles.put(attachedFile.getId(), attachedFile);
            }
        }

        TreeMap<Long, Update> cspOriginatedConversationMap = new TreeMap(); //IBM에 콘솔등을 통해 직접 등록된 대화 목록. (동기화된 대화 제외)
        TreeMap<String, JSONObject> freshdeskOriginatedConversationMap = new TreeMap<>(); //Freshdesk에 직접 등록된 대화 목록. (동기화된 대화 제외)
        List<String> syncedFreshdeskConversations = new ArrayList<>(); //Freshdesk -> IBM에 동기화가 끝난 대화 목록. (동기화 완료된 대화)

        for (Update update : ibmTicketService.getUpdates()) {
            if (ibmCaseBodyId == update.getId()) { //티켓 본문은 동기화할 대화에서 제외.
                removeAttachedFileByUpdateId(cspAttachedFiles, update.getId()); //티켓 본문에 첨부된 파일은 티켓 생성시 동기화 완료하였기에 목록에서 제거.
                continue;
            }
            //cspAttachedFiles 구성시 Freshdesk에서 첨부된 파일은 제외되었으므로, 현재 시점에서는 조회되는 첨부파일이 없음.
            //com.softlayer.api.service.ticket.attachment.File attachedFile = getAttachedFileByUpdateId(cspAttachedFiles, update.getId());
            //if(isAttachedFileFromFreshdesk(attachedFile)) {
            //}
            if (TicketUtil.isAttachmentNote(update)) { ////첨부파일에 대한 Note는 동기화할 대화에서 제외.
                log.info("attachment note. skipped. {}", update.getEntry());
                continue;
            }

            String ibmBodyContent = update.getEntry();
            if (ibmBodyContent == null || !TicketUtil.isTaggedFreshdesk(ibmBodyContent)) { //Conversation is originated on IBM.
                cspOriginatedConversationMap.put(update.getId(), update);
            } else { //Conversation is originated on Freshdesk.
                String conversationId = TicketUtil.getIdFromBodyTag(ibmBodyContent, AppConstants.CREATED_FROM_FRESHDESK, AppConstants.CSP_LINEFEED);
                //String timeString = TicketUtil.getTimeFromBodyTag(ibmBodyContent, AppConstants.CREATED_FROM_FRESHDESK, AppConstants.CSP_LINEFEED);
                if (conversationId != null) {
                    syncedFreshdeskConversations.add(conversationId);
                }
            }
        }

        int freshdeskSyncConversationSize = 0;
        boolean reachedSyncConversationMax = false;
        FreshdeskConversationLoader conversationLoader = FreshdeskConversationLoader.by(freshdeskTicketId);
        while (conversationLoader.hasNext() && !reachedSyncConversationMax) {
            JSONArray freshdeskConversations = null;
            try {
                freshdeskConversations = conversationLoader.next();
            } catch (AppInternalError e) {
                log.error("Freshdesk ticket {} getConversations failed. {}", freshdeskTicketId, e);
                if (e.getErrorReason() == AppInternalErrorReason.FreshdeskApiCallRateLimitExceed) {
                    log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
                } else {
                    e.note(true);
                }
                processResult.addError(e);
                return processResult;
            }
            if (freshdeskConversations == null) {
                log.error(TicketUtil.internalErrorText("Cannot read freshdesk conversations of ticket: {}, page: {}."), freshdeskTicketId, conversationLoader.currentPage());
                break;
            }

            log.info("freshdesk ticket: {} page: {} conversations: {} loaded.", freshdeskTicketId, conversationLoader.currentPage(), freshdeskConversations.length());
            if (freshdeskConversations.length() > 0) {
                for (int aa = 0; aa < freshdeskConversations.length(); aa++) {
                    JSONObject conversation = freshdeskConversations.optJSONObject(aa);
                    String conversationId = conversation.optString(FreshdeskTicketField.Id);
                    String freshdeskBodyHtml = conversation.optString(FreshdeskTicketField.ConversationBodyHtml);
                    if (!isSyncEnabled()) {
                        log.warn("Ticket Synchronization changed to disable. cancel synchronization.");
                        processResult.onCanceled();
                        return processResult;
                    }
                    if (isOperationCanceled(breaker, processResult)) {
                        log.info("synchronization operation is canceled. {}.", freshdeskTicketId);
                        return processResult;
                    }
                    if (conversation.getBoolean(FreshdeskTicketField.Private)) {
                        log.info("Skip freshdesk private conversation. conversationId: {}", conversationId);
                        continue;
                    }
                    if (TicketUtil.isTaggedMonitoring(freshdeskBodyHtml)) {
                        log.info("Skip freshdesk monitoring conversation. conversationId: {}", conversationId);
                        continue;
                    }

                    if (TicketUtil.isTaggedCsp(freshdeskBodyHtml)) { //This Conversation is created from IBM
                        String ibmUpdateIdString = TicketUtil.getIdFromBodyTag(freshdeskBodyHtml, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
                        if (ibmUpdateIdString != null) {
                            long updateId = Long.valueOf(ibmUpdateIdString);
                            cspOriginatedConversationMap.remove(updateId); //remove synced conversation.
                            removeAttachedFileByUpdateId(cspAttachedFiles, updateId); //동기화가 완료된 Update에 첨부된 파일 제거.
                        } else {
                            log.warn("Conversation is created from IBM but update id not found. conversationId: {}, freshdeskBodyHtml: {}", conversationId, freshdeskBodyHtml);
                        }
                        if (freshdeskBodyHtml.contains(AppConstants.IBM_FILE_CONTENT_BODY_HEADER)) { //IBM -> Freshdesk 역방향 동기화 완료된 첨부파일.
                            List<IbmAttachedFileMetadata> metaList = IbmAttachedFileMetadata.getFileMetadataListFromFreshdeskConversationHtmlBody(freshdeskBodyHtml);
                            //IBM 첨부파일 목록에서 동기화 완료된 항목 제거.
                            for (IbmAttachedFileMetadata metadata : metaList) {
                                if (metadata.isAvailableIbmFileId()) {
                                    cspAttachedFiles.remove(metadata.getIbmFileId());
                                } else {
                                    removeAttachedFileByMetadata(cspAttachedFiles, metadata);
                                }
                            }
                        }
                    } else { //This Conversation is created from Freshdesk
                        //String createAt = conversation.optString(FreshdeskTicketField.CreatedAt);
                        //String localTimeString = TicketUtil.convertFreshdeskTimeToLocalTimeString(createAt);
                        if (conversationId.length() > 0 && !syncedFreshdeskConversations.contains(conversationId)) { //Conversation is not synchronized yet.
                            freshdeskOriginatedConversationMap.put(conversationId, conversation);
                        }
                    }
                    //동기화 대상이 되는 대화 개수만 카운팅해야함.
                    freshdeskSyncConversationSize++;
                    if (freshdeskSyncConversationSize >= config.getTicketSyncConversationMax()) {
                        log.info("Stop freshdesk ticket {} conversation loading. Reached maximum conversations {}", freshdeskTicketId, config.getTicketSyncConversationMax());
                        reachedSyncConversationMax = true;
                        break;
                    }
                }
            }
            if (!isSyncEnabled()) {
                log.warn("Ticket Synchronization changed to disable. cancel synchronization.");
                processResult.onCanceled();
                return processResult;
            }
            if (isOperationCanceled(breaker, processResult)) {
                log.info("synchronization operation is canceled. {}.", freshdeskTicketId);
                return processResult;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Util.ignoreException(e);
            }
        }

        ConversationSequencer sequencer = new ConversationSequencer(freshdeskOriginatedConversationMap.values(), cspOriginatedConversationMap.values(), cspAttachedFiles.values());
        Map<com.softlayer.api.service.ticket.attachment.File, AppInternalError> failedAttachments = new ConcurrentHashMap<>();

        while (sequencer.hasNext() && !reachedSyncConversationMax) {
            if (!isSyncEnabled()) {
                log.warn("Ticket Synchronization changed to disable. cancel synchronization.");
                processResult.onCanceled();
                return processResult;
            }
            if (isOperationCanceled(breaker, processResult)) {
                log.info("synchronization operation is canceled. {}.", freshdeskTicketId);
                return processResult;
            }

            ConversationSequencer.ConversationItem conversationItem = sequencer.next();
            switch (conversationItem.getType()) {
                case freshdeskConversation:
                    // Freshdesk ===> IBM
                    JSONObject freshdeskConversation = conversationItem.asFreshdeskConversation();
                    try {
                        createCspConversation(ibmTicketService, freshdeskConversation, ticketMetadata);
                    } catch (AppInternalError e) {
                        log.error("Failed to create to Update on IBM. ticket: {}, conversationId: {}. {}" , freshdeskTicketId, freshdeskConversation.optString(FreshdeskTicketField.Id), e);
                        //Conversation 순서가 섞이면 안되므로 실패하면 다음 Conversation은 동기화 중지.
                        processResult.addError(e.note(true));
                        return processResult;
                    }
                    break;

                case cspConversation:
                    // IBM ===> Freshdesk
                    if (freshdeskSyncConversationSize < config.getTicketSyncConversationMax()) {
                        Update cspConversation = conversationItem.asCspConversation();
                        try {
                            createFreshdeskReplyByUpdate(freshdeskTicketId, cspConversation, ticketMetadata);
                            freshdeskSyncConversationSize++;
                        } catch (AppInternalError e) {
                            log.error("Failed to create to reply on freshdesk. ticket: {}, updateId: {}. {}" , freshdeskTicketId, cspConversation.getId(), e);
                            //Conversation 순서가 섞이면 안되므로 실패하면 다음 Conversation은 동기화 중지.
                            processResult.addError(e.note(true));
                            return processResult;
                        }
                    }
                    break;

                case cspAttachedFile:
                    // IBM ===> Freshdesk
                    if (freshdeskSyncConversationSize < config.getTicketSyncConversationMax()) {
                        com.softlayer.api.service.ticket.attachment.File attachmentFile = conversationItem.asCspAttachedFile();
                        try {
                            createFreshdeskReplyByAttachment(freshdeskTicketId, ibmTicketService, attachmentFile, ticketMetadata);
                            freshdeskSyncConversationSize++;
                        } catch (AppInternalError e) {
                            log.error("Failed to create to reply on freshdesk. ticket: {}, fileName: {}. {}" , freshdeskTicketId, attachmentFile.getFileName(), e);
                            failedAttachments.put(attachmentFile, e);
                            //Conversation 순서가 섞이면 안되므로 실패하면 다음 Conversation은 동기화 중지.
                            processResult.addError(e.note(true));
                            return processResult;
                        }
                    }
                    break;
            }
            if (freshdeskSyncConversationSize >= config.getTicketSyncConversationMax()) {
                log.info("Stop ticket {} conversation creating. Reached maximum conversations {}", freshdeskTicketId, config.getTicketSyncConversationMax());
                reachedSyncConversationMax = true;
                break;
            }
        }

        //동기화 하지 못한 파일에 대한 노트 작성.
        if (failedAttachments.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("some IBM attachments failed to attach to Freshdesk.\n"));
            for (com.softlayer.api.service.ticket.attachment.File failedFile : failedAttachments.keySet()) {
                AppInternalError e = failedAttachments.get(failedFile);
                sb.append(failedFile.getFileName() + " - cause: " + e.getMessage() + "\n");
            }
            processResult.addError(AppInternalError.partialFailure(sb.toString()).note(true));
        }

        operator.updateTicketMetadata(ticketMetadata);

        // Update Status
        //IBM -> Freshdesk
        com.softlayer.api.service.ticket.Status ibmTicketStatus = ibmTicketService.getStatus();
        int fdTicketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
        if (ibmTicketStatus != null && IbmTicketStatus.valueOf(ibmTicketStatus.getName()).isClosed()) {
            //IBM 티켓이 종료 상태이면 Freshdesk 티켓은 무조건 종료 처리.
            //JSONObject freshdeskTicketData = FreshdeskService.getTicket(freshdeskTicketId);
            //int fdTicketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
            if (fdTicketStatus != FreshdeskTicketStatus.Closed) {
                Update closeNote = ibmTicketService.getLastUpdate();
                try {
                    closeFreshdeskTicket(freshdeskTicketId, closeNote);
                } catch (AppInternalError e) {
                    log.error("failed to close freshdesk ticket {}, {}", freshdeskTicketId, e);
                    processResult.addError(e.note(true));
                    return processResult;
                }
            } else {
                operator.removeMonitoringTicket(freshdeskTicketId);
            }
        } else {
            //Freshdesk -> IBM
            //This operation is need while missed web hook event of close ticket.
            //IBM 티켓은 오픈된 상태인데 Freshdesk 티켓이 종료 상태인 경우.
            //	- Freshdesk의 종료시점
            //	- IBM에 Close note 작성 여부.
            if (FreshdeskTicketStatus.isClosed(fdTicketStatus)) {
                long ibmTicketUpdateTime = ibmTicketService.getLastUpdate().getCreateDate().getTimeInMillis();
                long freshdeskTicketUpdateTime = TicketUtil.getTimeByFreshdeskTime(freshdeskTicketData.optString(FreshdeskTicketField.UpdatedAt));
                log.info("ibmTicketUpdateTime:{}, freshdeskTicketUpdateTime:{}", ibmTicketUpdateTime, freshdeskTicketUpdateTime);
                if (freshdeskTicketUpdateTime > ibmTicketUpdateTime) {
                    ProcessResult closeResult = closeCspTicket(cspApiInfo, freshdeskTicketData, ticketMetadata.getCspTicketId());
                    if (!closeResult.isSuccess()) {
                        log.error("failed to close CSP ticket {}, {}", freshdeskTicketId, closeResult.getErrorCauseForErrorNote());
                        processResult.addErrors(closeResult.getErrors());
                        return processResult;
                    }
                }
            } else {
                operator.addMonitoringTicket(freshdeskTicketId, ticketMetadata);
            }
        }
        log.info("\n----------------------\nsynchronizeTicket() end - ticket:{}", freshdeskTicketId);
        TicketSyncLogger.setTicketSyncTime(System.currentTimeMillis());

        if (reachedSyncConversationMax) {
            //동기화 대화 개수가 제한값을 초과한 경우, 동기화 가능한 대화 모두 동기화 후에 에러 반환.
            String errorMessage = String.format("%s ticket conversation has reached a limited number(%d).", freshdeskTicketId, config.getTicketSyncConversationMax());
            log.error(errorMessage);
            processResult.addError(AppInternalError.exceedConversationLimit(config.getTicketSyncConversationMax()).note(true));
        }
        return processResult;
    }

    @Override
    public ProcessResult checkCspNewTicket(OperationBreaker breaker) {
        ProcessResult processResult = ProcessResult.base();
        int totalCount = 0;
        if (!isSyncEnabled() || !isReverseSyncEnabled()) {
            log.info("Synchronization is disabled.");
            processResult.onAborted();
            processResult.addError(AppInternalError.internalProcessingError("Synchronization is disabled."));
            return processResult;
        }
        if (!FreshdeskService.canApiCall()) {
            log.error("Not available Freshdesk API call.");
            processResult.addError(AppInternalError.freshdeskApiCallRateLimitExceed());
            return processResult;
        }

        log.info("started. time:{}\n=================================================", TicketUtil.getLocalTimeString(new Date()));
        if (config.getReverseSyncAccounts() != null && config.getReverseSyncAccounts().size() > 0) {
            for (IbmBrandAccount brandAccount : config.getReverseSyncAccounts()) {
                if (!isSyncEnabled() || !isReverseSyncEnabled()) {
                    log.info("Ticket Synchronization changed to disable. cancel synchronization.");
                    processResult.onCanceled();
                    processResult.addError(AppInternalError.internalProcessingError("Ticket Synchronization changed to disable."));
                    return processResult;
                }
                if (isOperationCanceled(breaker, processResult)) {
                    log.info("csp new ticket checking operation is canceled.");
                    return processResult;
                }

                String brandId = brandAccount.getBrandId();
                TicketTimeRecord recordedLastTicket = TicketSyncLogger.getReverseSyncLastTicketTimeRecord(brandId);
                //final long SoftLayerTimeZoneMissedTime = config.getIbmMissedTimeOffset(); //CST time zone & Central Daylight Time(CDT). IbmTicketLoader에 CST Timezone을 설정하여 Timezone에 의한 오차 수정됨.
                final long SoftLayerApiTimeMargin = config.getIbmSoftLayerApiDelayTime(); ////IBM console에서 새티켓 등록 직후 SoftLayer API로는 티켓이 바로 조회 되지않고, 2~3분 이후에 API로 새로 등록한 티켓이 조회됨. //API Time Margin 적용 필요.
                final long RevisionTime = SoftLayerApiTimeMargin; //SoftLayerTimeZoneMissedTime + SoftLayerApiTimeMargin;
                final long ConfiguredTicketSyncTargetTime = getTicketSyncTargetTime();
                final long LimitTimeMax = System.currentTimeMillis() - RevisionTime;
                log.info("Reverse sync brand: {}, recordedLastTicket: {}", brandId, recordedLastTicket);

                long filterTimeMillis = Math.max((recordedLastTicket.getCreateTime() - RevisionTime), (ConfiguredTicketSyncTargetTime - RevisionTime));
                filterTimeMillis = Math.min(filterTimeMillis, LimitTimeMax);
                Date ticketFilterTime = new Date(filterTimeMillis);

                log.info("Reverse sync start. brand: {}, ticketFilterTime: {}", brandId, ticketFilterTime);
                IbmTicketLoader ticketLoader = IbmTicketLoader.afterTime(brandAccount, ticketFilterTime, false);
                TicketTimeRecord currentLast = new TicketTimeRecord();
                while (ticketLoader.hasNext()) {
                    List<Ticket> ibmBrandTickets = ticketLoader.next();
                    log.info("Reverse sync brand {}, brandOpenedTickets size:{}", brandId, ibmBrandTickets.size());
                    totalCount += ibmBrandTickets.size();
                    for (Ticket ibmTicket : ibmBrandTickets) {
                        //String timeString = Util.getLocalTimeString(ticket.getCreateDate().getTime(), AppConstants.LOCAL_TIME_FORMAT, AppConstants.LOCAL_TIME_ZONE_ID);
                        String cspTicketId = ibmTicket.getId().toString();
                        if (!isSyncEnabled()) {
                            processResult.onCanceled();
                            processResult.addError(AppInternalError.internalProcessingError("Ticket Synchronization changed to disable."));
                            return processResult;
                        }
                        if (isOperationCanceled(breaker, processResult)) {
                            log.info("csp new ticket checking operation is canceled.");
                            return processResult;
                        }
                        if (ibmTicket.getCreateDate().getTimeInMillis() < recordedLastTicket.getCreateTime()) {
                            log.info("ticket {} is older than latest synchronized ticket time. created:{}, recordedLastTicket:{}", cspTicketId, ibmTicket.getCreateDate().getTimeInMillis(), recordedLastTicket);
                            continue;
                        }
                        if (cspTicketId.equals(recordedLastTicket.getTicketId())) {
                            log.info("ticket {} is latest recorded ticket. aborted.", cspTicketId);
                            continue;
                        }

                        if (!operator.isLinkedCspTicket(cspTicketId)) {
                            log.info("Found new IBM ticket {} - {} - {}", cspTicketId, ibmTicket.getServiceProviderResourceId(), ibmTicket.getTitle());
                            try {
                                if (config.isIgnoreTicketCreationByCspTicketId(cspTicketId)) {
                                    log.info("IBM ticket {} ==> Freshdesk ticket creation is ignored. This ticket exists ignore ticket list.", cspTicketId);
                                    continue;
                                } else if (config.isIgnoreTicketCreationByCspTicketTitleEnabled()) {
                                    log.info("Checking ticket title for filtering...");
                                    try {
                                        ApiClient ibmClient = brandAccount.buildApiClient();
                                        Ticket.Service ticketService = Ticket.service(ibmClient, ibmTicket.getId());
                                        Update firstUpdate = ticketService.getFirstUpdate();
                                        if (firstUpdate != null) {
                                            String editorType = firstUpdate.getEditorType();
                                            String ticketTitle = TicketUtil.buildIbmTicketTitle(editorType, ibmTicket.getTitle());
                                            log.info("cspTicketId:{}, editorType:{}, ticketTitle: {}", cspTicketId, editorType, ticketTitle);
                                            if (config.isIgnoreTicketCreationByCspTicketTitle(ticketTitle)) {
                                                log.info("IBM ticket {} ==> Freshdesk ticket creation is ignored. This ticket has not allowed title '{}'.", cspTicketId, ticketTitle);
                                                continue;
                                            }
                                        } else {
                                            log.info("IBM ticket {} ==> Freshdesk ticket creation is ignored. This ticket has invalid ticket body.", cspTicketId);
                                            continue;
                                        }
                                    } catch (Exception e) {
                                        log.error("Failed to check ibm ticket title. {}", e);
                                    }
                                }
                                log.info("Creating ticket IBM ticket {} - {} - {} ==> Freshdesk.", cspTicketId, ibmTicket.getServiceProviderResourceId(), ibmTicket.getTitle());
                                FreshdeskTicketBuilder ticketBuilder = buildFreshdeskTicketBuilder(brandAccount, cspTicketId);
                                ProcessResult creationResult = operator.createFreshdeskTicket(ticketBuilder);
                                //역방향 티켓 생성시 에러가 발생하지 않은 티켓만 레코드에 기록.
                                //개발환경에서 베타테스터가 아닌 티켓이 생성 되지 않았지만 마지막 티켓 정보는 갱신되는 문제 발생.
                                //조건이 맞지 않아서 생성 안한 것인지. 정상생성해야하지만 에러가 발생한 것인지 판단할 필요가 있음.
                                if (creationResult.isSuccess() || creationResult.isRejected()) {
                                    if (ibmTicket.getCreateDate().getTimeInMillis() > currentLast.getCreateTime()) {
                                        currentLast.setTicketId(cspTicketId);
                                        currentLast.setCreateTime(ibmTicket.getCreateDate().getTimeInMillis());
                                    }
                                }
                                if (!creationResult.isSuccess()) {
                                    log.error("Failed to create freshdesk ticket for CSP ticket {}. error:{}", cspTicketId, creationResult.getErrorCauseForErrorNote());
                                    //processResult.addErrors(creationResult.getErrors());
                                    if (creationResult.hasErrorReason(AppInternalErrorReason.FreshdeskApiCallRateLimitExceed)) {
                                        if (currentLast.isNewerThan(recordedLastTicket)) {
                                            TicketSyncLogger.setReverseSyncLatestTicketTime(brandId, currentLast);
                                        }
                                        processResult.addErrors(creationResult.getErrors());
                                        return processResult;
                                    }
                                }
                            } catch (AppInternalError e) {
                                log.error("Failed to create freshdesk ticket for CSP ticket {}. error:{}", cspTicketId, e);
                                //Continues other ticket synchronization.
                                //processResult.addError(e);
                                //return processResult;
                            }
                        } else {
                            log.info("Skip ticket creating IBM ==> Freshdesk. already exists. {} - {} ", cspTicketId, ibmTicket.getTitle());
                        }
                    }
                }
                log.info("Reverse sync complete. brand: {}, recordedLastTicket: {}, currentLast: {}", brandId, recordedLastTicket, currentLast);
                if (currentLast.isNewerThan(recordedLastTicket)) {
                    TicketSyncLogger.setReverseSyncLatestTicketTime(brandId, currentLast);
                }
            }
        }
        log.info("total {} tickets are checked. end time:{}\n=================================================", totalCount, TicketUtil.getLocalTimeString(new Date()));
        return processResult;
    }

    @Override
    public ProcessResult checkCspNewConversation(List<TicketMetadata> monitoringTickets, OperationBreaker breaker) {
        ProcessResult processResult = ProcessResult.base();
        if (monitoringTickets == null) {
            String errorMessage = String.format("Monitoring ticket information is null.");
            processResult.onAborted();
            processResult.addError(AppInternalError.internalProcessingError(errorMessage));
            return processResult;
        }

        log.info("Checking new conversation. monitoring ticket count: {}, tickets: {}", monitoringTickets.size(), TicketUtil.printTicketIdList(monitoringTickets));
        for (TicketMetadata ticketMetadata : monitoringTickets) {
            String freshdeskTicketId = ticketMetadata.getFreshdeskTicketId();
            CloudZCspApiInfo cspApiInfo = CloudZService.getCspApiInfo(ticketMetadata.getCspAccountEmail(), ticketMetadata.getCspAccountId());
            if (cspApiInfo == null) {
                log.error("Not found available csp account {} of ticket {}.", ticketMetadata.getCspAccountId(), freshdeskTicketId);
                continue;
            }

            ApiClient ibmClient = cspApiInfo.buildApiClient();
            if (ibmClient == null) {
                log.error("Cannot build ApiClient. ticket freshdeskTicketId:{}, accountId:{}, accessKey:{}", freshdeskTicketId, cspApiInfo.getApiId(), cspApiInfo.coveredKey());
                continue;
            }

            try {
                Ticket.Service ibmTicketService = Ticket.service(ibmClient, Long.valueOf(ticketMetadata.getCspTicketId()));
                Update ibmCaseBody = ibmTicketService.getFirstUpdate();
                if (ibmCaseBody == null) {
                    log.error("Empty ibm ticket body. {}", ticketMetadata.getCspTicketId());
                    continue;
                }

                long ibmCaseBodyId = ibmCaseBody.getId();
                long ibmCaseBodyCreatedTime = ibmCaseBody.getCreateDate().getTimeInMillis();

                if (!isSyncEnabled()) {
                    log.warn("Ticket Synchronization changed to disable. cancel synchronization.");
                    processResult.onCanceled();
                    return processResult;
                }
                if (isOperationCanceled(breaker, processResult)) {
                    log.info("synchronization operation is canceled. {}.", freshdeskTicketId);
                    return processResult;
                }

                com.softlayer.api.service.ticket.Status ibmTicketStatus = ibmTicketService.getStatus();
                //IBM 티켓이 종료 상태이면 모니터링 티켓 상태 동기화.
                if (ibmTicketStatus != null && IbmTicketStatus.valueOf(ibmTicketStatus.getName()).isClosed()) {
                    operator.synchronizeConversationByCspMonitoring(freshdeskTicketId);
                    continue;
                }

                boolean newConversationFound = false;
                List<Update> cspTicketConversations = ibmTicketService.getUpdates();

                for (Update update : cspTicketConversations) {
                    if (!isSyncEnabled()) {
                        log.warn("Ticket synchronization changed to disable. cancel synchronization for {}.", freshdeskTicketId);
                        processResult.onCanceled();
                        return processResult;
                    }
                    if (isOperationCanceled(breaker, processResult)) {
                        log.info("synchronization operation is canceled. {}.", freshdeskTicketId);
                        return processResult;
                    }

                    String conversationBody = update.getEntry();
                    long conversationCreatedTime = update.getCreateDate().getTimeInMillis();
                    if (conversationBody == null || !TicketUtil.isTaggedFreshdesk(conversationBody)) { //Communication created on IBM side.
                        //Todo. check whether new conversation.
                        if (conversationCreatedTime > ticketMetadata.getCspLatestConversationTime()) {
                            operator.synchronizeConversationByCspMonitoring(freshdeskTicketId);
                            newConversationFound = true;
                            break;
                        }
                    }
                }
                if (!newConversationFound) {
                    List<com.softlayer.api.service.ticket.attachment.File> attachedFiles = ibmTicketService.getAttachedFiles();
                    if (attachedFiles != null && attachedFiles.size() > 0) {
                        for (com.softlayer.api.service.ticket.attachment.File attachedFile : attachedFiles) {
                            if (attachedFile.getCreateDate() != null) {
                                long fileCreatedTime = attachedFile.getCreateDate().getTimeInMillis();
                                if (fileCreatedTime > ticketMetadata.getCspLatestAttachedFileTime()) {
                                    operator.synchronizeConversationByCspMonitoring(freshdeskTicketId);
                                    newConversationFound = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (com.softlayer.api.ApiException e) {
                log.error(TicketUtil.cspErrorText("{}"), e);
            } catch (IllegalArgumentException e) {
                log.error("error: {}", e);
            }
        }
        return processResult;
    }

    @Override
    public FreshdeskTicketBuilder buildFreshdeskTicketBuilder(IbmBrandAccount brandAccount, String cspTicketId) throws AppInternalError {
        try {
            if (brandAccount == null) {
                log.error("invalid parameter. brandAccount is null");
                throw AppInternalError.missingParametersByFields("brandAccount");
            }
            ApiClient client = brandAccount.buildApiClient();
            Ticket.Service ibmTicketService = Ticket.service(client, Long.valueOf(cspTicketId));
            if (ibmTicketService == null) {
                log.error("invalid ibmTicketService for ticket: {}", cspTicketId);
                throw AppInternalError.internalProcessingError("Can not build IBM ticket service instance.");
            }
            Ticket ticket = ibmTicketService.getObject();
            //Update ibmTicketBodyContent = ticket.getFirstUpdate(); //Always is null. Can not get Update from ticket Object. Use to Ticket.Service instance.
            Update firstUpdate = ibmTicketService.getFirstUpdate();
            if (firstUpdate == null || firstUpdate.getEntry() == null) {
                log.error("invalid ibm ticket body");
                throw AppInternalError.cannotReadTicket("Empty ticket body.");
            }
            if (TicketUtil.isTaggedFreshdesk(firstUpdate.getEntry())) {
                log.info("Ibm Ticket body is tagged Freshdesk");
                throw AppInternalError.conflict(cspTicketId + " ticket is created by Freshdesk.");
            }

            Long ibmUserCustomerId = ticket.getAssignedUserId();
            if (ibmUserCustomerId == null) {
                ibmUserCustomerId = firstUpdate.getEditorId();
            }
            String ibmAccountId = TicketUtil.attachIbmAccountPrefix(ticket.getAccountId());
            String ibmTicketCreatorEmail = CloudZService.getIbmCustomerEmail(brandAccount, ibmUserCustomerId);

            if (isBetaTestEnabled()) {
                if (!isBetaTester(ibmTicketCreatorEmail)) {
                    log.error("This ticket is not beta tester's ticket. aborted. csp ticket id: {}, email:{}", cspTicketId, ibmTicketCreatorEmail);
                    throw AppInternalError.notBetaTester(ibmTicketCreatorEmail + "is not beta tester.");
                }
            }

            FreshdeskTicketBuilder ticketBuilder = new FreshdeskTicketBuilder(brandAccount.getBrandId(), config.getIbmAgentL1Email(), config.getSkSupportPortalUsingRequestMessage(), config.getAgentL1TicketTemplateMessage());
            ticketBuilder.setIbmAccountId(ticket.getAccountId());
            ticketBuilder.setIbmServiceProviderResourceId(ticket.getServiceProviderResourceId());
            if (ticket.getSubjectId() != null) {
                String supportType = propertyMapper.getSupportTypeBySubjectId(ticket.getSubjectId());
                String offering = propertyMapper.getOfferingBySubjectId(ticket.getSubjectId());
                ticketBuilder.setIbmSupportType(supportType);
                ticketBuilder.setIbmOffering(offering);
            }
            ticketBuilder.setIbmCreateDate(firstUpdate.getCreateDate().getTime());
            ticketBuilder.setIbmBody(firstUpdate.getEntry());

            if (isExistsApiKeyOnCloudZ(ibmTicketCreatorEmail, ibmAccountId)) {
                if (isBetaTestEnabled() || isStagingStage()) {
                    if (isBetaTester(ibmTicketCreatorEmail)) {
                        ticketBuilder.setEmail(ibmTicketCreatorEmail);
                        ticketBuilder.setCspAccount(ibmTicketCreatorEmail + "/" + ibmAccountId);
                    } else {
                        ticketBuilder.setEmail(brandAccount.getEmail());
                        ticketBuilder.setCspAccount(brandAccount.getEmail() + "/" + ibmAccountId);
                    }
                } else {
                    ticketBuilder.setEmail(ibmTicketCreatorEmail);
                    ticketBuilder.setCspAccount(ibmTicketCreatorEmail + "/" + ibmAccountId);
                }
            } else {
                CloudZUser masterUser = getCloudZMasterUserByIbmAccountId(ibmAccountId);
                if (masterUser == null || masterUser.getUserEmail() == null) {
                    log.error("Not found master email for IBM account : {}", ibmAccountId);
                    throw AppInternalError.invalidCspAccount("Not found master email for IBM account : " + ibmAccountId);
                }

                if (isBetaTestEnabled() || isStagingStage()) {
                    if (isBetaTester(masterUser.getUserEmail())) {
                        ticketBuilder.setEmail(masterUser.getUserEmail());
                        ticketBuilder.setCspAccount(masterUser.getUserEmail() + "/" + ibmAccountId);
                    } else {
                        ticketBuilder.setEmail(brandAccount.getEmail());
                        ticketBuilder.setCspAccount(brandAccount.getEmail() + "/" + ibmAccountId);
                    }
                } else {
                    ticketBuilder.setEmail(masterUser.getUserEmail());
                    ticketBuilder.setCspAccount(masterUser.getUserEmail() + "/" + ibmAccountId);
                    //ticketCreation.setCcEmails(czUsers.getUserEmails());
                }

                if (ibmTicketCreatorEmail != null) {
                    List<String> ccEmails = new ArrayList<>();
                    ccEmails.add(ibmTicketCreatorEmail);
                    ticketBuilder.setCcEmails(ccEmails);
                }
            }

        /*List<AdditionalEmail> emailList = ibmTicketService.getAttachedAdditionalEmails();
        if (emailList != null && emailList.size() > 0) {
            List<String> ccEmails = ticketCreation.getCcEmails();
            if (ccEmails == null) {
                ccEmails = new ArrayList<>();
            }

            for (AdditionalEmail email : emailList) {
                ccEmails.add(email.getEmail());
            }
            ticketCreation.setCcEmails(ccEmails);
        }*/

            ticketBuilder.setStatus(FreshdeskTicketStatus.Open);
            ticketBuilder.setEscalation("Y");
            ticketBuilder.setCsp(AppConstants.CSP_NAME);
            ticketBuilder.setCspTicketId(String.valueOf(cspTicketId));
            String editorType = firstUpdate.getEditorType();
            ticketBuilder.setEditorType(editorType);
            ticketBuilder.setSubject(ticket.getTitle());
            if (UnplannedEvents.isUnplannedEvent(ticket.getTitle())) {
                ticketBuilder.setType(FreshdeskTicketType.Failure);
            }

            String localTimeString = TicketUtil.getLocalTimeString(ticketBuilder.getIbmCreateDate());
            String bodyContent = ticketBuilder.getIbmBody();
            bodyContent = StringEscapeUtils.escapeHtml4(bodyContent); //html tag가 포함된 본문 등록시 freshdesk api에 의해 html validation 과정에 본문 내용이 잘리며, 관리 태그가 누락되는 문제가 있으므로 html tag escape 처리.
            bodyContent = bodyContent.replaceAll(AppConstants.CSP_LINEFEED, AppConstants.FRESHDESK_LINEFEED);
            ticketBuilder.setDescription(String.format("%s%s[%s:%s,%s]", bodyContent, AppConstants.FRESHDESK_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_CSP, brandAccount.getBrandId(), localTimeString));
            List<com.softlayer.api.service.ticket.attachment.File> attachedFiles = ibmTicketService.getAttachedFiles();
            if (attachedFiles != null && attachedFiles.size() > 0) {
                long ticketBodyUpdateId = firstUpdate.getId();
                long bodyCreatedTime = firstUpdate.getCreateDate().getTimeInMillis();
                List<com.softlayer.api.service.ticket.attachment.File> ticketBodyFiles = new ArrayList<>();
                for (com.softlayer.api.service.ticket.attachment.File file : attachedFiles) {
                    long fileCreatedTime = 0;
                    if (file.getCreateDate() != null) {
                        fileCreatedTime = file.getCreateDate().getTimeInMillis();
                    }
                    if ((file.getUpdateId() == ticketBodyUpdateId) || (fileCreatedTime == bodyCreatedTime)) {
                        ticketBodyFiles.add(file);
                    }
                }
                if (ticketBodyFiles.size() > 0) {
                    try {
                        List<FreshdeskAttachment> attachments = buildFreshdeskAttachments(ibmTicketService, ticketBodyFiles);
                        ticketBuilder.setAttachments(attachments);
                    } catch (AppInternalError e) {
                        //첨부파일 오류는 대화 동기화시 처리될 수 있으므로 오류 반환 안함.
                        log.error("Failed to get ibm ticket attachment. {}", e);
                    }
                }
            }

            List<IbmDevice> attachedDevices = new ArrayList<>();
            List<com.softlayer.api.service.virtual.Guest> virtualGuests = ibmTicketService.getAttachedVirtualGuests();
            if (virtualGuests != null && virtualGuests.size() > 0) {
                for (com.softlayer.api.service.virtual.Guest guest : virtualGuests) {
                    attachedDevices.add(new IbmDevice(guest));
                }
            }

            List<Hardware> hardwareList = ibmTicketService.getAttachedHardware();
            if (hardwareList != null && hardwareList.size() > 0) {
                for (Hardware hardware : hardwareList) {
                    attachedDevices.add(new IbmDevice(hardware));
                }
            }

            List<DedicatedHost> dedicatedHosts = ibmTicketService.getAttachedDedicatedHosts();
            if (dedicatedHosts != null && dedicatedHosts.size() > 0) {
                for (DedicatedHost host : dedicatedHosts) {
                    attachedDevices.add(new IbmDevice(host));
                }
            }

            if (attachedDevices.size() > 0) {
                ticketBuilder.setIbmAttachedDevices(attachedDevices);
                ticketBuilder.setCspDevice(IbmDevice.join(AppConstants.FRESHDESK_CSP_DEVICE_DELIMITER, IbmDevice.AttributeDelimiter, attachedDevices));
            }
            return ticketBuilder;
        } catch (com.softlayer.api.ApiException e) {
            log.error(TicketUtil.cspErrorText("Cannot build to ticket creation. {}"), e);
            throw AppInternalError.cspApiError(e);
        }
    }

    //////////////////////////////
    /////  Internal Methods
    //////////////////////////////
    private boolean isOperationCanceled(OperationBreaker breaker, ProcessResult processResult) {
        if (breaker != null && breaker.isCanceled()) {
            log.warn("received a operation cancellation request.");
            breaker.onCanceled();
            if (processResult != null) {
                processResult.onCanceled();
            }
            return true;
        }
        return false;
    }

    private void closeFreshdeskTicket(String freshdeskTicketId, Update closeNote) throws AppInternalError {
        log.info("freshdeskTicketId:" + freshdeskTicketId);
        String solveReasonText;
        if (closeNote != null) {
            String closeNoteText = closeNote.getEntry();
            solveReasonText = getSolveReasonTextFromIbmCloseNote(closeNoteText);
        } else {
            solveReasonText = IbmTicketSolveReason.TheOthers;
        }
        try {
            FreshdeskService.closeTicketForIbm(freshdeskTicketId, solveReasonText);
            operator.removeMonitoringTicket(freshdeskTicketId);
        } catch (AppInternalError e) {
            log.error("Failed to close ticket {} on freshdesk. {}", freshdeskTicketId, e);
            throw AppInternalError.freshdeskApiError("Failed to close ticket on freshdesk.", e);
        }
    }

    private String getSolveReasonTextFromIbmCloseNote(String closeNoteText) {
        //IBM close note - "Close notes: Client error" or "Close notes: Documentation Error"
        //Freshdesk close note - "Please close the case.\n[Close notes: Client error]\n[AppConstants.CREATED_FROM_FRESHDESK:2020-04-16T15:04:14 +09:00]"
        if (closeNoteText.contains(AppConstants.CLOSE_NOTE_RESOLUTION_PREFIX)) {
            int start = closeNoteText.indexOf(AppConstants.CLOSE_NOTE_RESOLUTION_PREFIX) + AppConstants.CLOSE_NOTE_RESOLUTION_PREFIX.length();
            int end = closeNoteText.indexOf("\n", start);
            if (closeNoteText.contains(AppConstants.CREATED_FROM_FRESHDESK)) {
                end = closeNoteText.indexOf("]", start);
            }
            if (end > start) {
                return closeNoteText.substring(start, end);
            } else {
                return closeNoteText.substring(start);
            }
        }
        return null;
    }

    private com.softlayer.api.service.ticket.attachment.File getAttachedFileByUpdateId(Map<Long, com.softlayer.api.service.ticket.attachment.File> cspAttachedFiles, long updateId) {
        if (cspAttachedFiles != null && cspAttachedFiles.size() > 0) {
            for (long fileId : cspAttachedFiles.keySet()) {
                com.softlayer.api.service.ticket.attachment.File attachedFile = cspAttachedFiles.get(fileId);
                if (attachedFile.isUpdateIdSpecified() && updateId == attachedFile.getUpdateId()) {
                    return attachedFile;
                }
            }
        }
        return null;
    }

    private void removeAttachedFileByUpdateId(Map<Long, com.softlayer.api.service.ticket.attachment.File> cspAttachedFiles, long updateId) {
        if (cspAttachedFiles != null && cspAttachedFiles.size() > 0) {
            for (long fileId : cspAttachedFiles.keySet()) {
                com.softlayer.api.service.ticket.attachment.File attachedFile = cspAttachedFiles.get(fileId);
                if (updateId == attachedFile.getUpdateId()) {
                    cspAttachedFiles.remove(fileId);
                }
            }
        }
    }

    private void removeAttachedFileByMetadata(Map<Long, com.softlayer.api.service.ticket.attachment.File> cspAttachedFiles, IbmAttachedFileMetadata metadata) {
        if (metadata != null && cspAttachedFiles != null && cspAttachedFiles.size() > 0) {
            for (long fileId : cspAttachedFiles.keySet()) {
                com.softlayer.api.service.ticket.attachment.File attachedFile = cspAttachedFiles.get(fileId);
                if (metadata.equals(attachedFile)) {
                    cspAttachedFiles.remove(fileId);
                    return;
                }
            }
        }
    }

    private List<Attachment> buildCspAttachmentsByFreshdeskAttachment(JSONArray attachmentsArray) throws AppInternalError {
        int totalSize = 0;
        int contentSize = 0;
        final int countMax = config.getIbmAttachmentCountMax();
        final int totalSizeMax = config.getIbmAttachmentTotalSizeMax();
        final int contentSizeMax = config.getIbmAttachmentContentSizeMax();
        if (attachmentsArray == null || attachmentsArray.length() < 1) {
            log.error(TicketUtil.internalErrorText("invalid parameter."));
            throw AppInternalError.missingParameters();
        }
        if (attachmentsArray.length() > countMax) {
            //String details = "Select up to 10 files and 20 MB total. Each file should not exceed 8MB.";
            String details = String.format("최대 %d개의 파일을 첨부할 수 있습니다. 현재: %d 개", countMax, attachmentsArray.length());
            throw AppInternalError.attachmentExceedNumber(details);
        }
        for (int i = 0; i < attachmentsArray.length(); i++) {
            JSONObject attachmentInfo = attachmentsArray.getJSONObject(i);
            contentSize = attachmentInfo.optInt(FreshdeskTicketField.AttachmentSize, 0);
            totalSize += contentSize;

            if (totalSize > totalSizeMax || contentSize > contentSizeMax) {
                String details = String.format("최대 %.1f MB의 파일(단일 파일 최대 크기: %.1f MB)을 첨부할 수 있습니다. 현재: %.2f MB",
                        ((float) totalSizeMax / (1024 * 1024)),
                        ((float) contentSizeMax / (1024 * 1024)),
                        ((float) contentSize / (1024 * 1024)));
                throw AppInternalError.attachmentExceedSize(details);
            }
        }

        ArrayList<Attachment> attachments = new ArrayList<>();
        totalSize = 0;
        for (int i = 0; i < attachmentsArray.length(); i++) {
            if (!isSyncEnabled()) {
                log.info("Ticket Synchronization changed to disable. cancel synchronization.");
                break;
            }

            JSONObject attachmentInfo = attachmentsArray.getJSONObject(i);
            String urlString = attachmentInfo.optString(FreshdeskTicketField.AttachmentUrl);
            String fileName = attachmentInfo.optString(FreshdeskTicketField.AttachmentName);
            InputStream is = null;
            ByteArrayOutputStream bOut = null;
            contentSize = 0;
            try {
                URL url = new URL(urlString);
                is = url.openConnection().getInputStream();
                byte[] buff = new byte[1024];
                bOut = new ByteArrayOutputStream();
                int len;
                contentSize = 0;
                while ((len = is.read(buff)) != -1) {
                    bOut.write(buff, 0, len);
                    contentSize += len;
                    if (!isSyncEnabled()) {
                        log.info("Ticket Synchronization changed to disable. cancel synchronization.");
                        break;
                    }
                }
                totalSize += contentSize;

                fileName = TicketUtil.buildFreshdeskTaggedFileName(fileName);
                Attachment attachment = new Attachment();
                attachment.setFilename(fileName);
                attachment.setData(bOut.toByteArray());
                attachments.add(attachment);
            } catch (IOException e) {
                String details = "Failed to download attachment. " + fileName;
                throw AppInternalError.failedToDownloadAttachment(details);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
                if (bOut != null) {
                    try {
                        bOut.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        return attachments;
    }

    private FreshdeskAttachment buildFreshdeskAttachment(Ticket.Service ibmTicketService, com.softlayer.api.service.ticket.attachment.File attachedFile) throws AppInternalError {
        //https://sldn.softlayer.com/reference/services/SoftLayer_Ticket/getAttachedFile/
        //List<com.softlayer.api.service.ticket.attachment.File> attachedFiles = ibmTicketService.getAttachedFiles();
        final long freshdeskAttachmentTotalSizeMax = config.getFreshdeskAttachmentMaxSize();
        if (attachedFile != null) {
            String fileName = attachedFile.getFileName(); //getDeduplicateName(attachments, file.getFileName());
            int fileSize = Integer.valueOf(attachedFile.getFileSize());
            if (fileSize < freshdeskAttachmentTotalSizeMax) {
                try {
                    byte[] fileContent = ibmTicketService.getAttachedFile(attachedFile.getId());
                    if (fileContent != null) {
                        Date createDate = (attachedFile.getCreateDate() != null) ? attachedFile.getCreateDate().getTime() : new Date();
                        Date modifyDate = (attachedFile.getModifyDate() != null) ? attachedFile.getModifyDate().getTime() : new Date();
                        return new FreshdeskAttachment(fileContent, fileName, createDate, modifyDate);
                    } else {
                        throw AppInternalError.cspApiError("Cannot read attachment content for " + attachedFile.getFileName());
                    }
                } catch (com.softlayer.api.ApiException e) {
                    log.error(TicketUtil.cspErrorText("Cannot read attachment content for " + attachedFile.getFileName() + " {}"), e);
                    throw AppInternalError.cspApiError("Cannot read attachment content for " + attachedFile.getFileName(), e);
                }
            } else {
                throw AppInternalError.attachmentExceedSize(String.format("The attachment size (%d) is largger than maximum size (%d).", fileSize, freshdeskAttachmentTotalSizeMax));
            }
        }
        throw AppInternalError.cspApiError("The attachment is null.");
    }

    private List<FreshdeskAttachment> buildFreshdeskAttachments(Ticket.Service ibmTicketService, List<com.softlayer.api.service.ticket.attachment.File> attachedFiles) throws AppInternalError {
        //https://sldn.softlayer.com/reference/services/SoftLayer_Ticket/getAttachedFile/
        //List<com.softlayer.api.service.ticket.attachment.File> attachedFiles = ibmTicketService.getAttachedFiles();
        final long freshdeskAttachmentTotalSizeMax = config.getFreshdeskAttachmentMaxSize();
        int totalContentSize = 0;
        if (attachedFiles != null && attachedFiles.size() > 0) {
            List<FreshdeskAttachment> attachments = new ArrayList<>();
            for (com.softlayer.api.service.ticket.attachment.File attachedFile : attachedFiles) {
                String fileName = attachedFile.getFileName(); //getDeduplicateName(attachments, file.getFileName());
                int fileSize = Integer.valueOf(attachedFile.getFileSize());
                if (!isSyncEnabled()) {
                    log.info("Ticket Synchronization changed to disable. cancel synchronization.");
                    break;
                }
                if (attachedFile.getCreateDate() == null) {
                    log.warn("{} has not createDate. aborted.", attachedFile.getFileName());
                    continue;
                }
                if (isAttachedFileFromFreshdesk(fileName)) {
                    log.warn("{} is attached from freshdesk.. aborted.", attachedFile.getFileName());
                    continue;
                }

                if ((fileSize < freshdeskAttachmentTotalSizeMax) && (totalContentSize + fileSize < freshdeskAttachmentTotalSizeMax)) {
                    try {
                        byte[] fileContent = ibmTicketService.getAttachedFile(attachedFile.getId());
                        if (fileContent != null) {
                            Date createDate = (attachedFile.getCreateDate() != null) ? attachedFile.getCreateDate().getTime() : null;
                            Date modifyDate = (attachedFile.getModifyDate() != null) ? attachedFile.getModifyDate().getTime() : null;
                            //if ((fileContent.length < freshdeskAttachmentTotalSizeMax) && (totalContentSize + fileContent.length < freshdeskAttachmentTotalSizeMax)) {
                            attachments.add(new FreshdeskAttachment(fileContent, fileName, createDate, modifyDate));
                            totalContentSize += fileContent.length;
                            //}
                        }
                    } catch (com.softlayer.api.ApiException e) {
                        log.error(TicketUtil.cspErrorText("Cannot read attachment content for " + attachedFile.getFileName() + " {}"), e);
                        throw AppInternalError.cspApiError("Cannot read attachment content for " + attachedFile.getFileName(), e);
                    }
                }
            }
            return attachments;
        }
        return null;
    }

    private void createFreshdeskReplyByUpdate(String freshdeskTicketId, Update cspConversation, TicketMetadata ticketMetadata) throws AppInternalError {
        if (cspConversation != null) {
            String freshdeskTicketLink = operator.getTicketPublicUrl(freshdeskTicketId);
            if (freshdeskTicketLink == null) {
                freshdeskTicketLink = TicketUtil.buildFreshdeskTicketLink(freshdeskTicketId);
            }
            String localTimeString = TicketUtil.getLocalTimeString(cspConversation.getCreateDate().getTime());
            String bodyContent = cspConversation.getEntry();
            bodyContent = StringEscapeUtils.escapeHtml4(bodyContent); //html tag가 포함된 본문 등록시 freshdesk api에 의해 html validation 과정에 본문 내용이 잘리며, 관리 태그가 누락되는 문제가 있으므로 html tag escape 처리.
            bodyContent = bodyContent.replaceAll(AppConstants.CSP_LINEFEED, AppConstants.FRESHDESK_LINEFEED);
            bodyContent += (AppConstants.FRESHDESK_LINEFEED_TWO_LINE + freshdeskTicketLink);
            bodyContent = String.format("%s%s[%s:%s,%s]", bodyContent, AppConstants.FRESHDESK_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_CSP, String.valueOf(cspConversation.getId()), localTimeString);
            try {
                createFreshdeskReply(freshdeskTicketId, bodyContent, null);
                ticketMetadata.onCspConversationSynced(String.valueOf(cspConversation.getId()), cspConversation.getCreateDate().getTimeInMillis());
            } catch (AppInternalError e) {
                log.error("Failed to create to reply on freshdesk. ticket: {} - {}" , freshdeskTicketId, e);
                throw e;
            }
        }
    }

    private void createFreshdeskReplyByAttachment(String freshdeskTicketId, Ticket.Service ibmTicketService, com.softlayer.api.service.ticket.attachment.File attachmentFile, TicketMetadata ticketMetadata) throws AppInternalError {
        IbmAttachedFileMetadata metadata = new IbmAttachedFileMetadata(attachmentFile);
        try {
            List<FreshdeskAttachment> attachments = new ArrayList<>();
            FreshdeskAttachment attachment = buildFreshdeskAttachment(ibmTicketService, attachmentFile);
            attachments.add(attachment);
            String bodyContent = metadata.buildConversationBodyForAttachment();
            //Ticket attachments. The total size of these attachments cannot exceed 20MB.
            //Attachments associated with the conversation. The total size of all of a ticket's attachments cannot exceed 20MB.
            createFreshdeskReply(freshdeskTicketId, bodyContent, attachments);
            if (attachmentFile.getCreateDate() != null && attachmentFile.getCreateDate().getTimeInMillis() > ticketMetadata.getCspLatestAttachedFileTime()) {
                ticketMetadata.onCspAttachedFileSynched(attachmentFile.getCreateDate().getTimeInMillis());
            }

        } catch (AppInternalError e) {
            log.error("Failed to create attachment's reply on freshdesk. ticket: {} - {}" , freshdeskTicketId, e);
            throw e;
        }
    }

    private void createFreshdeskReply(String freshdeskTicketId, String body, List<FreshdeskAttachment> attachments) throws AppInternalError {
        log.info("freshdeskTicketId:" + freshdeskTicketId);
        if (freshdeskTicketId == null) {
            return;
        }
        JSONObject data = new JSONObject();
        data.put(FreshdeskTicketField.ConversationBodyHtml, body);
        try {
            FreshdeskService.createReply(freshdeskTicketId, data, attachments);
        } catch (AppInternalError e) {
            log.error("Failed to createReply for ticket {}. {}", freshdeskTicketId, e);
            throw AppInternalError.freshdeskApiError("Failed to create to reply on freshdesk.", e);
        }
    }

    private boolean isAttachedFileFromFreshdesk(String fileName) {
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                String namePart = fileName.substring(0, dotIndex);
                //String extPart = fileName.substring(dotIndex + 1);
                if (namePart.endsWith(AppConstants.FRESHDESK_FILE_SUFFIX)) {
                    return true;
                }
            } else {
                if (fileName.endsWith(AppConstants.FRESHDESK_FILE_SUFFIX)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAttachedFileFromFreshdesk(com.softlayer.api.service.ticket.attachment.File attachmentFile) {
        if (attachmentFile != null) {
            return isAttachedFileFromFreshdesk(attachmentFile.getFileName());
        }
        return false;
    }

    private boolean isExistsApiKeyOnCloudZ(String email, String ibmAccountId) {
        return CloudZService.isExistsCspApiInfo(email, ibmAccountId);
    }

    private CloudZUser getCloudZMasterUserByIbmAccountId(String ibmAccountId) {
        return CloudZService.getCloudZMasterUserByAccountId(ibmAccountId);
    }

    private CloudZCspApiInfo getCspApiInfoByAccountField(FreshdeskCspAccountField accountField) throws AppInternalError {
        if (accountField != null && accountField.isValid()) {
            if (TicketUtil.isBrandEmail(accountField.getEmail())) {
                CloudZUser masterUser = getCloudZMasterUserByIbmAccountId(accountField.getAccountId());
                if (masterUser != null && masterUser.getUserEmail() != null) {
                    return getCspApiInfo(masterUser.getUserEmail(), accountField.getAccountId());
                } else {
                    log.error("Not found master email for IBM account : {}", accountField.getAccountId());
                }
            } else {
                return getCspApiInfo(accountField.getEmail(), accountField.getAccountId());
            }
        }
        if (accountField != null) {
            throw AppInternalError.invalidCspAccount("Unavailable csp account - email:" + accountField.getEmail() + ", account:" + accountField.getAccountId());
        } else {
            throw AppInternalError.invalidCspAccount("Unavailable csp account - Empty account information.");
        }
    }

    private CloudZCspApiInfo getCspApiInfo(String email, String apiId) throws AppInternalError {
        CloudZCspApiInfo cspApiInfo = CloudZService.getCspApiInfo(email, apiId);
        if (cspApiInfo == null) {
            throw AppInternalError.notFoundCspAccount();
        }
        return cspApiInfo;
    }

    ///////////////////////// static methods
    public static RestApiClient buildClient(String apiId, String apiKey) {
        if (apiId != null && apiKey != null) {
            try {
                return new RestApiClient().withCredentials(apiId, apiKey);
            } catch (com.softlayer.api.ApiException e) {
                log.error(TicketUtil.cspErrorText("Cannot generate IBM RestApiClient. {}"), e);
            }
        } else {
            log.error("Invalid Account information. accountId:{}", apiId);
        }
        return null;
    }

    public static Ticket.Service ticketService(String apiId, String apiKey, long caseId) {
        ApiClient ibmClient = buildClient(apiId, apiKey);
        if (ibmClient != null) {
            try {
                return Ticket.service(ibmClient, caseId);
            } catch (com.softlayer.api.ApiException e) {
                log.error(TicketUtil.cspErrorText("Cannot generate Ticket.service. {}"), e);
            }
        } else {
            log.error("Invalid Account information. accountId:{}", apiId);
        }
        return null;
    }

    public static Ticket getTicket(String apiId, String apiKey, long caseId) {
        Ticket.Service ticketService = ticketService(apiId, apiKey, caseId);
        if (ticketService != null) {
            try {
                return ticketService.getObject();
            } catch (com.softlayer.api.ApiException e) {
                log.error(TicketUtil.cspErrorText("Cannot read ticket. caseId: {}, {}"), caseId, e);
            }
        } else {
            log.error(TicketUtil.cspErrorText("Cannot build Ibm SoftLayerTicket Service. caseId: {}"), caseId);
        }
        return null;
    }

    public static Ticket getTicket(CloudZCspApiInfo cspApiInfo, long caseId) {
        if (cspApiInfo != null && cspApiInfo.isAvailable()) {
            return getTicket(cspApiInfo.getApiId(), cspApiInfo.getApiKey(), caseId);
        } else {
            log.error(TicketUtil.internalErrorText("Unavailable csp api info. cspApiInfo:{}, caseId: {}"), cspApiInfo, caseId);
        }
        return null;
    }

    public static String getIbmCustomerEmail(String ibmAccountId, String ibmApiKey, long ibmUserCustomerId) {
        final String keyEmail = "email";
        JSONObject customerInfo = getIbmCustomerInfo(ibmAccountId, ibmApiKey, ibmUserCustomerId);
        if (customerInfo != null) {
            if (customerInfo.has(keyEmail)) {
                return customerInfo.getString(keyEmail);
            }
        }
        return null;
    }

    public static JSONObject getIbmCustomerInfo(String ibmAccountId, String ibmApiKey, long ibmUserCustomerId) {
        //final String targetUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_User_Customer/" + ibmUserCustomerId + "/getObject?objectMask=mask[accountId,displayName,email,iamId,id,isMasterUserFlag,username,timezone,userStatus]";
        final String targetUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_User_Customer/" + ibmUserCustomerId + "/getObject?objectMask=mask[accountId,displayName,email,iamId,id,isMasterUserFlag,username]";
        try {
            RestApiUtil.RestApiResult result = RestApiUtil.get(targetUrl, new UsernamePasswordCredentials(ibmAccountId, ibmApiKey));
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            }
            log.error("SoftLayer_User_Customer getting failed. {}", ibmUserCustomerId);
        } catch (Exception e) {
            log.error("SoftLayer_User_Customer getting failed. {} - {}", ibmUserCustomerId, e);
        }
        return null;
    }

    public static JSONObject getIbmAccountInfoByCustomerId(String ibmAccountId, String ibmApiKey, long ibmUserCustomerId) {
        final String targetUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_User_Customer/" + ibmUserCustomerId + "/getAccount?objectMask=mask[id,email,brandId]";
        try {
            RestApiUtil.RestApiResult result = RestApiUtil.get(targetUrl, new UsernamePasswordCredentials(ibmAccountId, ibmApiKey));
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            }
            log.error("SoftLayer_User_Customer getting failed. {}", ibmUserCustomerId);
        } catch (Exception e) {
            log.error("SoftLayer_User_Customer getting failed. {} - {}", ibmUserCustomerId, e);
        }
        return null;
    }

    public static JSONArray getCustomersOfBrand(IbmBrandAccount brandAccount) {
        if (brandAccount != null) {
            //https://api.softlayer.com/rest/v3.1/SoftLayer_Brand/72327/getUsers?objectMask=mask[accountId,id,email,username]
            final String targetUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Brand/" + brandAccount.getBrandId() + "/getUsers?objectMask=mask[accountId,displayName,email,iamId,id,isMasterUserFlag,username]";
            try {
                RestApiUtil.RestApiResult result = RestApiUtil.get(targetUrl, new UsernamePasswordCredentials(brandAccount.getAccountId(), brandAccount.getApiKey()));
                if (result.isOK()) {
                    return new JSONArray(result.getResponseBody());
                }
                log.error("brand customer getting failed. brandId:{} - result:{}, {}", brandAccount.getBrandId(), result.getStatus(), result.getResponseBody());
            } catch (Exception e) {
                log.error("brand customer getting failed. brandId:{} - error: {}", brandAccount.getBrandId(), e);
            }
        }
        return null;
    }

    public static List<IbmCustomer> getCustomerListOfBrand(IbmBrandAccount brandAccount) {
        List<IbmCustomer> customerList = new ArrayList<>();
        if (brandAccount != null) {
            JSONArray customerArray = getCustomersOfBrand(brandAccount);
            if (customerArray != null) {
                for (int i = 0; i < customerArray.length(); i++) {
                    JSONObject customerInfo = customerArray.getJSONObject(i);
                    try {
                        IbmCustomer customer = JsonUtil.unmarshal(customerInfo.toString(), IbmCustomer.class);
                        customerList.add(customer);
                    } catch (IOException e) {
                        log.error("error : {}", e);
                    }
                }
            }
        }
        return customerList;
    }

    public static JSONObject getAccountDevicesByApiKey(String apiId, String apiKey) {
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(apiId, apiKey);
        JSONObject devices = new JSONObject();
        try {
            final String getVirtualGuests = "https://api.softlayer.com/rest/v3.1/SoftLayer_Account/getVirtualGuests";
            RestApiUtil.RestApiResult virtualGuests = RestApiUtil.get(getVirtualGuests, credentials);
            if (virtualGuests.isOK()) {
                JSONArray array = new JSONArray(virtualGuests.getResponseBody());
                devices.put(IbmDevice.DeviceType.VirtualGuest.name(), array);
            }
        } catch (IOException | URISyntaxException | JSONException e) {
            log.error("getVirtualGuests failed.{}", e);
        }

        try {
            final String getHardware = "https://api.softlayer.com/rest/v3.1/SoftLayer_Account/getHardware";
            RestApiUtil.RestApiResult hardware = RestApiUtil.get(getHardware, credentials);
            if (hardware.isOK()) {
                JSONArray array = new JSONArray(hardware.getResponseBody());
                devices.put(IbmDevice.DeviceType.Hardware.name(), array);
            }
        } catch (IOException | URISyntaxException | JSONException e) {
            log.error("getHardware failed.{}", e);
        }

        try {
            final String getDedicatedHosts = "https://api.softlayer.com/rest/v3.1/SoftLayer_Account/getDedicatedHosts";
            RestApiUtil.RestApiResult dedicatedHosts = RestApiUtil.get(getDedicatedHosts, credentials);
            if (dedicatedHosts.isOK()) {
                JSONArray array = new JSONArray(dedicatedHosts.getResponseBody());
                devices.put(IbmDevice.DeviceType.DedicatedHost.name(), array);
            }
        } catch (IOException | URISyntaxException | JSONException e) {
            log.error("getDedicatedHosts failed.{}", e);
        }
        return devices;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Debugging API
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void printTicket(Ticket ticket) {
        if (ticket != null) {
            log.debug("------------------------------- Ibm Ticket Information -------------------------------");
            log.debug("id - {}", ticket.getId());
            log.debug("getTitle - {}", ticket.getTitle());
            log.debug("getCreateDate - {}", ticket.getCreateDate().getTime());
            log.debug("getAccountId - {}", ticket.getAccountId());
            log.debug("getAccount - {}", ticket.getAccount());
            log.debug("getTotalUpdateCount - {}", ticket.getTotalUpdateCount());
            log.debug("getAttachedFiles - {}", ticket.getAttachedFiles());
            log.debug("getAttachedFileCount - {}", ticket.getAttachedFileCount());
            log.debug("getAssignedAgentCount - {}", ticket.getAssignedAgentCount());
            log.debug("getAssignedAgents - {}", ticket.getAssignedAgents());
            log.debug("getAssignedUserId - {}", ticket.getAssignedUserId());
            log.debug("getAssignedUser - {}", ticket.getAssignedUser());
            log.debug("getAttachedAdditionalEmailCount - {}", ticket.getAttachedAdditionalEmailCount());
            log.debug("getAttachedAdditionalEmails - {}", ticket.getAttachedAdditionalEmails());
            log.debug("getAttachedDedicatedHostCount - {}", ticket.getAttachedDedicatedHostCount());
            log.debug("getAttachedDedicatedHosts - {}", ticket.getAttachedDedicatedHosts());
            log.debug("getAttachedHardwareCount - {}", ticket.getAttachedHardwareCount());
            log.debug("getAttachedHardware - {}", ticket.getAttachedHardware());
            log.debug("getPriority - {}", ticket.getPriority());
            log.debug("getEmployeeAttachmentCount - {}", ticket.getEmployeeAttachmentCount());
            log.debug("getEmployeeAttachments - {}", ticket.getEmployeeAttachments());
            log.debug("getAttachedResourceCount - {}", ticket.getAttachedResourceCount());
            log.debug("getAttachedResources - {}", ticket.getAttachedResources());
            log.debug("getAttachedVirtualGuestCount - {}", ticket.getAttachedVirtualGuestCount());
            log.debug("getAttachedVirtualGuests - {}", ticket.getAttachedVirtualGuests());
            log.debug("getGroup - {}", ticket.getGroup());
            log.debug("getGroupId - {}", ticket.getGroupId());
            log.debug("getStateCount - {}", ticket.getStateCount());
            log.debug("getState - {}", ticket.getState());
            log.debug("getStatusId - {}", ticket.getStatusId());
            log.debug("getStatus - {}", ticket.getStatus());
            log.debug("getSubjectId - {}", ticket.getSubjectId());
            log.debug("getSubject - {}", ticket.getSubject());
            log.debug("getServiceProviderId - {}", ticket.getServiceProviderId());
            log.debug("getServiceProviderResourceId - {}", ticket.getServiceProviderResourceId());
            log.debug("getAttachedDedicatedHostCount - {}", ticket.getAttachedDedicatedHostCount());
            log.debug("getAttachedDedicatedHosts - {}", ticket.getAttachedDedicatedHosts());
            log.debug("--------------------------------------------------------------");
        }
    }

    public static void printIbmUpdate(Update update) {
        if (update != null) {
            log.debug("------------------------------- Ibm Update Information -------------------------------");
            log.debug("id - {}", update.getId());
            log.debug("entry - {}", update.getEntry());
            log.debug("editorId - {}", update.getEditorId());
            log.debug("editorType - {}", update.getEditorType());
            log.debug("createDate - {}", update.getCreateDate().getTime());
            log.debug("fileAttachment - {}", update.getFileAttachment());
            log.debug("fileAttachmentCount - {}", update.getFileAttachmentCount());
            log.debug("ticketId - {}", update.getTicketId());
            if (update.getType() != null) {
                log.debug("type {} - {}", update.getType().getKeyName(), update.getType().getDescription());
            } else {
                log.debug("type - null");
            }
            log.debug("--------------------------------------------------------------");
        }
    }

    public static void printIbmAttachmentFile(com.softlayer.api.service.ticket.attachment.File file) {
        if (file != null) {
            log.debug("------------------------------- Ibm Attachment File Information -------------------------------");
            log.debug("ticketId - {}", file.getTicketId());
            log.debug("getUpdateId - {}", file.getUpdateId());
            log.debug("getId - {}", file.getId());
            log.debug("getFileName - {}", file.getFileName());
            log.debug("getFileSize - {}", file.getFileSize());
            log.debug("getCreateDate - {}", file.getCreateDate().getTime());
            log.debug("isUpdateIdSpecified - {}", file.isUpdateIdSpecified());
            log.debug("isTicketIdSpecified - {}", file.isTicketIdSpecified());
            log.debug("--------------------------------------------------------------");
        }
    }
}
