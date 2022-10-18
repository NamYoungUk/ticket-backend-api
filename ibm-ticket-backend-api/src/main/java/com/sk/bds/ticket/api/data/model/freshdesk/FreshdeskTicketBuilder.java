package com.sk.bds.ticket.api.data.model.freshdesk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.UnplannedEvents;
import com.sk.bds.ticket.api.data.model.ibm.IbmDevice;
import com.sk.bds.ticket.api.data.model.ibm.IbmTicketEditorType;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import lombok.Data;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Data
public class FreshdeskTicketBuilder {
    ////////////////////////
    // IBM Ticket information
    ////////////////////////
    private String ibmBrandId;
    private String ibmEditorId;
    private Long ibmAccountId = null;
    private String ibmSupportType = null;
    private String ibmOffering = null;
    private String ibmBody = null;
    private Date ibmCreateDate = null;
    private String ibmServiceProviderResourceId;
    private List<IbmDevice> ibmAttachedDevices = null;
    ////////////////////////
    // For Freshdesk
    ////////////////////////
    private String email = null; //연락처
    private List<String> ccEmails = null; //참조 연락처
    private String editorType; //IBMTicketProperty.IbmTicketEditorType AUTO, USER, AGENT, EMPLOYEE, ...
    private String type = FreshdeskTicketType.Etc; //유형
    private String csp = AppConstants.CSP_NAME; //CSP
    private String subject = null; //제목
    private String description = null; //설명
    private int priority = FreshdeskTicketPriority.Low; //우선순위
    private int status = FreshdeskTicketStatus.Open; //상태
    private String escalation = null; //Escalation
    private String cspTicketId = null; //CSP 티켓 Id
    private String cspDevice = null; //CSP Device list 대상장비
    private String cspAccount = null; //CSP Account
    private List<FreshdeskAttachment> attachments = null;

    private String unplannedEventManagerEmail = null; //장애 티켓 담당자 email

    @JsonIgnore
    private String supportPortalUsingMessage;
    @JsonIgnore
    private String unplannedEventMessageTemplate;

    public FreshdeskTicketBuilder(String brandId, String unplannedEventManagerEmail, String portalMessage, String unplannedEventTemplate) {
        this.ibmBrandId = brandId;
        this.unplannedEventManagerEmail = unplannedEventManagerEmail;
        this.supportPortalUsingMessage = portalMessage;
        this.unplannedEventMessageTemplate = unplannedEventTemplate;
    }

    public void setSubject(String subject) {
        this.subject = subject;
        checkUnplannedEvent();
    }

    public void setEditorType(String editorType) {
        this.editorType = editorType;
        checkUnplannedEvent();
    }

    private void checkUnplannedEvent() {
        if (isUnplannedEvent()) {
            setType(FreshdeskTicketType.Failure);
        } else {
            setType(FreshdeskTicketType.Etc);
        }
    }

    public boolean isUserTicket() {
        if (this.editorType != null) {
            return IbmTicketEditorType.User.equals(this.editorType);
        }
        return false;
    }

    public boolean isUnplannedEvent() {
        //사용자에 의해 생성된 티켓이 아니고 키워드가 장애 티켓에 해당하는 경우에만.
        return !isUserTicket() && UnplannedEvents.isUnplannedEvent(getSubject());
    }

    private String buildUnplannedEventDescription() {
        Date ticketCreatedDate = getIbmCreateDate();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(ticketCreatedDate);
        calendar.setTimeZone(AppConstants.getLocalTimeZone());
        String dayOfWeekString = Util.getDayOfWeekKorString(calendar);
        String eventDate = String.format("%d/%d(%s)", calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH), dayOfWeekString);
        String template = unplannedEventMessageTemplate.replace("##DATE##", eventDate);
        if (getIbmServiceProviderResourceId() != null) {
            template = template.replace("##TICKET_NO##", getIbmServiceProviderResourceId());
        } else {
            template = template.replace("##TICKET_NO##", getCspTicketId());
        }
        template = template.replace("##CUSTOMER_NO##", TicketUtil.attachIbmAccountPrefix(getIbmAccountId()));
        if (getIbmAttachedDevices() != null) {
            String devices = IbmDevice.join(", ", IbmDevice.AttributeDelimiterWithSpace, getIbmAttachedDevices());
            template = template.replace("##TARGET_DEVICES##", "[" + devices + "]");
        } else {
            template = template.replace("##TARGET_DEVICES##", "[]");
        }

