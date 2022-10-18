package com.sk.bds.ticket.api.data.model.freshdesk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.Util;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Slf4j
public class FreshdeskTicketUrlMapper {
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final Object fileLocker = new Object();

    long updatedTime;
    Map<String, String> ticketUrlList;

    public FreshdeskTicketUrlMapper() {
        ticketUrlList = new ConcurrentHashMap<>();
    }

    public void setPublicUrl(String freshdeskTicketId, String freshdeskTicketPublicUrl) {
        if (freshdeskTicketId != null && freshdeskTicketPublicUrl != null) {
            if (!ticketUrlList.containsKey(freshdeskTicketId)) {
                updatedTime = System.currentTimeMillis();
                ticketUrlList.put(freshdeskTicketId, freshdeskTicketPublicUrl);
                storeMapping();
            }
        }
    }

    public String getPublicUrl(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            return ticketUrlList.get(freshdeskTicketId);
        }
        return null;
    }

    public void eraseUrl(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            if (ticketUrlList.containsKey(freshdeskTicketId)) {
                updatedTime = System.currentTimeMillis();
                ticketUrlList.remove(freshdeskTicketId);
                storeMapping();
            }
        }
    }

    public void storeMapping() {
        try {
            synchronized (fileLocker) {
                String exportText = JsonUtil.marshal(this);
                log.debug("exportText: {}", exportText);
                Util.writeFile(AppConfig.getTicketUrlMappingFilePath(), JsonUtil.prettyPrint(exportText));
            }
        } catch (IOException e) {
            log.error("Failed to store url mapping. {}", e);
        }
    }

    public static FreshdeskTicketUrlMapper loadMapping() {
        try {
            synchronized (fileLocker) {
                String urlMapJsonText = Util.readFile(AppConfig.getTicketUrlMappingFilePath());
                log.info("urlMapJsonText: {}", urlMapJsonText);
                FreshdeskTicketUrlMapper mapper = JsonUtil.unmarshal(urlMapJsonText, FreshdeskTicketUrlMapper.class);
                return mapper;
            }
        } catch (IOException e) {
            log.error("Failed to load url mapping from file. {}", e);
        }
        return new FreshdeskTicketUrlMapper();
    }
}
