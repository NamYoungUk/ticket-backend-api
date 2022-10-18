package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.LocalDateDeserializer;
import com.sk.bds.ticket.api.util.LocalDateSerializer;
import com.sk.bds.ticket.api.util.Util;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Data
public class TicketErrorNoteMetadata {
    private static final String KeyTime = "time";
    private static final String KeyFailCause = "cause";
    private static final String KeyCreateFail = "create-fail";
    private static final String KeySyncFail = "sync-fail";
    private static final String KeyChangeStatusFail = "change-status-fail";
    private static final String MetaFileExtension = ".json";

    @Data
    public static class ErrorMetadata {
        @JsonProperty(KeyTime)
        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        Date dateTime;
        @JsonProperty(KeyFailCause)
        String cause;
    }

    @JsonIgnore
    String ticketId;
    @JsonProperty(KeyCreateFail)
    ErrorMetadata ticketCreationFailure;
    @JsonProperty(KeySyncFail)
    ErrorMetadata conversationSyncFailure;
    @JsonProperty(KeyChangeStatusFail)
    ErrorMetadata ticketStatusChangeFailure;

    public TicketErrorNoteMetadata() {
        this.ticketId = null;
    }

    public TicketErrorNoteMetadata(String ticketId) {
        this.ticketId = ticketId;
    }

    public boolean hasTicketCreationFail() {
        return (ticketCreationFailure != null);
    }

    public boolean hasConversationSyncFail() {
        return (conversationSyncFailure != null);
    }

    public boolean hasTicketStatusChangeFail() {
        return (ticketStatusChangeFailure != null);
    }

    public boolean isDuplicatedTicketCreationFailCause(String cause) {
        if (ticketCreationFailure != null && cause != null) {
            return cause.equals(ticketCreationFailure.getCause());
        }
        return false;
    }

    public boolean isDuplicatedConversationSyncFailCause(String cause) {
        if (conversationSyncFailure != null && cause != null) {
            return cause.equals(conversationSyncFailure.getCause());
        }
        return false;
    }

    public boolean isDuplicatedTicketStatusChangeFailCause(String cause) {
        if (ticketStatusChangeFailure != null && cause != null) {
            return cause.equals(ticketStatusChangeFailure.getCause());
        }
        return false;
    }

    public void onSuccessTicketCreation() {
        if (hasTicketCreationFail()) {
            this.ticketCreationFailure = null;
            storeFile();
        }
    }

    public void onSuccessConversationSync() {
        if (hasConversationSyncFail()) {
            this.ticketCreationFailure = null;
            this.conversationSyncFailure = null;
            storeFile();
        }
    }

    public void onSuccessTicketStatusChange() {
        if (hasTicketStatusChangeFail()) {
            this.ticketCreationFailure = null;
            this.ticketStatusChangeFailure = null;
            storeFile();
        }
    }

    public void onFailedTicketCreation(String cause) {
        if (cause == null || cause.trim().length() < 1) {
            return;
        }

        if (isDuplicatedTicketCreationFailCause(cause)) {
            return;
        } else {
            ticketCreationFailure = new ErrorMetadata();
        }
        ticketCreationFailure.setDateTime(new Date());
        ticketCreationFailure.setCause(cause);
        storeFile();
    }

    public void onFailedConversationSync(String cause) {
        if (cause == null || cause.trim().length() < 1) {
            return;
        }

        if (isDuplicatedConversationSyncFailCause(cause)) {
            return;
        } else {
            conversationSyncFailure = new ErrorMetadata();
        }
        conversationSyncFailure.setDateTime(new Date());
        conversationSyncFailure.setCause(cause);
        storeFile();
    }

    public void onFailedTicketStatusChange(String cause) {
        if (cause == null || cause.trim().length() < 1) {
            return;
        }

        if (isDuplicatedTicketStatusChangeFailCause(cause)) {
            return;
        } else {
            ticketStatusChangeFailure = new ErrorMetadata();
        }
        ticketStatusChangeFailure.setDateTime(new Date());
        ticketStatusChangeFailure.setCause(cause);
        storeFile();
    }

    public void clear() {
        this.ticketCreationFailure = null;
        this.conversationSyncFailure = null;
        this.ticketStatusChangeFailure = null;
    }

    private File getMetaFile() {
        String errorNotePath = AppConfig.getAppErrorNotePath();
        return new File(errorNotePath, ticketId + MetaFileExtension);
    }

    public void deleteFile() {
        FileUtils.deleteQuietly(getMetaFile());
    }

