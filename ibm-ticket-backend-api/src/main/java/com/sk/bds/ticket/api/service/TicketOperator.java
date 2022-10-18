package com.sk.bds.ticket.api.service;

import com.sk.bds.ticket.api.data.model.ProcessResult;
import com.sk.bds.ticket.api.data.model.TicketMetadata;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketBuilder;
import com.sk.bds.ticket.api.exception.AppInternalError;
import org.json.JSONObject;

public interface TicketOperator {

    void startInstantTicketSync(JSONObject freshdeskTicketData);

    void synchronizeConversationByCspMonitoring(String freshdeskTicketId);

    boolean isMonitoringTicket(String freshdeskTicketId);

    void addMonitoringTicket(String freshdeskTicketId, TicketMetadata ticketMetadata);

    void removeMonitoringTicket(String freshdeskTicketId);

    String getTicketPublicUrl(String freshdeskTicketId);

    TicketMetadata getTicketMetadata(String freshdeskTicketId);

    void updateTicketMetadata(TicketMetadata ticketMetadata);

    void onLinkedTicketId(String freshdeskTicketId, String cspCaseId);

    boolean isLinkedCspTicket(String cspTicketId);

    void updateEscalationInfo(JSONObject freshdeskTicketData, String cspTicketId, String cspTicketDisplayId) throws AppInternalError;

    ProcessResult createFreshdeskTicket(FreshdeskTicketBuilder ticketBuilder);
}
