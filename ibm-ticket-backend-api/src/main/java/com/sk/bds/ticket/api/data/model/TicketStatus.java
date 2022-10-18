package com.sk.bds.ticket.api.data.model;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum TicketStatus {
    opened,
    pending,
    resolved,
    closed,
    all;

    public static TicketStatus fromName(String name) {
        try {
            return TicketStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            log.error("invalid status name: {} - {}", name, e);
        }
        return opened;
    }

    public String korText() {
        switch (this) {
            case opened:
                return "열려 있음";
            case pending:
                return "대기 중";
            case resolved:
                return "해결됨";
            case closed:
                return "종료됨";
        }
        return "모든 상태";
    }
}
