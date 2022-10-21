package com.sk.bds.ticket.api.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AppErrorReason {
    BadRequest(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase()),
    Unauthorized(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase()),
    Forbidden(HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase()),
    NotFound(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase()),
    NotAcceptable(HttpStatus.NOT_ACCEPTABLE.value(), HttpStatus.NOT_ACCEPTABLE.getReasonPhrase()),
    RequestTimeout(HttpStatus.REQUEST_TIMEOUT.value(), HttpStatus.REQUEST_TIMEOUT.getReasonPhrase()),
    Conflict(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase()),
    UnsupportedMediaType(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase()),
    InternalServerError(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()),
    NotImplemented(HttpStatus.NOT_IMPLEMENTED.value(), HttpStatus.NOT_IMPLEMENTED.getReasonPhrase()),
    ServiceUnavailable(HttpStatus.SERVICE_UNAVAILABLE.value(), HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());

    public static final String keyCode = "code";
    public static final String keyCause = "cause";
    public static final String keyDetails = "details";

    @JsonProperty(keyCode)
    final int code;
    @JsonProperty(keyCause)
    final String cause;
    @JsonProperty(keyDetails)
    String details;

    AppErrorReason(int code, String cause) {
        this.code = code;
        this.cause = cause;
        this.details = null;
    }

    AppErrorReason(int code, String cause, String details) {
        this.code = code;
        this.cause = cause;
        this.details = details;
    }

    public int code() {
        return code;
    }

    public String cause() {
        return cause;
    }

    public String details() {
        return details;
    }

    public AppErrorReason withDetails(String details) {
        this.details = details;
        return this;
    }

    public AppErrorReason withException(Exception exception) {
        this.details = exceptionString(exception);
        return this;
    }

    public AppErrorReason withMissingFields(String... fields) {
        this.details = missingFieldNames(fields);
        return this;
    }

    public boolean hasDetails() {
        return (details != null);
    }

    public JSONObject export() {
        JSONObject export = new JSONObject();
        try {
            export.put(keyCode, code);
            if (hasDetails()) {
                export.put(keyCause, cause + " - " + details);
            } else {
                export.put(keyCause, cause);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return export;
    }

    public String output() {
        return export().toString();
    }

    @JsonCreator
    public static AppErrorReason fromJson(@JsonProperty(keyCode) int code) {
        for (AppErrorReason reason : AppErrorReason.values()) {
            if (reason.code() == code) {
                return reason;
            }
        }
        return null;
    }

    /*@JsonCreator
    public static AppErrorReason fromJsonNode(JsonNode node) {
        if (node.has(keyCode)) {
            JsonNode codeNode = node.get(keyCode);
            int code = codeNode.asInt();
            for (AppErrorReason reason : AppErrorReason.values()) {
                if (reason.code() == code) {
                    return reason;
                }
            }
        }
        throw new IllegalArgumentException();
    }*/

    public static String missingFieldNames(String... fields) {
        if (fields != null && fields.length > 0) {
            if (fields.length > 1) {
                return String.format("Missing fields : %s", String.join(", ", fields));
            }
            return String.format("Missing field : %s", fields[0]);
        }
        return "";
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
        return "AppErrorReason{" +
                "code=" + code +
                ", cause='" + cause + '\'' +
                ", details='" + details + '\'' +
                '}';
    }
}
