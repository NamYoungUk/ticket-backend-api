package com.sk.bds.ticket.api.data.model.freshdesk;

import com.sk.bds.ticket.api.util.Util;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

@Data
public class FreshdeskAttachment {
    private byte[] data;
    private String fileName;
    private Date createdAt;
    private Date modifiedAt;

    public FreshdeskAttachment(byte[] data, String fileName) {
        this.data = data;
        this.fileName = fileName;
        this.createdAt = new Date();
        this.modifiedAt = new Date();
    }

    public FreshdeskAttachment(byte[] data, String fileName, Date createdAt, Date modifiedAt) {
        this.data = data;
        this.fileName = fileName;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        if (this.createdAt == null) {
            this.createdAt = new Date();
        }
        if (this.modifiedAt == null) {
            this.modifiedAt = new Date();
        }
    }

    public FreshdeskAttachment(String dataText, String fileName) {
        this.data = dataText.getBytes(StandardCharsets.UTF_8);
        this.fileName = fileName;
        this.createdAt = new Date();
        this.modifiedAt = new Date();
    }

    public FreshdeskAttachment(File attachFile) throws IOException {
        if (attachFile != null && attachFile.isFile()) {
            this.fileName = attachFile.getName();
            //BasicFileAttributes attr = Util.readFileAttributes(attachFile);
            //this.createdAt = new Date(attr.creationTime().toMillis());
            //this.modifiedAt = new Date(attr.lastModifiedTime().toMillis());
            this.createdAt = new Date(attachFile.lastModified());
            this.modifiedAt = new Date(attachFile.lastModified());
            this.data = Util.readFileToBytes(attachFile);
        }
    }

    public int getSize() {
        if (data != null) {
            return data.length;
        }
        return 0;
    }
}
