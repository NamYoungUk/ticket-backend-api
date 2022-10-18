package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskCspAccountField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketPriority;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketStatus;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

@Slf4j
@Data
public class TicketMetadata {
    String freshdeskTicketId;
    String cspTicketId;
    String cspAccountId;
    String cspAccountEmail;
    String freshdeskLatestConversationId;
    long freshdeskLatestConversationTime;
    String cspLatestConversationId;
    long cspLatestConversationTime;
    long cspLatestAttachedFileTime;

    boolean createdByCsp;
    boolean createdByUser;
    int freshdeskTicketStatus;

    /////////////For SLA Report
    int severity;
    String tribe;
    long freshdeskCreatedTime;
    long cspCreatedTime;
    long l1ResponseTime;
    long l2AssignTime;
    long l2ResponseTime;
    long escalationTime;
    long cspResponseTime;

    public TicketMetadata() {
    }

    public boolean isSolved() {
        return FreshdeskTicketStatus.isClosed(freshdeskTicketStatus);
    }

    public boolean isSetL1ResponseTime() {
        return (l1ResponseTime > 0);
    }

    public boolean isSetL2AssignTime() {
        return (l2AssignTime > 0);
    }

    public boolean isSetL2ResponseTime() {
        return (l2ResponseTime > 0);
    }

    public boolean isSetEscalationTime() {
        return (escalationTime > 0);
    }

    public boolean isSetCspResponseTime() {
        return (cspResponseTime > 0);
    }

    public boolean isSetAllSLATimes() {
        if (isSolved()) {
            return (isSetL1ResponseTime() && isSetEscalationTime());
        } else {
            return (isSetL1ResponseTime() && isSetEscalationTime() && isSetCspResponseTime());
        }
    }

    public void onFreshdeskConversationSynced(String conversationId, long conversationCreatedTime) {
        if (conversationId != null && conversationCreatedTime > freshdeskLatestConversationTime) {
            freshdeskLatestConversationId = conversationId;
            freshdeskLatestConversationTime = conversationCreatedTime;
        }
    }

    public void onCspConversationSynced(String conversationId, long conversationCreatedTime) {
        if (conversationId != null && conversationCreatedTime > cspLatestConversationTime) {
            cspLatestConversationId = conversationId;
            cspLatestConversationTime = conversationCreatedTime;
        }
    }

    public void onCspAttachedFileSynched(long latestAttachedFileTime) {
        if (latestAttachedFileTime > cspLatestAttachedFileTime) {
            cspLatestAttachedFileTime = latestAttachedFileTime;
        }
    }

    public static TicketMetadata build(JSONObject freshdeskTicketData, boolean cspTicketIdRequired) {
        if (freshdeskTicketData == null) {
            log.error("invalid ticket data.");
            return null;
        }
        if (!TicketUtil.isValidCustomField(freshdeskTicketData)) {
            log.error("invalid custom field. freshdesk ticket id:{}", freshdeskTicketData.optString(FreshdeskTicketField.Id));
            return null;
        }
        DateFormat localTimeFormat = TicketUtil.getLocalDateFormat();
        TicketMetadata ticketMetadata = new TicketMetadata();
        JSONObject customData = freshdeskTicketData.optJSONObject(FreshdeskTicketField.CustomFields);
        String descriptionHtml = freshdeskTicketData.optString(FreshdeskTicketField.DescriptionHtml);
        boolean cspTagged = TicketUtil.isTaggedCsp(descriptionHtml);
        boolean createdByUser = TicketUtil.isCreatedByUser(freshdeskTicketData);
        int ticketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
        //String brandId = getIdFromBodyTag(descriptionHtml, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
        ticketMetadata.setFreshdeskTicketId(freshdeskTicketData.optString(FreshdeskTicketField.Id));
        ticketMetadata.setFreshdeskTicketStatus(ticketStatus);
        ticketMetadata.setCreatedByCsp(cspTagged);
        ticketMetadata.setCreatedByUser(createdByUser);
        //ticket.setBrandId(brandId);
        if (customData != null) {
            if (!customData.isNull(FreshdeskTicketField.CfCspCaseId)) { //if(customData.optString(FreshdeskTicketField.CfCspCaseId).length() > 0) {
                ticketMetadata.setCspTicketId(customData.optString(FreshdeskTicketField.CfCspCaseId));
            }
            FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(customData.optString(FreshdeskTicketField.CfCspAccount));
            if (accountField.isValid()) {
                ticketMetadata.setCspAccountEmail(accountField.getEmail());
                ticketMetadata.setCspAccountId(accountField.getAccountId());
            }
            ///////Optional for SLA Report
            if (customData.has(FreshdeskTicketField.Priority)) {
                ticketMetadata.setSeverity(customData.optInt(FreshdeskTicketField.Priority, FreshdeskTicketPriority.Low));
            }
            if (customData.has(FreshdeskTicketField.CfTribe)) {
                ticketMetadata.setTribe(customData.optString(FreshdeskTicketField.CfTribe));
            }

            if (freshdeskTicketData.has(FreshdeskTicketField.CreatedAt)) {
                try {
                    String timeString = freshdeskTicketData.optString(FreshdeskTicketField.CreatedAt);
                    DateFormat timeFormat = TicketUtil.getFreshdeskDateFormat();
                    Date parsedTime = timeFormat.parse(timeString);
                    ticketMetadata.setFreshdeskCreatedTime(parsedTime.getTime());
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }

            if (cspTagged) {
                String freshdeskBodyHtml = freshdeskTicketData.optString(FreshdeskTicketField.DescriptionHtml);
                String timeString = TicketUtil.getTimeFromBodyTag(freshdeskBodyHtml, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
                if (timeString != null) {
                    try {
                        Date parsedTime = localTimeFormat.parse(timeString);
                        ticketMetadata.setCspCreatedTime(parsedTime.getTime());
                    } catch (ParseException e) {
                        Util.ignoreException(e);
                    }
                }
            }

            if (customData.has(FreshdeskTicketField.CfL1ResponseTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfL1ResponseTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    ticketMetadata.setL1ResponseTime(parsedTime.getTime());
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }

            if (customData.has(FreshdeskTicketField.CfEscalationTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfEscalationTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    ticketMetadata.setEscalationTime(parsedTime.getTime());
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }
            if (customData.has(FreshdeskTicketField.CfCspResponseTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfCspResponseTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    ticketMetadata.setCspResponseTime(parsedTime.getTime());
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }
        }
        if (ticketMetadata.getFreshdeskTicketId() == null || ticketMetadata.getFreshdeskTicketId() == "") {
            log.error("Empty freshdesk ticket Id. csp ticket id:{}", ticketMetadata.getCspTicketId());
            return null;
        }
        if (ticketMetadata.getCspAccountId() == null || ticketMetadata.getCspAccountEmail() == null) {
            log.error("Invalid csp account. freshdesk id:{}", ticketMetadata.getFreshdeskTicketId());
            return null;
        }
        if (cspTicketIdRequired && (ticketMetadata.getCspTicketId() == null || ticketMetadata.getCspTicketId() == "")) {
            log.error("Empty csp ticket Id. freshdesk id:{}", ticketMetadata.getFreshdeskTicketId());
            return null;
        }
        return ticketMetadata;
    }

}
