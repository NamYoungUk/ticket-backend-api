package com.sk.bds.ticket.api.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AppInternalErrorReason {
    NotSupported,
    NotImplemented,
    NotAuthorized,
    NoSubscription,
    NotFoundCspAccount,
    NoPermission,
    NotExistsTicket,
    EmptyTicketData,
    InvalidCsp,
    EmptyCspTicketId,
    NotBetaTester,
    NotEscalated,
    MissingParameters,
    OutOfSyncTargetTimeRange,
    InvalidCspAccount,
    AttachmentExceedNumber,
    AttachmentExceedSize,
    FailedToDownloadAttachment,
    CannotReadTicket,
    CannotReadTicketConversation,
    CannotReadTicketAttachment,
    ExceedConversationLimit,
    Conflict,
    FreshdeskApiError,
    FreshdeskApiCallRateLimitExceed,
    CspApiError,
    CloudzApiError,
    OpsgenieApiError,
    SlackApiError,
    PartialFailure,
    InternalProcessingError;

    public static final String keyReason = "reason";
    public static final String keyDetails = "details";

    @JsonProperty(keyDetails)
    @Getter
    String details;
    @JsonIgnore
    @Getter
    Exception exception;

    AppInternalErrorReason() {
        this.details = null;
    }

    AppInternalErrorReason(String details) {
        this.details = details;
    }

    public String getMessage() {
        if (details != null) {
            if (exception != null) {
                return this.name() + "\n" + details + "\n" + exception.getMessage();
            } else {
                return this.name() + "\n" + details;
            }
        } else if (exception != null) {
            return this.name() + "\n" + exception.getMessage();
        }
        return this.name();
    }

    public AppInternalErrorReason withDetails(String details) {
        this.details = details;
        return this;
    }

    public AppInternalErrorReason withException(Exception exception) {
        if (exception != null) {
            //this.details = exceptionString(exception);
            this.exception = exception;
            this.details = exception.getMessage();
        }
        return this;
    }

    public AppInternalErrorReason withDetailsException(String details, Exception exception) {
        this.details = details;
        if (exception != null) {
            this.exception = exception;
            if (details != null) {
                this.details += "\n" + exception.getMessage();
            } else {
                this.details = exception.getMessage();
            }
        }
        return this;
    }

    public AppInternalErrorReason withMissingFields(String... fields) {
        this.details = missingFieldNames(fields);
        return this;
    }

    public boolean hasDetails() {
        return (details != null);
    }

    public JSONObject export() {
        JSONObject export = new JSONObject();
        try {
            export.put(keyReason, this.name());
            export.put(keyDetails, details);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return export;
    }

    public String output() {
        return export().toString();
    }

    public static String missingFieldNames(String... fields) {
        if (fields != null && fields.length > 0) {
            if (fields.length > 1) {
                return String.format("Missing fields in [%s]", String.join(", ", fields));
            }
            return String.format("Missing fields in [%s]", fields[0]);
        }
        return "Missing fields []";
    }

    public static String exceptionString(Exception exception) {
        if (exception != null) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final String utf8 = StandardCharsets.UTF_8.name();
            PrintStream printStream = null;
            try {
                printStream = new PrintStream(baos, true, utf8);
                exception.printStackTrace(printStream);
                String data = baos.toString(utf8);
                return data;
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            } finally {
                if (printStream != null) {
                    printStream.close();
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "AppInternalErrorReason{" +
                keyReason + "='" + this.name() + '\'' +
                "details='" + details + '\'' +
                ", exception=" + exception +
                '}';
    }

}
