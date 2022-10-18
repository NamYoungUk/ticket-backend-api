package com.sk.bds.ticket.api.exception;

import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.sk.bds.ticket.api.service.FreshdeskService;
import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.util.Date;

@Slf4j
@Data
public class AppInternalErrorNote {
    public enum ErrorType {
        TicketCreationFailure,
        TicketConversationSyncFailure,
        TicketAttachmentFailure,
        TicketStatusChangeFailure,
        TicketSlaUpdateFailure,
        Unknown;

        public static final String TicketCreationFailureText = "CSP 티켓 생성 실패";
        public static final String TicketConversationSyncFailureText = "대화 동기화 실패";
        public static final String TicketAttachmentFailureText = "파일 첨부 실패";
        public static final String TicketStatusChangeFailureText = "티켓 상태 변경 실패";
        public static final String TicketSlaUpdateFailureText = "SLA 정보 갱신 실패";
        public static final String UnknownText = "Unknown";

        public String text() {
            switch (this) {
                case TicketCreationFailure:
                    return TicketCreationFailureText;
                case TicketConversationSyncFailure:
                    return TicketConversationSyncFailureText;
                case TicketAttachmentFailure:
                    return TicketAttachmentFailureText;
                case TicketStatusChangeFailure:
                    return TicketStatusChangeFailureText;
                case TicketSlaUpdateFailure:
                    return TicketSlaUpdateFailureText;
            }
            return UnknownText;
        }

        public static ErrorType fromText(String text) {
            if (text != null) {
                if (text.equals(TicketCreationFailureText)) {
                    return TicketCreationFailure;
                } else if (text.equals(TicketConversationSyncFailureText)) {
                    return TicketConversationSyncFailure;
                } else if (text.equals(TicketAttachmentFailureText)) {
                    return TicketAttachmentFailure;
                } else if (text.equals(TicketStatusChangeFailureText)) {
                    return TicketStatusChangeFailure;
                } else if (text.equals(TicketSlaUpdateFailureText)) {
                    return TicketSlaUpdateFailure;
                }
            }
            return Unknown;
        }
    }

    ErrorType type;
    String cause;
    Date dateTime;

    public AppInternalErrorNote() {
        this.type = ErrorType.Unknown;
        this.cause = "";
        this.dateTime = new Date();
    }

    public AppInternalErrorNote(ErrorType type, String cause) {
        this.type = type;
        this.cause = cause;
        this.dateTime = new Date();
    }

    public AppInternalErrorNote(ErrorType type, String cause, Date date) {
        this.type = type;
        this.cause = cause;
        this.dateTime = date;
    }

    public static final String ErrorHeaderLabel = "[에러 노트]\n";
    public static final String ErrorTypeLabel = "에러 유형 : ";
    public static final String ErrorContentLabel = "에러 내용 : ";

    public String formattedText() {
        //AppConfig config = AppConfig.getInstance();
        String template = ErrorHeaderLabel +
                ErrorTypeLabel + "%s\n" +
                ErrorContentLabel + "%s\n\n" +
                "[%s:%s]";
        String errorType = "";
        String errorCause = "";
        String errorTime = TicketUtil.getLocalTimeString(dateTime);
        if (type != null) {
            errorType = type.text();
        }
        if (cause != null) {
            errorCause = cause;
            errorCause = errorCause.replaceAll(AppConstants.CSP_LINEFEED, AppConstants.FRESHDESK_LINEFEED);
        }
        template = template.replaceAll(AppConstants.CSP_LINEFEED, AppConstants.FRESHDESK_LINEFEED);
        return String.format(template, errorType, errorCause, AppConstants.CREATED_FROM_TICKET_MONITORING, errorTime);
    }

    public boolean isSameError(ErrorType errorType, String errorCause) {
        if (errorType != null && errorCause != null) {
            return this.type == errorType && errorCause.equals(this.cause);
        }
        return false;
    }

    public static boolean isErrorNote(JSONObject freshdeskConversation) {
        if (freshdeskConversation != null) {
            String htmlBody = freshdeskConversation.optString(FreshdeskTicketField.ConversationBodyHtml);
            AppInternalErrorNote errorNote = AppInternalErrorNote.from(htmlBody);
            return (errorNote != null);
        }
        return false;
    }

    public static AppInternalErrorNote from(String freshdeskBodyHtml) {
        if (freshdeskBodyHtml != null) {
            String body = freshdeskBodyHtml.replaceAll(AppConstants.FRESHDESK_LINEFEED, AppConstants.CSP_LINEFEED);
            if (body.contains(ErrorHeaderLabel) && body.contains(ErrorTypeLabel) && body.contains(ErrorContentLabel)) {
                String errorType = body.substring(body.indexOf(ErrorTypeLabel) + ErrorTypeLabel.length(), body.indexOf("\n" + ErrorContentLabel));
                String errorCause = body.substring(body.indexOf(ErrorContentLabel) + ErrorContentLabel.length(), body.indexOf("\n\n["));
                String timeString = TicketUtil.getTimeFromBodyTag(freshdeskBodyHtml, AppConstants.CREATED_FROM_TICKET_MONITORING, AppConstants.FRESHDESK_LINEFEED);
                Date errorTime = TicketUtil.parseLocalTime(timeString);
                return new AppInternalErrorNote(ErrorType.fromText(errorType), errorCause, errorTime);
            }
        }
        return null;
    }
}
