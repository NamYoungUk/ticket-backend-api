package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.service.FreshdeskService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;

@Slf4j
public class FreshdeskConversationLoader {
    private boolean hasMore;
    private int targetPage;
    private String ticketId;

    public FreshdeskConversationLoader(String ticketId) {
        this.ticketId = ticketId;
        targetPage = 0;
        hasMore = true;
    }

    public FreshdeskConversationLoader(String ticketId, int skipCount) {
        this.ticketId = ticketId;
        if (skipCount > FreshdeskService.CONVERSATION_LIST_ITEMS_PER_PAGE) {
            targetPage = skipCount / FreshdeskService.CONVERSATION_LIST_ITEMS_PER_PAGE;
        } else {
            targetPage = 0;
        }
        hasMore = true;
    }

    public boolean hasNext() {
        return hasMore;
    }

    public JSONArray next() throws AppInternalError {
        targetPage++;
        try {
            log.info("Retrieving the freshdesk ticket {} conversations. - page: {} ", ticketId, targetPage);
            JSONArray conversations = FreshdeskService.getConversations(ticketId, targetPage);
            hasMore = (conversations != null && conversations.length() >= FreshdeskService.CONVERSATION_LIST_ITEMS_PER_PAGE);
            if (conversations != null) {
                log.info("Retrieved the freshdesk ticket: {}, page: {}, conversations: {}", ticketId, targetPage, conversations.length());
            } else {
                log.info("Failed to retrieve the freshdesk ticket {} conversations. - page: {} ", ticketId, targetPage);
            }
            return conversations;
        } catch (AppInternalError e) {
            log.error("Failed to retrieve the freshdesk ticket {} conversations. - page: {}, error: {} ", ticketId, targetPage, e);
            hasMore = false;
            throw e;
        }
    }

    public JSONArray nextSafety() {
        try {
            return next();
        } catch (AppInternalError e) {
            Util.ignoreException(e);
        }
        return new JSONArray();
    }

    public int currentPage() {
        return targetPage;
    }

    public static FreshdeskConversationLoader by(String ticketId) {
        return new FreshdeskConversationLoader(ticketId);
    }

    public static FreshdeskConversationLoader by(String ticketId, int skipCount) {
        return new FreshdeskConversationLoader(ticketId, skipCount);
    }
}