        //L1용 티켓의 본문에는 원본 티켓의 분류(지원유형, 오퍼링)가 포함되도록 함.
        if (getIbmSupportType() != null) {
            template = template.replace("##SUPPORT_TYPE##", getIbmSupportType());
        } else {
            template = template.replace("##SUPPORT_TYPE##", "");
        }

        if (getIbmOffering() != null) {
            template = template.replace("##OFFERING##", getIbmOffering());
        } else {
            template = template.replace("##OFFERING##", "");
        }

        template = template.replace("##TICKET_BODY##", getIbmBody());

        String bodyContent = template.replaceAll(AppConstants.CSP_LINEFEED, AppConstants.FRESHDESK_LINEFEED);
        String localTimeString = TicketUtil.getLocalTimeString(ticketCreatedDate);
        return String.format("%s%s[%s:%s,%s]", bodyContent, AppConstants.FRESHDESK_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_CSP, ibmBrandId, localTimeString);
    }

    public JSONObject buildUnplannedEventTicketParameter(List<String> relatedTicketIds) {
        final String CfL1 = "cf_l1";
        final String CfL2 = "cf_l2";
        final String CspValue = "Cloud Z";
        final String L1Value = "플랫폼(Platform)";
        final String L2Value = "IaaS";
        JSONObject parameter = buildTicketCreationParameter();
        parameter.put(FreshdeskTicketField.Priority, FreshdeskTicketPriority.Urgent);
        parameter.put(FreshdeskTicketField.Email, this.unplannedEventManagerEmail);
        parameter.put(FreshdeskTicketField.DescriptionHtml, buildUnplannedEventDescription());
        if (relatedTicketIds != null && relatedTicketIds.size() > 0) {
            JSONArray trackingIds = new JSONArray();
            for (String id : relatedTicketIds) {
                try {
                    trackingIds.put(Integer.valueOf(id));
                } catch (NumberFormatException e) {
                    Util.ignoreException(e);
                }
            }
            parameter.put(FreshdeskTicketField.RelatedTicketIds, trackingIds);
        }

        JSONObject customFields = parameter.getJSONObject(FreshdeskTicketField.CustomFields);
        customFields.put(FreshdeskTicketField.CfCsp, CspValue);
        customFields.put(CfL1, L1Value);
        customFields.put(CfL2, L2Value);
        return parameter;
    }

    public JSONObject buildTicketCreationParameter() {
        FreshdeskTicketParameterBuilder builder = new FreshdeskTicketParameterBuilder();
        builder.email(email);
        builder.ticketType(type);
        builder.ccEmails(ccEmails);

        if (this.editorType != null) {
            builder.title(TicketUtil.buildIbmTicketTitle(editorType, subject)); //"[" + editorType + "] " + this.subject
            if (isUserTicket()) {
                builder.description(supportPortalUsingMessage + this.description);
            } else if (isUnplannedEvent()) {
                builder.description(buildUnplannedEventDescription());
            } else {
                builder.description(description);
            }
        } else {
            if (isUnplannedEvent()) {
                builder.title(subject);
                builder.description(buildUnplannedEventDescription());
            } else {
                builder.title(subject);
                builder.description(description);
            }
        }

        builder.priority(priority);
        builder.ticketStatus(status);
        builder.tags(AppConstants.CREATED_FROM_CSP);

        //Custom Field
        builder.csp(csp);
        builder.customFieldValue(FreshdeskTicketField.CfIbmL1, ibmSupportType);
        builder.customFieldValue(FreshdeskTicketField.CfIbmL2, ibmOffering);
        builder.escalation(escalation);
        builder.cspTicketId(cspTicketId);
        builder.cspAccount(cspAccount);
        builder.cspDevice(cspDevice);
        return builder.buildParameter();
    }

    public static JSONObject buildErrorReportTicketParameter(String cspTicketId, String cspTicketDisplayId, String cspTicketTitle, String agentEmail, List<String> ccEmails) {
        final String CfL1 = "cf_l1";
        final String CfL2 = "cf_l2";
        final String CspValue = "Cloud Z";
        final String L1Value = "Cloud Z Care";
        final String L2Value = "Support Portal";

        FreshdeskTicketParameterBuilder builder = new FreshdeskTicketParameterBuilder();
        builder.email(agentEmail);
        builder.ticketType(FreshdeskTicketType.Failure);
        builder.ccEmails(ccEmails);
        builder.title("[티켓 생성 실패] " + cspTicketTitle);
        String description = String.format("%s에 등록된 티켓이 Support Portal에 등록되지 않았습니다.<br>-------------------<br>티켓 ID:%s<br>티켓표시번호:%s<br>티켓 제목:%s<br>-------------------<br><br>담당자분께서는 확인 후 조치 바랍니다.<br><br>", AppConstants.CSP_NAME, cspTicketId, cspTicketDisplayId, cspTicketTitle);
        builder.description(description);
        builder.priority(FreshdeskTicketPriority.Medium);
        builder.ticketStatus(FreshdeskTicketStatus.Open);
        builder.tags(AppConstants.CREATED_FROM_TICKET_MONITORING);

        //Custom Field
        builder.csp(CspValue);
        builder.customFieldValue(CfL1, L1Value);
        builder.customFieldValue(CfL2, L2Value);
        return builder.buildParameter();
    }

    public static class FreshdeskTicketParameterBuilder {
        public static JSONObject buildParameter(String title, String description, String email) {
            return buildParameter(title, description, FreshdeskTicketType.Etc, FreshdeskTicketPriority.Low, FreshdeskTicketStatus.Open, email, null, null, null);
        }

        public static JSONObject buildParameter(String title, String description, String email, List<String> ccEmailList) {
            return buildParameter(title, description, FreshdeskTicketType.Etc, FreshdeskTicketPriority.Low, FreshdeskTicketStatus.Open, email, ccEmailList, null, null);
        }

        public static JSONObject buildParameter(String title, String description, String email, String ticketType) {
            return buildParameter(title, description, ticketType, FreshdeskTicketPriority.Low, FreshdeskTicketStatus.Open, email, null, null, null);
        }

        public static JSONObject buildParameter(String title, String description, String email, String ticketType, int priority) {
            return buildParameter(title, description, ticketType, priority, FreshdeskTicketStatus.Open, email, null, null, null);
        }

        public static JSONObject buildParameter(String title, String description, String email, String ticketType, int priority, int ticketStatus) {
            return buildParameter(title, description, ticketType, priority, ticketStatus, email, null, null, null);
        }

        public static JSONObject buildParameter(String title, String description, String email, List<String> ccEmailList, List<String> tagList, JSONObject customFields) {
            return buildParameter(title, description, FreshdeskTicketType.Etc, FreshdeskTicketPriority.Low, FreshdeskTicketStatus.Open, email, ccEmailList, tagList, customFields);
        }

        public static JSONObject buildParameter(String title, String description, String ticketType, int priority, int ticketStatus, String email, List<String> ccEmailList, List<String> tagList, JSONObject customFields) {
            FreshdeskTicketParameterBuilder builder = new FreshdeskTicketParameterBuilder();
            builder.ticketType(ticketType);
            builder.email(email);
            builder.ccEmails(ccEmailList);
            builder.title(title);
            builder.description(description);
            builder.priority(priority);
            builder.ticketStatus(ticketStatus);
            builder.tags(tagList);
            return builder.buildParameter();
        }

        private JSONObject parameter;

        public FreshdeskTicketParameterBuilder() {
            parameter = new JSONObject();
            //Default values
            ticketType(FreshdeskTicketType.Etc);
            ticketStatus(FreshdeskTicketStatus.Open);
            priority(FreshdeskTicketPriority.Low);
            csp(AppConstants.CSP_NAME);
        }

        public JSONObject buildParameter() {
            return parameter;
        }

        public void title(String title) {
            if (title != null) {
                parameter.put(FreshdeskTicketField.Subject, title);
            }
        }

        public void description(String description) {
            if (description != null) {
                parameter.put(FreshdeskTicketField.DescriptionHtml, description);
            }
        }

        public void ticketType(String ticketType) {
            if (ticketType != null) {
                parameter.put(FreshdeskTicketField.Type, ticketType);
            }
        }

        public void email(String email) {
            if (email != null) {
                parameter.put(FreshdeskTicketField.Email, email);
            }
        }

        public void ccEmails(List<String> ccEmailList) {
            if (ccEmailList != null && ccEmailList.size() > 0) {
                JSONArray ccEmails = new JSONArray();
                for (String cc : ccEmailList) {
                    ccEmails.put(cc);
                }
                parameter.put(FreshdeskTicketField.CcEmails, ccEmails);
            }
        }

        public void ccEmails(String... ccEmailArray) {
            if (ccEmailArray != null && ccEmailArray.length > 0) {
                JSONArray ccEmails = new JSONArray();
                for (String cc : ccEmailArray) {
                    ccEmails.put(cc);
                }
                parameter.put(FreshdeskTicketField.CcEmails, ccEmails);
            }
        }

        public void priority(int priority) {
            parameter.put(FreshdeskTicketField.Priority, priority);
        }

        public void ticketStatus(int ticketStatus) {
            parameter.put(FreshdeskTicketField.Status, ticketStatus);
        }

        public void tags(List<String> tagList) {
            if (tagList != null && tagList.size() > 0) {
                JSONArray tags = new JSONArray();
                for (String tag : tagList) {
                    tags.put(tag);
                }
                parameter.put(FreshdeskTicketField.Tags, tags);
            }
        }

        public void tags(String... tagArray) {
            if (tagArray != null && tagArray.length > 0) {
                JSONArray tags = new JSONArray();
                for (String tag : tagArray) {
                    tags.put(tag);
                }
                parameter.put(FreshdeskTicketField.Tags, tags);
            }
        }

        public void customFields(JSONObject customFields) {
            if (customFields != null) {
                parameter.put(FreshdeskTicketField.CustomFields, customFields);
            }
        }

        public void csp(String csp) {
            if (csp != null) {
                customFieldValue(FreshdeskTicketField.CfCsp, csp);
            }
        }

        public void escalation(boolean escalation) {
            if (escalation) {
                customFieldValue(FreshdeskTicketField.CfEscalation, "Y");
            } else {
                customFieldValue(FreshdeskTicketField.CfEscalation, "N");
            }
        }

        public void escalation(String escalation) {
            if (escalation != null && ("Y".equals(escalation) || "N".equals(escalation))) {
                customFieldValue(FreshdeskTicketField.CfEscalation, escalation);
            }
        }

        public void cspTicketId(String cspTicketId) {
            if (cspTicketId != null) {
                customFieldValue(FreshdeskTicketField.CfCspCaseId, cspTicketId);
            }
        }

        public void cspAccount(String cspAccount) {
            if (cspAccount != null) {
                customFieldValue(FreshdeskTicketField.CfCspAccount, cspAccount);
            }
        }

        public void cspDevice(String cspDevice) {
            if (cspDevice != null) {
                customFieldValue(FreshdeskTicketField.CfCspDevice, cspDevice);
            }
        }

        public void customFieldValue(String fieldName, Object fieldValue) {
            if (fieldName != null && fieldValue != null) {
                JSONObject customFields = parameter.optJSONObject(FreshdeskTicketField.CustomFields);
                if (customFields == null) {
                    customFields = new JSONObject();
                }
                customFields.put(fieldName, fieldValue);
                parameter.put(FreshdeskTicketField.CustomFields, customFields);
            }
        }
    }
}