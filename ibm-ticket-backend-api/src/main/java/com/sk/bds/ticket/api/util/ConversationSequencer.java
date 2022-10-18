package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.softlayer.api.service.ticket.Update;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.util.*;

@Slf4j
public class ConversationSequencer {
    //List<JSONObject> freshdeskConversations;
    //List<Update> cspConversations;
    //List<com.softlayer.api.service.ticket.attachment.File> cspAttachedFiles;
    List<ConversationItem> conversationItems;
    int conversationReadIndex;

    public ConversationSequencer(Collection<JSONObject> freshdeskConversations, Collection<Update> cspConversations, Collection<com.softlayer.api.service.ticket.attachment.File> cspAttachedFiles) {
        //this.freshdeskConversations = freshdeskConversations;
        //this.cspConversations = cspConversations;
        //this.cspAttachedFiles = cspAttachedFiles;
        conversationItems = new ArrayList<>();
        if (freshdeskConversations != null) {
            freshdeskConversations.forEach(conversation -> conversationItems.add(new ConversationItem(conversation)));
            //freshdeskConversations.forEach(conversation -> log.debug("freshdesk conversation: {}", conversation));
        }
        if (cspConversations != null) {
            cspConversations.forEach(conversation -> conversationItems.add(new ConversationItem(conversation)));
            //cspConversations.forEach(conversation -> IbmService.printIbmUpdate(conversation));
        }
        if (cspAttachedFiles != null) {
            cspAttachedFiles.forEach(attachedFile -> conversationItems.add(new ConversationItem(attachedFile)));
            //cspAttachedFiles.forEach(attachedFile -> IbmService.printIbmAttachmentFile(attachedFile));
        }
        arrange();
        conversationReadIndex = 0;
    }

    private void arrange() {
        //log.info("conversationItems before arrange: {}", conversationItems);
        Collections.sort(conversationItems, new Comparator<ConversationItem>() {
            @Override
            public int compare(ConversationItem o1, ConversationItem o2) {
                return o1.compareTo(o2);
            }
        });
        //log.info("conversationItems after arrange: {}", conversationItems);
    }

    public enum ConversationItemType {
        freshdeskConversation,
        cspConversation,
        cspAttachedFile
    }

    public static class ConversationItem {
        @Getter
        ConversationItemType type;
        Object conversation;
        long createdTime;

        public ConversationItem(JSONObject freshdeskConversation) {
            type = ConversationItemType.freshdeskConversation;
            conversation = freshdeskConversation;
            createdTime = 0;
            checkCreatedTime();
        }

        public ConversationItem(Update cspConversation) {
            type = ConversationItemType.cspConversation;
            conversation = cspConversation;
            createdTime = 0;
            checkCreatedTime();
        }

        public ConversationItem(com.softlayer.api.service.ticket.attachment.File cspAttachedFile) {
            type = ConversationItemType.cspAttachedFile;
            conversation = cspAttachedFile;
            createdTime = 0;
            checkCreatedTime();
        }

        public JSONObject asFreshdeskConversation() {
            return (JSONObject) conversation;
        }

        public Update asCspConversation() {
            return (Update) conversation;
        }

        public com.softlayer.api.service.ticket.attachment.File asCspAttachedFile() {
            return (com.softlayer.api.service.ticket.attachment.File) conversation;
        }

        private void checkCreatedTime() {
            switch (type) {
                case freshdeskConversation:
                    createdTime = TicketUtil.getTimeByFreshdeskTime(asFreshdeskConversation().optString(FreshdeskTicketField.CreatedAt));
                    break;
                case cspConversation:
                    createdTime = asCspConversation().getCreateDate().getTimeInMillis();
                    break;
                case cspAttachedFile:
                    createdTime = asCspAttachedFile().getCreateDate().getTimeInMillis();
                    break;
            }
        }

        public long getCreatedTime() {
            return createdTime;
        }

        public int compareTo(ConversationItem another) {
            if (another != null) {
                if (getCreatedTime() > another.getCreatedTime()) {
                    return 1;
                } else if (getCreatedTime() < another.getCreatedTime()) {
                    return -1;
                }
            }
            return 0;
        }

        @Override
        public String toString() {
            return "ConversationItem{" +
                    "type=" + type +
                    ", conversation=" + conversation +
                    ", createdTime=" + createdTime +
                    '}';
        }
    }

    public boolean hasNext() {
        return conversationReadIndex < conversationItems.size();
    }

    public ConversationItem next() {
        if (hasNext()) {
            //log.debug("conversationReadIndex: {}", conversationReadIndex);
            return conversationItems.get(conversationReadIndex++);
        }
        return null;
    }
}