    public void storeFile() {
        try {
            String metaText = JsonUtil.marshal(this);
            Util.writeFile(getMetaFile(), metaText);
        } catch (IOException e) {
            log.error("error: {}", e);
        }
    }

    public static Map<String, TicketErrorNoteMetadata> loadAllErrorNoteMetadatas() {
        Map<String, TicketErrorNoteMetadata> metadataMap = new TreeMap<>();
        String errorNotePath = AppConfig.getAppErrorNotePath();
        File dir = new File(errorNotePath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            Util.sortFileByName(files);
            for (File file : files) {
                String fileName = file.getName();
                if (file.isFile() && fileName.endsWith(MetaFileExtension)) {
                    try {
                        String ticketId = fileName.substring(0, fileName.lastIndexOf(MetaFileExtension));
                        String metadataText = Util.readFile(file);
                        TicketErrorNoteMetadata meta = JsonUtil.unmarshal(metadataText, TicketErrorNoteMetadata.class);
                        meta.setTicketId(ticketId);
                        metadataMap.put(ticketId, meta);
                    } catch (IOException e) {
                        log.error("Metadata file {} reading failed. {}", fileName, e);
                    }
                }
            }
        }
        return metadataMap;
    }

    public static TicketErrorNoteMetadata loadErrorNoteMetadata(String ticketId) {
        if (ticketId != null) {
            String errorNotePath = AppConfig.getAppErrorNotePath();
            try {
                File metaFile = new File(errorNotePath, ticketId + MetaFileExtension);
                if (metaFile.exists()) {
                    String metadataText = Util.readFile(metaFile);
                    TicketErrorNoteMetadata errorNoteMetadata = JsonUtil.unmarshal(metadataText, TicketErrorNoteMetadata.class);
                    errorNoteMetadata.setTicketId(ticketId);
                    return errorNoteMetadata;
                }
            } catch (IOException e) {
                log.error("error: {}", e);
            }
        }
        return null;
    }

    public static void deleteErrorNoteMetadata(String ticketId) {
        if (ticketId != null) {
            String errorNotePath = AppConfig.getAppErrorNotePath();
            File metaFile = new File(errorNotePath, ticketId + MetaFileExtension);
            if (metaFile.exists()) {
                FileUtils.deleteQuietly(metaFile);
            }
        }
    }

    public static void onSuccessTicketCreation(String ticketId) {
        if (ticketId != null) {
            TicketErrorNoteMetadata metadata = loadErrorNoteMetadata(ticketId);
            if (metadata != null) {
                metadata.onSuccessTicketCreation();
            }
        }
    }

    public static void onSuccessConversationSync(String ticketId) {
        if (ticketId != null) {
            TicketErrorNoteMetadata metadata = loadErrorNoteMetadata(ticketId);
            if (metadata != null) {
                metadata.onSuccessConversationSync();
            }
        }
    }

    public static void onSuccessTicketStatusChange(String ticketId) {
        if (ticketId != null) {
            TicketErrorNoteMetadata metadata = loadErrorNoteMetadata(ticketId);
            if (metadata != null) {
                metadata.onSuccessTicketStatusChange();
            }
        }
    }

    public static void onFailedTicketCreation(String ticketId, String cause) {
        if (ticketId == null || ticketId.trim().length() < 1 || cause == null || cause.trim().length() < 1) {
            return;
        }
        TicketErrorNoteMetadata metadata = loadErrorNoteMetadata(ticketId);
        if (metadata == null) {
            metadata = new TicketErrorNoteMetadata(ticketId);
        }
        metadata.onFailedTicketCreation(cause);
    }

    public static void onFailedConversationSync(String ticketId, String cause) {
        if (ticketId == null || ticketId.trim().length() < 1 || cause == null || cause.trim().length() < 1) {
            return;
        }
        TicketErrorNoteMetadata metadata = loadErrorNoteMetadata(ticketId);
        if (metadata == null) {
            metadata = new TicketErrorNoteMetadata(ticketId);
        }
        metadata.onFailedConversationSync(cause);
    }

    public static void onFailedTicketStatusChange(String ticketId, String cause) {
        if (ticketId == null || ticketId.trim().length() < 1 || cause == null || cause.trim().length() < 1) {
            return;
        }
        TicketErrorNoteMetadata metadata = loadErrorNoteMetadata(ticketId);
        if (metadata == null) {
            metadata = new TicketErrorNoteMetadata(ticketId);
        }
        metadata.onFailedTicketStatusChange(cause);
    }

}
