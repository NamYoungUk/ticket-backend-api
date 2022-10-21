package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import lombok.Data;

import java.util.Date;

@Data
public class TimeSection {
    long start;
    long end;

    public TimeSection() {
    }

    public TimeSection(long start) {
        this.start = start;
        this.end = 0;
    }

    public TimeSection(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public boolean hasEndTime() {
        return (end > start);
    }

    public long getEnd() {
        if (hasEndTime()) {
            return end;
        }
        return System.currentTimeMillis();
    }

    public String print() {
        String localStart = TicketUtil.getLocalTimeString(new Date(getStart()));
        String localEnd;
        if (hasEndTime()) {
            localEnd = TicketUtil.getLocalTimeString(new Date(getEnd()));
        } else {
            localEnd = "Up to date";
        }
        return String.format("[%s - %s]", localStart, localEnd);
    }
}
