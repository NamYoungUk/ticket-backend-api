package com.sk.bds.ticket.api.service;

import com.sk.bds.ticket.api.data.model.OperationBreaker;
import com.sk.bds.ticket.api.data.model.ProcessResult;
import com.sk.bds.ticket.api.data.model.TicketMetadata;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketBuilder;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.softlayer.api.service.Ticket;
import org.json.JSONObject;

import java.util.List;

public interface CspTicketHandler {
    ProcessResult createCspTicket(JSONObject freshdeskTicketData);

    void createCspConversation(Ticket.Service ibmTicketService, JSONObject freshdeskConversation, TicketMetadata ticketMetadata) throws AppInternalError;

    ProcessResult closeCspTicket(CloudZCspApiInfo cspApiInfo, JSONObject freshdeskTicketData, String cspTicketId);

    ProcessResult synchronizeTicket(JSONObject freshdeskTicketData, OperationBreaker breaker);

    ProcessResult checkCspNewTicket(OperationBreaker breaker);

    ProcessResult checkCspNewConversation(List<TicketMetadata> monitoringTickets, OperationBreaker breaker);

    FreshdeskTicketBuilder buildFreshdeskTicketBuilder(IbmBrandAccount brandAccount, String cspTicketId) throws AppInternalError;
}
