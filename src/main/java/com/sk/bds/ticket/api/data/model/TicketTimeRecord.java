package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;

@Slf4j
@Data
public class TicketTimeRecord {
    String ticketId;
    long createTime;

    public TicketTimeRecord() {
        ticketId = null;
        createTime = 0;
    }

    public TicketTimeRecord(String ticketId, long createTime) {
        this.ticketId = ticketId;
        this.createTime = createTime;
    }

    public void replace(TicketTimeRecord another) {
        if (another != null) {
            setTicketId(another.getTicketId());
            setCreateTime(another.getCreateTime());
        }
    }

    public boolean equalsId(String anotherId) {
        if (anotherId != null) {
            return anotherId.equals(ticketId);
        }
        return false;
    }

    public int compareCreateTime(long anotherTime) {
        if (createTime > anotherTime) {
            return 1;
        } else if (createTime < anotherTime) {
            return -1;
        }
        return 0;
    }

    public boolean isNewerThan(long anotherTime) {
        return (createTime > anotherTime);
    }

    public boolean isNewerThan(TicketTimeRecord another) {
        if (another != null) {
            return (createTime > another.getCreateTime());
        }
        return true;
    }

    public JSONObject export() {
        try {
            String jsonText = JsonUtil.marshal(this);
            return new JSONObject(jsonText);
        } catch (JsonProcessingException e) {
            log.error("error:{}", e);
        }
        return new JSONObject();
    }

    public JSONObject exportByFormattedDate() {
        JSONObject formatted = new JSONObject();
        String createTime = TicketUtil.getLocalTimeString(new Date(getCreateTime()));
        formatted.put("TicketId", getTicketId());
        formatted.put("CreateTime", createTime);
        return formatted;
    }

    public static TicketTimeRecord from(String jsonText) {
        if (jsonText != null) {
            try {
                return JsonUtil.unmarshal(jsonText, TicketTimeRecord.class);
            } catch (IOException e) {
                log.error("error : {}", e);
            }
        }
        return null;
    }
}
