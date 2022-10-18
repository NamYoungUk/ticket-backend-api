package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketResponse;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketUrlMapper;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.service.FreshdeskService;
import com.sk.bds.ticket.api.util.Util;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TicketRegistry {
    Map<String, String> ticketIdLinker;
    Map<String, TicketMetadata> ticketMetadataList;
    TreeSet<String> monitoringTickets;
    FreshdeskTicketUrlMapper urlMapper;

    private static class LazyHolder {
        private static final TicketRegistry ticketRegistryInstance = new TicketRegistry();
    }

    public static TicketRegistry getInstance() {
        return LazyHolder.ticketRegistryInstance;
    }

    private TicketRegistry() {
        ticketIdLinker = new ConcurrentHashMap();
        ticketMetadataList = new ConcurrentHashMap();
        monitoringTickets = new TreeSet<>();
        urlMapper = FreshdeskTicketUrlMapper.loadMapping();
    }

    public void clear() {
        ticketIdLinker.clear();
        ticketMetadataList.clear();
        monitoringTickets.clear();
    }

    public void updateTicketMetadata(TicketMetadata ticketMetadata) {
        if (ticketMetadata != null && ticketMetadata.getFreshdeskTicketId() != null) {
            ticketMetadataList.put(ticketMetadata.getFreshdeskTicketId(), ticketMetadata);
            if (ticketMetadata.getCspTicketId() != null) {
                onLinkedTicketId(ticketMetadata.getFreshdeskTicketId(), ticketMetadata.getCspTicketId());
            }
        }
    }

    public TicketMetadata getTicketMetadata(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            if (ticketMetadataList.containsKey(freshdeskTicketId)) {
                return ticketMetadataList.get(freshdeskTicketId);
            }
        }
        return null;
    }

    public List<TicketMetadata> getTicketMetadataList() {
        return new ArrayList<>(ticketMetadataList.values());
    }

    public int getMonitoringTicketCount() {
        return monitoringTickets.size();
    }

    public List<String> getMonitoringTicketIdList() {
        List<String> tickets = new ArrayList<>();
        tickets.addAll(monitoringTickets);
        return tickets;
    }

    public List<TicketMetadata> getMonitoringTicketMetadataList() {
        List<TicketMetadata> metadataList = new ArrayList<>();
        for (String freshdeskTicketId : getMonitoringTicketIdList()) {
            TicketMetadata metadata = getTicketMetadata(freshdeskTicketId);
            if (metadata == null) {
                try {
                    FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
                    JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
                    if (freshdeskTicketData != null) {
                        metadata = TicketMetadata.build(freshdeskTicketData, true);
                        updateTicketMetadata(metadata);
                    }
                } catch (AppInternalError e) {
                    log.error("Failed to get freshdesk ticket {} with exception. {}", freshdeskTicketId, e);
                }
            }
            if (metadata != null) {
                metadataList.add(metadata);
            }
        }
        return metadataList;
    }

    public boolean isMonitoringTicket(String freshdeskTicketId) {
        return monitoringTickets.contains(freshdeskTicketId);
    }

    public void addMonitoring(String freshdeskTicketId) {
        if (freshdeskTicketId != null && freshdeskTicketId.trim().length() > 0) {
            monitoringTickets.add(freshdeskTicketId);
        }
    }

    public void addMonitoringList(List<String> freshdeskTicketIds) {
        if (freshdeskTicketIds != null && freshdeskTicketIds.size() > 0) {
            monitoringTickets.addAll(freshdeskTicketIds);
        }
    }

    public void addMonitoring(String freshdeskTicketId, TicketMetadata ticketMetadata) {
        if (freshdeskTicketId != null) {
            monitoringTickets.add(freshdeskTicketId);
        }
        if (ticketMetadata != null) {
            updateTicketMetadata(ticketMetadata);
        }
    }

    public void removeMonitoring(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            monitoringTickets.remove(freshdeskTicketId);
            ticketMetadataList.remove(freshdeskTicketId);
        }
    }

    public void onLinkedTicketId(String freshdeskTicketId, String cspTicketId) {
        if (freshdeskTicketId != null && cspTicketId != null) {
            ticketIdLinker.put(freshdeskTicketId, cspTicketId);
        }
    }

    public boolean isLinkedFreshdeskTicket(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            return ticketIdLinker.containsKey(freshdeskTicketId);
        }
        return false;
    }

    public boolean isLinkedCspTicket(String cspTicketId) {
        if (cspTicketId != null) {
            return ticketIdLinker.containsValue(cspTicketId);
        }
        return false;
    }

    public void setTicketPublicUrl(String freshdeskTicketId, String freshdeskTicketPublicUrl) {
        //자동화 규칙시 전달받아서 설정해야함.
        //ticket_create, ticket_public_note_added, ticket_close
        urlMapper.setPublicUrl(freshdeskTicketId, freshdeskTicketPublicUrl);
    }

    public String getTicketPublicUrl(String freshdeskTicketId) {
        return urlMapper.getPublicUrl(freshdeskTicketId);
    }

    public void eraseTicketPublicUrl(String freshdeskTicketId) {
        //Erase ticket url once ticket was closed.
        urlMapper.eraseUrl(freshdeskTicketId);
    }
}
