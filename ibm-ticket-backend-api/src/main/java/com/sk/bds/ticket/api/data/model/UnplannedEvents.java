package com.sk.bds.ticket.api.data.model;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class UnplannedEvents {
    public static boolean isUnplannedEvent(String ticketTitle) {
        return (getMatchedUnplannedEvent(ticketTitle) != null);
    }

    public static String getMatchedUnplannedEvent(String ticketTitle) {
        List<String> unplannedEvent = new ArrayList<>(AppConfig.getInstance().getIbmUnplannedEvents());
        if (ticketTitle != null) {
            for (String event : unplannedEvent) {
                if (StringUtils.containsIgnoreCase(ticketTitle, event)) {
                    return event;
                }
            }
        }
        return null;
    }
}
