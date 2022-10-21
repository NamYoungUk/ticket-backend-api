package com.sk.bds.ticket.api.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Data
@EqualsAndHashCode(callSuper = false)
public class AppError extends Exception {
    int statusCode;
    String errorCause;
    String errorDetails;
    AppErrorReason appErrorReason;

    public AppError(AppErrorReason reason) {
        super(((reason != null) ? ("\n" + reason.output()) : ""));
        if (reason != null) {
            this.statusCode = reason.code();
            this.errorCause = reason.cause();
            this.errorDetails = reason.details();
            this.appErrorReason = reason;
        }
    }

    public AppError(HttpStatus status, String errorDetails) {
        super(((status != null) ? ("\n" + status.getReasonPhrase()) : "") + ((errorDetails != null) ? ("\n" + errorDetails) : ""));
        if (status != null) {
            this.statusCode = status.value();
            this.errorCause = status.getReasonPhrase();
        }
        this.errorDetails = errorDetails;
    }

    public AppError(HttpStatus status, Exception exception) {
        super(exception);
        if (status != null) {
            this.statusCode = status.value();
            this.errorCause = status.getReasonPhrase();
        }
        this.errorDetails = exceptionString(exception);
    }

    public static class BadRequest extends AppError {
        public BadRequest() {
            super(AppErrorReason.BadRequest);
        }

        public BadRequest(String details) {
            super(AppErrorReason.BadRequest.withDetails(details));
        }

        public BadRequest(Exception exception) {
            super(AppErrorReason.BadRequest.withException(exception));
        }
    }

    public static class Unauthorized extends AppError {
        public Unauthorized() {
            super(AppErrorReason.Unauthorized);
        }

        public Unauthorized(String details) {
            super(AppErrorReason.Unauthorized.withDetails(details));
        }

        public Unauthorized(Exception exception) {
            super(AppErrorReason.Unauthorized.withException(exception));
        }
    }

    public static class Forbidden extends AppError {
        public Forbidden() {
            super(AppErrorReason.Forbidden);
        }

        public Forbidden(String details) {
            super(AppErrorReason.Forbidden.withDetails(details));
        }

        public Forbidden(Exception exception) {
            super(AppErrorReason.Forbidden.withException(exception));
        }
    }

    public static class NotFound extends AppError {
        public NotFound() {
            super(AppErrorReason.NotFound);
        }

        public NotFound(String details) {
            super(AppErrorReason.NotFound.withDetails(details));
        }

        public NotFound(Exception exception) {
            super(AppErrorReason.NotFound.withException(exception));
        }
    }

    public static class NotAcceptable extends AppError {
        public NotAcceptable() {
            super(AppErrorReason.NotAcceptable);
        }

        public NotAcceptable(String details) {
            super(AppErrorReason.NotAcceptable.withDetails(details));
        }

        public NotAcceptable(Exception exception) {
            super(AppErrorReason.NotAcceptable.withException(exception));
        }
    }

    public static class RequestTimeout extends AppError {
        public RequestTimeout() {
            super(AppErrorReason.RequestTimeout);
        }

        public RequestTimeout(String details) {
            super(AppErrorReason.RequestTimeout.withDetails(details));
        }

        public RequestTimeout(Exception exception) {
            super(AppErrorReason.RequestTimeout.withException(exception));
        }
    }

    public static class Conflict extends AppError {
        public Conflict() {
            super(AppErrorReason.Conflict);
        }

        public Conflict(String details) {
            super(AppErrorReason.Conflict.withDetails(details));
        }

        public Conflict(Exception exception) {
            super(AppErrorReason.Conflict.withException(exception));
        }
    }

    public static class UnsupportedMediaType extends AppError {
        public UnsupportedMediaType() {
            super(AppErrorReason.UnsupportedMediaType);
        }

        public UnsupportedMediaType(String details) {
            super(AppErrorReason.UnsupportedMediaType.withDetails(details));
        }

        public UnsupportedMediaType(Exception exception) {
            super(AppErrorReason.UnsupportedMediaType.withException(exception));
        }
    }

    public static class InternalServerError extends AppError {
        public InternalServerError() {
            super(AppErrorReason.InternalServerError);
        }

        public InternalServerError(String details) {
            super(AppErrorReason.InternalServerError.withDetails(details));
        }

        public InternalServerError(Exception exception) {
            super(AppErrorReason.InternalServerError.withException(exception));
        }
    }

    public static class NotImplemented extends AppError {
        public NotImplemented() {
            super(AppErrorReason.NotImplemented);
        }

        public NotImplemented(String details) {
            super(AppErrorReason.NotImplemented.withDetails(details));
        }

        public NotImplemented(Exception exception) {
            super(AppErrorReason.NotImplemented.withException(exception));
        }
    }

    public static class ServiceUnavailable extends AppError {
        public ServiceUnavailable() {
            super(AppErrorReason.ServiceUnavailable);
        }

        public ServiceUnavailable(String details) {
            super(AppErrorReason.ServiceUnavailable.withDetails(details));
        }

        public ServiceUnavailable(Exception exception) {
            super(AppErrorReason.ServiceUnavailable.withException(exception));
        }
    }

    public static BadRequest badRequest(String details) {
        return new AppError.BadRequest(details);
    }

    public static InternalServerError internalError(String details) {
        return new AppError.InternalServerError(details);
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

}
