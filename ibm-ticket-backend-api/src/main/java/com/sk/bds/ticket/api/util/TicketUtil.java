package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZUser;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskCspAccountField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketBuilder;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.sk.bds.ticket.api.data.model.ibm.IbmAttachedFileMetadata;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.sk.bds.ticket.api.data.model.ibm.IbmTicketEditorType;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.service.CloudZService;
import com.sk.bds.ticket.api.service.FreshdeskService;
import com.sk.bds.ticket.api.service.IbmService;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.ticket.Update;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class TicketUtil {
    /////////////////////////////////////////////////////////////////////////////////
    ////// Common methods for all CSP.
    /////////////////////////////////////////////////////////////////////////////////
    public static boolean isEscalationCheckEnabled() {
        AppConfig config = AppConfig.getInstance();
        return config.isEscalationCheckEnabled();
    }

    public static boolean isValidEscalationField(String escalation) {
        if (isEscalationCheckEnabled()) {
            return "Y".equals(escalation);
        }
        return true;
    }

    public static boolean isValidCsp(JSONObject freshdeskTicketData) {
        if (freshdeskTicketData != null && freshdeskTicketData.has(FreshdeskTicketField.CustomFields)) {
            JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
            String csp = customData.optString(FreshdeskTicketField.CfCsp);
            return isValidCsp(csp);
        }
        return false;
    }

    public static boolean isValidCsp(String csp) {
        if (csp != null) {
            return AppConstants.CSP_NAME.equals(csp);
        }
        return false;
    }

    public static boolean isValidCustomField(JSONObject freshdeskTicketData) {
        if (freshdeskTicketData != null && freshdeskTicketData.has(FreshdeskTicketField.CustomFields)) {
            JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
            String escalation = customData.optString(FreshdeskTicketField.CfEscalation);
            String cspAccount = customData.optString(FreshdeskTicketField.CfCspAccount);
            String csp = customData.optString(FreshdeskTicketField.CfCsp);
            if (!isValidCsp(csp)) {
                log.error("Ticket ticket {} is not IBM Ticket.", freshdeskTicketData.optString(FreshdeskTicketField.Id));
                return false;
            }
            if (!isValidEscalationField(escalation)) {
                log.error("invalid escalation field. ticket id:{}, escalation:{}", freshdeskTicketData.optString(FreshdeskTicketField.Id), escalation);
                return false;
            }
            FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(cspAccount);
            return accountField.isValid();
        }
        return false;
    }

    /**
     * Content Text에서 <div> </div> 태그 코드 삭제.
     *
     * @param sourceString
     */
    public static String removeDivTag(String sourceString) {
        return sourceString.replaceAll("[<](/)?div[^>]*[>]", "");
    }

    /**
     * Content Text에서 <span> </span> 태그 코드 삭제.
     *
     * @param sourceString
     */
    public static String removeSpanTag(String sourceString) {
        return sourceString.replaceAll("[<](/)?span[^>]*[>]", "");
    }

    /**
     * Content Text에서 <br style ...> 태그에서 <br> tag로 치환.
     *
     * @param sourceString
     */
    public static String replaceBrStyleTag(String sourceString) {
        return sourceString.replaceAll("[<]br[^>]*[>]", "<br>");
    }

    /**
     * 티켓 본문 내용에서 티켓 서비스의 태그 정보를 추출.
     *
     * @param contents
     * @param lineFeed
     */
    public static String getLastLineStringForTag(String contents, String lineFeed) {
        if (contents != null && lineFeed != null) {
            //String replacedPool = contents.replaceAll("[" + lineFeed + "]$", ""); //\n => ok. but <br> => failed.
            String replacedPool = new String(contents);
            while (replacedPool.endsWith(lineFeed)) {
                replacedPool = replacedPool.substring(0, replacedPool.length() - lineFeed.length());
            }
            int lastIndex = replacedPool.lastIndexOf(lineFeed);
            if (lastIndex >= 0) {
                String lastLine = replacedPool.substring(lastIndex + lineFeed.length());
                return lastLine;
            }
        }
        return null;
    }

    /**
     * 티켓 본문에 등로된 티켓 서비스의 태그가 특정 태그와 일치하는지 비교.
     *
     * @param body
     * @param tag
     * @param lineFeed
     */
    public static boolean isTaggedBody(String body, String tag, String lineFeed) {
        String lastLineString = getLastLineStringForTag(body, lineFeed);
        if (lastLineString != null && tag != null) {
            final String search = String.format("[\\[]{1}%s:[\\d]*[,]?([\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2})[\\]]{1}", tag);
            Pattern pattern = Pattern.compile(search);
            Matcher matcher = pattern.matcher(lastLineString);
            while (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * CSP 티켓 본문에 등록된 티켓 서비스의 태그에 Freshdesk의 태그 정보가 존재하는지 확인.
     *
     * @param cspBodyContent
     */
    //CREATED_FROM_FRESHDESK tag is attached to ticket on CSP(AWS,IBM,Azure,GCP,NCP, ...) side. Using CSP linefeed(\n).
    public static boolean isTaggedFreshdesk(String cspBodyContent) {
        return isTaggedBody(cspBodyContent, AppConstants.CREATED_FROM_FRESHDESK, AppConstants.CSP_LINEFEED);
    }

    /**
     * Freshdesk 티켓 본문에 등록된 티켓 서비스의 태그에 CSP의 태그 정보가 존재하는지 확인.
     *
     * @param freshdeskBodyHtml
     */
    //CREATED_FROM_XXX tag is attached to ticket on Freshdesk side. Using Freshdesk linefeed(<br>)
    public static boolean isTaggedCsp(String freshdeskBodyHtml) {
        return isTaggedBody(freshdeskBodyHtml, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
    }

    /**
     * Freshdesk 티켓 본문에 등록된 티켓 서비스의 태그에 Monitoring 태그 정보가 존재하는지 확인.
     *
     * @param freshdeskBodyHtml
     */
    //CREATED_FROM_TICKET_MONITORING tag is attached to ticket on Freshdesk side. Using Freshdesk linefeed(<br>)
    public static boolean isTaggedMonitoring(String freshdeskBodyHtml) {
        return isTaggedBody(freshdeskBodyHtml, AppConstants.CREATED_FROM_TICKET_MONITORING, AppConstants.FRESHDESK_LINEFEED);
    }

    /**
     * 티켓 본문에 등록된 티켓 서비스의 태그 정보 추출.
     *
     * @param body
     * @param tag
     * @param lineFeed
     */
    public static TicketMetaTag getTicketMetaTag(String body, String tag, String lineFeed) {
        //[CREATED_FROM_XXX:72323,2020-04-16T15:04:14 +09:00] //brandId
        //[CREATED_FROM_XXX:549417328,2020-04-16T15:04:14 +09:00] //updateId
        //[CREATED_FROM_FRESHDESK:223,2020-04-16T15:04:14 +09:00]
        if (body != null && tag != null && lineFeed != null) {
            String lastLineString = getLastLineStringForTag(body, lineFeed);
            if (lastLineString != null) {
                final String search = String.format("[\\[]{1}%s:([\\d]*),([\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2})[\\]]{1}", tag);
                Pattern pattern = Pattern.compile(search);
                Matcher matcher = pattern.matcher(lastLineString);
                if (matcher.find()) {
                    TicketMetaTag metaTag = new TicketMetaTag();
                    metaTag.setTag(tag);
                    metaTag.setId(matcher.group(1));
                    metaTag.setTime(matcher.group(2));
                    return metaTag;
                }
            }
        }
        return null;
    }

    /**
     * 티켓 본문에 등록된 티켓 서비스의 태그 정보에서 Id 정보를 추출.
     *
     * @param body
     * @param tag
     * @param lineFeed
     */
    public static String getIdFromBodyTag(String body, String tag, String lineFeed) {
        //[CREATED_FROM_XXX:72323,2020-04-16T15:04:14 +09:00] //brandId
        //[CREATED_FROM_XXX:549417328,2020-04-16T15:04:14 +09:00] //updateId
        String foundId = null;
        String lastLineString = getLastLineStringForTag(body, lineFeed);
        if (lastLineString != null && tag != null) {
            final String search = String.format("[\\[]{1}%s:([\\d]*),[\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2}[\\]]{1}", tag);
            Pattern pattern = Pattern.compile(search);
            Matcher matcher = pattern.matcher(lastLineString);
            while (matcher.find()) {
                foundId = matcher.group(matcher.groupCount());
            }
        }
        return foundId;
    }

    /**
     * 티켓 본문에 등록된 티켓 서비스의 태그 정보에서 Time 정보를 추출.
     *
     * @param body
     * @param tag
     * @param lineFeed
     */
    public static String getTimeFromBodyTag(String body, String tag, String lineFeed) {
        //[CREATED_FROM_FRESHDESK:223,2020-04-16T15:04:14 +09:00]
        //[CREATED_FROM_FRESHDESK:2020-04-02T14:58:08 +09:00]
        //[CREATED_FROM_XXX:2020-03-12T11:06:23 +09:00]
        String foundTime = null;
        String lastLineString = getLastLineStringForTag(body, lineFeed);
        if (lastLineString != null && tag != null) {
            String search;
            search = String.format("[\\[]{1}%s:[\\d]*[,]?([\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2})[\\]]{1}", tag);
            Pattern pattern = Pattern.compile(search);
            Matcher matcher = pattern.matcher(lastLineString);
            while (matcher.find()) {
                foundTime = matcher.group(matcher.groupCount());
            }
        }
        return foundTime;
    }

    public static long getTimeByLocalTime(String formattedTimeString) {
        if (formattedTimeString != null) {
            try {
                DateFormat timeFormat = getLocalDateFormat();
                Date timeDate = timeFormat.parse(formattedTimeString);
                return timeDate.getTime();
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", formattedTimeString, e);
            }
        } else {
            log.error("Invalid time. formattedTimeString is null.");
        }
        return 0;
    }

    public static long getTimeByFreshdeskTime(String formattedTimeString) {
        if (formattedTimeString != null) {
            try {
                DateFormat timeFormat = getFreshdeskDateFormat();
                Date timeDate = timeFormat.parse(formattedTimeString);
                return timeDate.getTime();
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", formattedTimeString, e);
            }
        } else {
            log.error("Invalid time. formattedTimeString is null.");
        }
        return 0;
    }

    public static long getTimeByCspTime(String formattedTimeString) {
        if (formattedTimeString != null) {
            DateFormat cspTimeFormat = getCspDateFormat();
            try {
                return cspTimeFormat.parse(formattedTimeString).getTime();
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", formattedTimeString, e);
            }
        } else {
            log.error("Invalid time. formattedTimeString is null.");
        }
        return 0;
    }

    public static String buildFreshdeskTicketLink(String freshdeskTicketId) {
        //https://skcczcareservice.freshdesk.com/support/tickets/1998
        //https://skcczcareservice.freshdesk.com/ko/support/tickets/1998
        //https://skcczcareservice.freshdesk.com/helpdesk/tickets/1998
        AppConfig config = AppConfig.getInstance();
        String ticketUrl = config.getServicePortalEndpoint() + "/support/tickets/" + freshdeskTicketId;
        return String.format("<a href=\"%s\" target=\"_blank\">%s</a>", ticketUrl, ticketUrl);
    }

    public static void createRelatedCspTicketMappingNote(String freshdeskTicketId, String cspTicketId, String cspTicketDisplayId) {
        log.debug("freshdeskTicketId:{}, cspTicketId:{}, cspTicketDisplayId:{}", freshdeskTicketId, cspTicketId, cspTicketDisplayId);
        if (freshdeskTicketId != null && cspTicketId != null && cspTicketDisplayId != null && cspTicketDisplayId.length() > 1) {
            JSONObject data = new JSONObject();
            String message = String.format(AppConstants.CONVERSATION_PREFIX_FOR_SK_CUSTOMER + "IBM ticket has been linked.<br>Case Id: %s<br>Case Display Id: %s", cspTicketId, cspTicketDisplayId);
            String localTimeString = getLocalTimeString(new Date());
            String body = String.format("<div>%s%s[%s:%s]</div>", message, AppConstants.FRESHDESK_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_TICKET_MONITORING, localTimeString);
            data.put(FreshdeskTicketField.ConversationBodyHtml, body);
            data.put(FreshdeskTicketField.Private, false);
            try {
                FreshdeskService.createNote(freshdeskTicketId, data, null);
            } catch (AppInternalError e) {
                log.error(freshdeskErrorText("IBM ticket id mapping note creation failed. {}-{} {}"), cspTicketId, cspTicketDisplayId, e);
            }
        }
    }

    public static void createPublicNote(String freshdeskTicketId, String body) {
        log.debug("freshdeskTicketId:{}, body:{}", freshdeskTicketId, body);
        if (freshdeskTicketId != null && body != null) {
            JSONObject data = new JSONObject();
            data.put(FreshdeskTicketField.ConversationBodyHtml, body);
            data.put(FreshdeskTicketField.Private, false);
            try {
                FreshdeskService.createNote(freshdeskTicketId, data, null);
                log.debug("public note created. freshdeskTicketId:{}", freshdeskTicketId);
            } catch (AppInternalError e) {
                log.error(freshdeskErrorText("Failed to create public note. {}-{} {}"), freshdeskTicketId, body, e);
            }
        }
    }

    public static boolean isExistsTaggedConversation(String freshdeskTicketId, String tag) throws AppInternalError {
        if (freshdeskTicketId != null && tag != null) {
            FreshdeskConversationLoader conversationLoader = FreshdeskConversationLoader.by(freshdeskTicketId);
            while (conversationLoader.hasNext()) {
                JSONArray freshdeskConversations = conversationLoader.next();
                for (int i = 0; i < freshdeskConversations.length(); i++) {
                    JSONObject conversation = freshdeskConversations.getJSONObject(i);
                    String freshdeskBodyHtml = conversation.optString(FreshdeskTicketField.ConversationBodyHtml);
                    boolean found = isTaggedBody(freshdeskBodyHtml, tag, AppConstants.FRESHDESK_LINEFEED);
                    if (found) {
                        log.debug("found tag from {}", freshdeskBodyHtml);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static String internalErrorText(String errorText) {
        return AppConstants.ERROR_HEADER_INTERNAL + errorText;
    }

    public static String cspErrorText(String errorText) {
        return AppConstants.ERROR_HEADER_CSP + errorText;
    }

    public static String freshdeskErrorText(String errorText) {
        return AppConstants.ERROR_HEADER_FRESHDESK + errorText;
    }

    public static String cloudzErrorText(String errorText) {
        return AppConstants.ERROR_HEADER_CLOUDZ + errorText;
    }

    public static String opsgenieErrorText(String errorText) {
        return AppConstants.ERROR_HEADER_OPSGENIE + errorText;
    }

    public static String slackErrorText(String errorText) {
        return AppConstants.ERROR_HEADER_SLACK + errorText;
    }

    public static DateFormat getCspDateFormat() {
        DateFormat cspTimeFormat = new SimpleDateFormat(AppConstants.CSP_TIME_FORMAT);
        cspTimeFormat.setTimeZone(AppConstants.getUTCTimeZone());
        return cspTimeFormat;
    }

    public static DateFormat getFreshdeskDateFormat() {
        DateFormat freshdeskTimeFormat = new SimpleDateFormat(AppConstants.FRESHDESK_TIME_FORMAT);
        freshdeskTimeFormat.setTimeZone(AppConstants.getUTCTimeZone());
        return freshdeskTimeFormat;
    }

    public static Date parseFreshdeskTime(String freshdeskTimeString) {
        if (freshdeskTimeString != null) {
            DateFormat timeFormat = getFreshdeskDateFormat();
            try {
                return timeFormat.parse(freshdeskTimeString);
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", freshdeskTimeString, e);
            }
        } else {
            log.error("Invalid time. freshdeskTimeString is null.");
        }
        return null;
    }

    public static Date parseLocalTime(String localTimeString) {
        if (localTimeString != null) {
            DateFormat timeFormat = getLocalDateFormat();
            try {
                return timeFormat.parse(localTimeString);
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", localTimeString, e);
            }
        } else {
            log.error("Invalid time. localTimeString is null.");
        }
        return null;
    }

    public static String convertFreshdeskTimeToLocalTimeString(String freshdeskTimeString) {
        if (freshdeskTimeString != null) {
            DateFormat freshdeskTimeFormat = getFreshdeskDateFormat();
            try {
                Date dateTime = freshdeskTimeFormat.parse(freshdeskTimeString);
                return getLocalTimeString(dateTime);
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", freshdeskTimeString, e);
            }
        } else {
            log.error("Invalid time. freshdeskTimeString is null.");
        }
        return null;
    }

    public static Date convertCspTimeStringToDate(String cspTimeString) {
        if (cspTimeString != null) {
            DateFormat cspTimeFormat = getCspDateFormat();
            try {
                return cspTimeFormat.parse(cspTimeString);
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", cspTimeString, e);
            }
        } else {
            log.error("Invalid time. cspTimeString is null.");
        }
        return null;
    }

    public static String convertCspTimeToLocalTimeString(String cspTimeString) {
        if (cspTimeString != null) {
            DateFormat cspTimeFormat = getCspDateFormat();
            try {
                Date dateTime = cspTimeFormat.parse(cspTimeString);
                return getLocalTimeString(dateTime);
            } catch (ParseException e) {
                log.error("Date parsing error: {} - {}", cspTimeString, e);
            }
        } else {
            log.error("Invalid time. cspTimeString is null.");
        }
        return null;
    }

    public static DateFormat getLocalDateFormat() {
        DateFormat localTimeFormat = new SimpleDateFormat(AppConstants.LOCAL_TIME_FORMAT);
        localTimeFormat.setTimeZone(AppConstants.getLocalTimeZone());
        return localTimeFormat;
    }

    public static String getLocalTimeString(Date dateTime) {
        if (dateTime != null) {
            DateFormat localFormat = getLocalDateFormat();
            return localFormat.format(dateTime);
        }
        return null;
    }

    public static String printTicketIdList(List<TicketMetadata> ticketMetadataList) {
        if (ticketMetadataList != null) {
            List<String> idList = new ArrayList<>();
            for (TicketMetadata metadata : ticketMetadataList) {
                idList.add(metadata.getFreshdeskTicketId());
            }
            return String.join(", ", idList);
        }
        return "empty";
    }

    /////////////////////////////////////////////////////////////////////////////////
    ////// Methods for IBM
    /////////////////////////////////////////////////////////////////////////////////

    /*
    public static String openSlackDirectMessageChannel(List<String> slackUsers, String slackApiToken) throws IOException, SlackApiException {
        Slack slack = Slack.getInstance();
        MethodsClient methods = slack.methods(slackApiToken);
        ConversationsOpenRequest request = ConversationsOpenRequest.builder().token("").users(slackUsers).build();
        ConversationsOpenResponse response = methods.conversationsOpen(request);
        return response.getChannel().getId();
    }

    public static void sendSLAReportToSlack(SLAReportMeta meta, String slackApiToken) throws IOException, SlackApiException {
        Slack slack = Slack.getInstance();
        MethodsClient methods = slack.methods(slackApiToken);
        List<com.slack.api.model.Attachment> attachments = new ArrayList<>();
        //com.slack.api.model.Attachment attachment = new com.slack.api.model.Attachment();
        //attachment.setFilename(aaaaa);
        // Build a request object
        List<String> channels = null; //meta.getSlackChannels();
        if (channels != null) {
            for (String channel : channels) {
                ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                        .channel(channel)
                        .text("요청하신 SLA Report(일자:2020.07.22/기간:6개월)가 생성되었습니다.\n아래 링크를 눌러 다운로드 하세요.\n")
                        //.attachments()
                        .build();
                // Get a response as a Java object
                ChatPostMessageResponse response = methods.chatPostMessage(request);
                log.debug("slack response:{}", response);
            }
        }
    }
    */

    /**
     * 특정 IBM 사용자의 email, Account Id의 사용자가 보유한 Device 목록 조회.
     *
     * @param email
     * @param accountId
     * @throws AppError.BadRequest
     * @throws IOException
     * @throws URISyntaxException
     */
    public static JSONObject getAccountDevicesByEmail(String email, String accountId) throws AppError {
        try {
            CloudZCspApiInfo cspApiInfo = CloudZService.getCspApiInfo(email, accountId);
            if (cspApiInfo == null) {
                throw AppInternalError.notFoundCspAccount();
            }
            return IbmService.getAccountDevicesByApiKey(cspApiInfo.getApiId(), cspApiInfo.getApiKey());
        } catch (AppInternalError e) {
            throw AppError.badRequest(e.getErrorReason().output());
        }
    }

    public static void checkAndReplaceBrandEmail(JSONObject freshdeskTicketData) {
        if (freshdeskTicketData != null) {
            JSONObject customData = freshdeskTicketData.optJSONObject(FreshdeskTicketField.CustomFields);
            if (customData != null) {
                FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(customData.optString(FreshdeskTicketField.CfCspAccount));
                if (accountField.isValid()) {
                    if (TicketUtil.isBrandEmail(accountField.getEmail())) {
                        CloudZUser masterUser = CloudZService.getCloudZMasterUserByAccountId(accountField.getAccountId());
                        if (masterUser != null && masterUser.getUserEmail() != null) {
                            String cspAccountFieldValue = masterUser.getUserEmail() + "/" + accountField.getAccountId();
                            customData.put(FreshdeskTicketField.CfCspAccount, cspAccountFieldValue);
                        } else {
                            log.error("Not found master email for IBM brand account : {}", accountField.getAccountId());
                        }
                    }
                }
            }
        }
    }

    public static boolean isBrandEmail(String email) {
        AppConfig config = AppConfig.getInstance();
        if (email != null && config.getReverseSyncAccounts() != null) {
            for (IbmBrandAccount account : config.getReverseSyncAccounts()) {
                return email.equals(account.getEmail());
            }
        }
        return false;
    }

    public static void sortIbmUpdates(List<Update> ibmTicketUpdates) {
        if (ibmTicketUpdates != null && ibmTicketUpdates.size() > 1) {
            Collections.sort(ibmTicketUpdates, new Comparator<Update>() {
                @Override
                public int compare(Update o1, Update o2) {
                    //Ascending sort
                    long time1 = o1.getCreateDate().getTimeInMillis();
                    long time2 = o2.getCreateDate().getTimeInMillis();
                    if (time1 > time2) {
                        return 1;
                    } else if (time1 < time2) {
                        return -1;
                    }
                    return 0;
                }
            });
        }
    }

    public static boolean isAttachmentNote(Update update) {
        if (update != null) {
            String editorType = update.getEditorType();
            String body = update.getEntry();
            if (editorType != null && body != null && !body.contains("\n")) { //single line cause
                if (IbmTicketEditorType.isUser(editorType) || IbmTicketEditorType.isAgent(editorType)) {
                    //"editorType": "USER",
                    //"editorType": "AGENT",
                    //"entry": "Attached file image1__from_freshdesk.jpg",
                    return body.startsWith("Attached file") && body.contains("__from_freshdesk");
                } else if (IbmTicketEditorType.isEmployee(editorType)) {
                    //"editorType": "EMPLOYEE",
                    //"entry": "A new file attachment has been added"
                    return body.contains("A new file attachment has been added");
                }
            }
        }
        return false;
    }

    /**
     * Freshdesk에 등록된 첨부파일을 IBM에 등록하기 위해 파일명에 __from_freshdesk 추가.
     *
     * @param fileName
     */
    public static String buildFreshdeskTaggedFileName(String fileName) {
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                String namePart = fileName.substring(0, dotIndex);
                String extPart = fileName.substring(dotIndex + 1);
                return namePart + AppConstants.FRESHDESK_FILE_SUFFIX + "." + extPart;
            } else {
                return fileName + AppConstants.FRESHDESK_FILE_SUFFIX;
            }
        }
        return null;
    }

    public static Update getCspTicketBody(ApiClient ibmClient, String cspTicketId) {
        Ticket.Service service = Ticket.service(ibmClient, Long.valueOf(cspTicketId));
        return service.getFirstUpdate();
    }

    public static boolean isCreatedByUser(JSONObject ticketData) {
        if (ticketData != null && ticketData.has(FreshdeskTicketField.DescriptionHtml)) {
            String descriptionHtml = ticketData.optString(FreshdeskTicketField.DescriptionHtml);
            boolean cspTagged = isTaggedCsp(descriptionHtml);
            if (cspTagged && ticketData.has(FreshdeskTicketField.Subject)) {
                String title = ticketData.optString(FreshdeskTicketField.Subject);
                log.debug("title:{}", title);
                //[USER] How to get attached device information
                return title.startsWith("[" + IbmTicketEditorType.User + "]");
            } else { //All tickets generated by Freshdesk are user tickets.
                return true;
            }
        }
        return false;
    }

    /**
     * IBM의 티켓 본문에 등록된 티켓 서비스의 태그가 Freshdesk 티켓의 티켓 아이디와 일치하는지 확인.
     *
     * @param update
     * @param freshdeskTicketId
     * @param createAt
     */
    public static boolean isMatchedTicketWithFreshdeskTicket(Update update, String freshdeskTicketId, String createAt) {
        if (update != null && freshdeskTicketId != null) {
            //[CREATED_FROM_FRESHDESK:223,2020-04-16T15:04:14 +09:00]
            //[CREATED_FROM_FRESHDESK:1197,2020-07-21T13:01:18 +09:00]
            String entry = update.getEntry();
            if (entry != null) {
                TicketMetaTag metaTag = TicketUtil.getTicketMetaTag(entry, AppConstants.CREATED_FROM_FRESHDESK, AppConstants.CSP_LINEFEED);
                if (metaTag != null) {
                    return freshdeskTicketId.equals(metaTag.getId());// && createAt.equals(metaTag.getTime());
                }
                /*if (isTaggedFreshdesk(entry)) {
                    String compareString = String.format("[%s:%s,%s]", AppConstants.CREATED_FROM_FRESHDESK, freshdeskTicketId, createAt);
                    return entry.contains(compareString);
                }*/
            }
        }
        return false;
    }

    private static final String UnplannedEventTestEmail = "seoingood@sk.com";

    public static boolean isUnplannedEventTest(FreshdeskTicketBuilder ticketBuilder) {
        if (ticketBuilder != null) {
            AppConfig config = AppConfig.getInstance();
            if (config.isStagingStage() && UnplannedEventTestEmail.equals(ticketBuilder.getEmail())) {
                return UnplannedEvents.isUnplannedEvent(ticketBuilder.getSubject());
            }
        }
        return false;
    }

    public static String attachIbmAccountPrefix(long ibmAccountId) {
        return AppConstants.IBM_ACCOUNT_PREFIX + ibmAccountId;
    }

    public static String detachIbmAccountPrefix(String ibmAccountId) {
        if (ibmAccountId != null && ibmAccountId.startsWith(AppConstants.IBM_ACCOUNT_PREFIX)) {
            return ibmAccountId.replaceFirst(AppConstants.IBM_ACCOUNT_PREFIX, "");
        }
        return ibmAccountId;
    }

    public static String buildIbmTicketTitle(String editorType, String title) {
        if (editorType == null) {
            return title;
        }
        return "[" + editorType + "] " + title;
    }
}
