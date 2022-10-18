package com.sk.bds.ticket.api.data.model.ibm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskAttachment;
import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Slf4j
public class IbmAttachedFileMetadata {
    long ibmFileId;
    long ibmUpdateId;
    String fileName;
    Date fileCreatedTime;
    //long size;
    //long crc32;

    public IbmAttachedFileMetadata() {
        fileName = null;
        fileCreatedTime = new Date();
        ibmFileId = 0;
        ibmUpdateId = 0;
    }

    public IbmAttachedFileMetadata(com.softlayer.api.service.ticket.attachment.File ibmFile) {
        if (ibmFile != null) {
            fileName = ibmFile.getFileName();
            fileCreatedTime = ibmFile.getCreateDate().getTime();
            ibmFileId = ibmFile.getId();
            ibmUpdateId = ibmFile.getUpdateId();
        } else {
            fileName = null;
            fileCreatedTime = new Date();
            ibmFileId = 0;
        }
    }

    public IbmAttachedFileMetadata(String fileName, Date fileCreatedTime) {
        this.fileName = fileName;
        this.fileCreatedTime = fileCreatedTime;
        ibmFileId = 0;
        ibmUpdateId = 0;
    }

    public IbmAttachedFileMetadata(String fileName, Date fileCreatedTime, long ibmFileId, long ibmUpdateId) {
        this.fileName = fileName;
        this.fileCreatedTime = fileCreatedTime;
        this.ibmFileId = ibmFileId;
        this.ibmUpdateId = ibmUpdateId;
    }

    @JsonIgnore
    public boolean isAvailableIbmFileId() {
        return (ibmFileId != 0);
    }

    @JsonIgnore
    public boolean isAvailableIbmUpdateId() {
        return (ibmUpdateId != 0);
    }

    public boolean equals(com.softlayer.api.service.ticket.attachment.File ibmFile) {
        if (ibmFile != null) {
            if (getIbmFileId() != 0 && getIbmFileId() == ibmFile.getId()) {
                return (getFileName() != null) && getFileName().equals(ibmFile.getFileName());
            } else if (getFileName() != null && getFileCreatedTime() != null && ibmFile.getCreateDate() != null) {
                //image.png [2020-05-28T14:38:04 +09:00] 대화에 파일 메타 정보 작성시 초단위까지만 작성되므로 초단위로 비교.
                //변경 전의 파일 메타 정보에는 파일 Id가 포함되어 있지 않아 비교할 수 없으므로, 기존 방식대로 파일이름과 시간 비교.
                long diffSeconds = ((getFileCreatedTime().getTime() - ibmFile.getCreateDate().getTimeInMillis())) / 1000;
                log.debug("diffTime: {}, {} - {}", diffSeconds, getFileCreatedTime().getTime(), ibmFile.getCreateDate().getTimeInMillis());
                return getFileName().equals(ibmFile.getFileName()) && (diffSeconds == 0);
            }
        }
        return false;
    }

    public String formattedMetadataText(boolean escapeHtml) {
        //image.png [2020-05-28T14:38:04 +09:00]        //deprecated pattern.
        //image.png [ibm-첨부파일-Id, 2021-11-19T19:22:08 +09:00]   //new pattern.
        String fileTimeString = TicketUtil.getLocalTimeString(getFileCreatedTime());
        if (escapeHtml) {
            String escapedFileName = StringEscapeUtils.escapeHtml4(getFileName());
            return String.format("%s [%d,%s]", escapedFileName, getIbmFileId(), fileTimeString);
        }
        return String.format("%s [%d,%s]", getFileName(), getIbmFileId(), fileTimeString);
    }

    public String buildConversationBodyForAttachment() {
        String localTimeString = TicketUtil.getLocalTimeString(getFileCreatedTime());
        String bodyContent = AppConstants.IBM_FILE_CONTENT_BODY_HEADER + formattedMetadataText(true);
        bodyContent = bodyContent.replaceAll(AppConstants.CSP_LINEFEED, AppConstants.FRESHDESK_LINEFEED);
        return String.format("%s%s[%s:%d,%s]", bodyContent, AppConstants.FRESHDESK_LINEFEED_TWO_LINE, AppConstants.CREATED_FROM_CSP, ibmUpdateId, localTimeString);
    }

    public static List<IbmAttachedFileMetadata> getFileMetadataListFromFreshdeskConversationHtmlBody(String htmlBody) {
        List<IbmAttachedFileMetadata> metaList = new ArrayList<>();
        log.debug("htmlBody: {}", htmlBody);
        if (htmlBody != null) {
            String ibmUpdateIdString = TicketUtil.getIdFromBodyTag(htmlBody, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
            long updateId = Long.valueOf(ibmUpdateIdString);
            //Not allowed characters - "[:\\\\/%*?:|\"<>]"
            //<div>File attached from IBM<br>-------------------------<br>image.png [ibm-첨부파일-Id,2021-11-19T19:22:08 +09:00]<br><br>[CREATED_FROM_IBM:IBM대화Id,2021-11-19T19:24:08 +09:00]</div>
            final String searchPattern1 = "<br>([^\\\\/:*?\"<>|]+)[\\s]+[\\[]{1}([\\d]*),[\\s]?([\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2})[\\]]{1}";
            Pattern pattern = Pattern.compile(searchPattern1);
            Matcher matcher = pattern.matcher(htmlBody);
            while (matcher.find()) {
                //freshdesk tag encoding에 의해 파일 이름이 변경되는 문제 방지.
                String unEscapedFileName = StringEscapeUtils.unescapeHtml4(matcher.group(1));
                Date fileCreateTime = TicketUtil.parseLocalTime(matcher.group(3));
                long fileId = Long.valueOf(matcher.group(2));
                IbmAttachedFileMetadata meta = new IbmAttachedFileMetadata(unEscapedFileName, fileCreateTime, fileId, updateId);
                log.debug("meta: {}", meta);
                metaList.add(meta);
            }
            //If first pattern not matched.
            if (metaList.size() == 0) {
                //<div>File attached from IBM<br>-------------------------<br>image.png [2020-05-28T14:38:04 +09:00]<br><br>[CREATED_FROM_IBM:0,2020-05-28T14:38:04 +09:00]</div>
                final String searchPattern2 = "<br>([^\\\\/:*?\"<>|]+)[\\s]+[\\[]{1}([\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2})[\\]]{1}";
                final long ibmFileId = 0;
                pattern = Pattern.compile(searchPattern2);
                matcher = pattern.matcher(htmlBody);
                while (matcher.find()) {
                    //freshdesk tag encoding에 의해 파일 이름이 변경되는 문제 방지.
                    String unEscapedFileName = StringEscapeUtils.unescapeHtml4(matcher.group(1));
                    Date fileCreateTime = TicketUtil.parseLocalTime(matcher.group(2));
                    IbmAttachedFileMetadata meta = new IbmAttachedFileMetadata(unEscapedFileName, fileCreateTime, ibmFileId, updateId);
                    log.debug("meta: {}", meta);
                    metaList.add(meta);
                }
            }
        }
        return metaList;
    }

}
