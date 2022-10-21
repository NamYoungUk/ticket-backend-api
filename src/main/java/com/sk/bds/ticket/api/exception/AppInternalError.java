package com.sk.bds.ticket.api.exception;

import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AppInternalError extends Exception {
    @Getter
    AppInternalErrorReason errorReason;
    @Getter
    boolean noteEnabled;
    @Getter
    List<String> tags;

    public AppInternalError(AppInternalErrorReason errorReason) {
        this.errorReason = errorReason;
        this.noteEnabled = false;
        this.tags = null;
    }

    @Override
    public String getMessage() {
        if (errorReason != null) {
            return errorReason.getMessage();
        }
        return super.getMessage();
    }

    public boolean equalsReason(AppInternalErrorReason reason) {
        return (reason != null) && (errorReason == reason);
    }

    public AppInternalError note(boolean enable) {
        this.noteEnabled = enable;
        return this;
    }

    public AppInternalError tag(String... tagArray) {
        if (tagArray != null) {
            if (tags == null) {
                tags = new ArrayList<>();
            }
            for (String tag : tagArray) {
                if (!tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        return this;
    }

    public void removeTag(String tag) {
        if (tag != null) {
            if (tags != null) {
                tags.remove(tag);
            }
        }
    }

    public boolean isTagged(String tag) {
        if (tag != null) {
            if (tags != null) {
                return tags.contains(tag);
            }
        }
        return false;
    }

    public boolean hasDetails() {
        if (errorReason != null) {
            return errorReason.getDetails() != null;
        }
        return false;
    }

    public String getDetails() {
        if (errorReason != null) {
            return errorReason.getDetails();
        }
        return "";
    }

    public static AppInternalError notSupported() {
        return new AppInternalError(AppInternalErrorReason.NotSupported);
    }

    public static AppInternalError notSupported(String details) {
        return new AppInternalError(AppInternalErrorReason.NotSupported.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError notImplemented() {
        return new AppInternalError(AppInternalErrorReason.NotImplemented);
    }

    public static AppInternalError notImplemented(String details) {
        return new AppInternalError(AppInternalErrorReason.NotImplemented.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError notAuthorized() {
        return new AppInternalError(AppInternalErrorReason.NotAuthorized);
    }

    public static AppInternalError notAuthorized(String details) {
        return new AppInternalError(AppInternalErrorReason.NotAuthorized.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError noSubscription() {
        return new AppInternalError(AppInternalErrorReason.NoSubscription);
    }

    public static AppInternalError noSubscription(String details) {
        return new AppInternalError(AppInternalErrorReason.NoSubscription.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError notFoundCspAccount() {
        return new AppInternalError(AppInternalErrorReason.NotFoundCspAccount);
    }

    public static AppInternalError notFoundCspAccount(String details) {
        return new AppInternalError(AppInternalErrorReason.NotFoundCspAccount.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError noPermission() {
        return new AppInternalError(AppInternalErrorReason.NoPermission);
    }

    public static AppInternalError noPermission(String details) {
        return new AppInternalError(AppInternalErrorReason.NoPermission.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError notExistsTicket() {
        return new AppInternalError(AppInternalErrorReason.NotExistsTicket);
    }

    public static AppInternalError notExistsTicket(String details) {
        return new AppInternalError(AppInternalErrorReason.NotExistsTicket.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError emptyTicketData() {
        return new AppInternalError(AppInternalErrorReason.EmptyTicketData);
    }

    public static AppInternalError emptyTicketData(String details) {
        return new AppInternalError(AppInternalErrorReason.EmptyTicketData.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError invalidCsp() {
        return new AppInternalError(AppInternalErrorReason.InvalidCsp);
    }

    public static AppInternalError invalidCsp(String details) {
        return new AppInternalError(AppInternalErrorReason.InvalidCsp.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError emptyCspTicketId() {
        return new AppInternalError(AppInternalErrorReason.EmptyCspTicketId);
    }

    public static AppInternalError emptyCspTicketId(String details) {
        return new AppInternalError(AppInternalErrorReason.EmptyCspTicketId.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError notBetaTester() {
        return new AppInternalError(AppInternalErrorReason.NotBetaTester);
    }

    public static AppInternalError notBetaTester(String details) {
        return new AppInternalError(AppInternalErrorReason.NotBetaTester.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError notEscalated() {
        return new AppInternalError(AppInternalErrorReason.NotEscalated);
    }

    public static AppInternalError notEscalated(String details) {
        return new AppInternalError(AppInternalErrorReason.NotEscalated.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError invalidCspAccount() {
        return new AppInternalError(AppInternalErrorReason.InvalidCspAccount);
    }

    public static AppInternalError invalidCspAccount(String details) {
        return new AppInternalError(AppInternalErrorReason.InvalidCspAccount.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError outOfSyncTargetTimeRange() {
        return new AppInternalError(AppInternalErrorReason.OutOfSyncTargetTimeRange);
    }

    public static AppInternalError outOfSyncTargetTimeRange(String details) {
        return new AppInternalError(AppInternalErrorReason.OutOfSyncTargetTimeRange.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError missingParameters() {
        return new AppInternalError(AppInternalErrorReason.MissingParameters);
    }

    public static AppInternalError missingParameters(String details) {
        return new AppInternalError(AppInternalErrorReason.MissingParameters.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError missingParametersByFields(String... fields) {
        return new AppInternalError(AppInternalErrorReason.MissingParameters.withDetails(TicketUtil.internalErrorText(missingFieldNames(fields))));
    }

    public static AppInternalError attachmentExceedNumber() {
        return new AppInternalError(AppInternalErrorReason.AttachmentExceedNumber);
    }

    public static AppInternalError attachmentExceedNumber(String details) {
        return new AppInternalError(AppInternalErrorReason.AttachmentExceedNumber.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError attachmentExceedSize() {
        return new AppInternalError(AppInternalErrorReason.AttachmentExceedSize);
    }

    public static AppInternalError attachmentExceedSize(String details) {
        return new AppInternalError(AppInternalErrorReason.AttachmentExceedSize.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError failedToDownloadAttachment() {
        return new AppInternalError(AppInternalErrorReason.FailedToDownloadAttachment);
    }

    public static AppInternalError failedToDownloadAttachment(String details) {
        return new AppInternalError(AppInternalErrorReason.FailedToDownloadAttachment.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError cannotReadTicket() {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicket);
    }

    public static AppInternalError cannotReadTicket(String details) {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicket.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError cannotReadTicket(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.CannotReadTicket.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.CannotReadTicket);
    }

    public static AppInternalError cannotReadTicket(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicket.withDetailsException(TicketUtil.internalErrorText(details), e));
    }

    public static AppInternalError cannotReadTicketConversation() {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketConversation);
    }

    public static AppInternalError cannotReadTicketConversation(String details) {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketConversation.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError cannotReadTicketConversation(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.CannotReadTicketConversation.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketConversation);
    }

    public static AppInternalError cannotReadTicketConversation(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketConversation.withDetailsException(TicketUtil.internalErrorText(details), e));
    }

    public static AppInternalError cannotReadTicketAttachment() {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketAttachment);
    }

    public static AppInternalError cannotReadTicketAttachment(String details) {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketAttachment.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError cannotReadTicketAttachment(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.CannotReadTicketAttachment.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketAttachment);
    }

    public static AppInternalError cannotReadTicketAttachment(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.CannotReadTicketAttachment.withDetailsException(TicketUtil.internalErrorText(details), e));
    }

    public static AppInternalError exceedConversationLimit(int limitSize) {
        String details = String.format("Ticket conversation has reached a limited number(%d).", limitSize);
        return new AppInternalError(AppInternalErrorReason.ExceedConversationLimit.withDetails(details));
    }

    public static AppInternalError conflict() {
        return new AppInternalError(AppInternalErrorReason.Conflict);
    }

    public static AppInternalError conflict(String details) {
        return new AppInternalError(AppInternalErrorReason.Conflict.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError freshdeskApiError() {
        return new AppInternalError(AppInternalErrorReason.FreshdeskApiError);
    }

    public static AppInternalError freshdeskApiError(String details) {
        return new AppInternalError(AppInternalErrorReason.FreshdeskApiError.withDetails(TicketUtil.freshdeskErrorText(details)));
    }

    public static AppInternalError freshdeskApiError(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.FreshdeskApiError.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.FreshdeskApiError);
    }

    public static AppInternalError freshdeskApiError(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.FreshdeskApiError.withDetailsException(TicketUtil.freshdeskErrorText(details), e));
    }

    public static AppInternalError freshdeskApiCallRateLimitExceed() {
        return new AppInternalError(AppInternalErrorReason.FreshdeskApiCallRateLimitExceed);
    }

    public static AppInternalError freshdeskApiCallRateLimitExceed(String details) {
        return new AppInternalError(AppInternalErrorReason.FreshdeskApiCallRateLimitExceed.withDetails(TicketUtil.freshdeskErrorText(details)));
    }

    public static AppInternalError cspApiError() {
        return new AppInternalError(AppInternalErrorReason.CspApiError);
    }

    public static AppInternalError cspApiError(String details) {
        return new AppInternalError(AppInternalErrorReason.CspApiError.withDetails(TicketUtil.cspErrorText(details)));
    }

    public static AppInternalError cspApiError(Exception e) {
        //return new AppInternalError(AppInternalErrorReason.CspApiError, exceptionString(e));
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.CspApiError.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.CspApiError);
    }

    public static AppInternalError cspApiError(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.CspApiError.withDetailsException(TicketUtil.cspErrorText(details), e));
    }

    public static AppInternalError cloudzApiError() {
        return new AppInternalError(AppInternalErrorReason.CloudzApiError);
    }

    public static AppInternalError cloudzApiError(String details) {
        return new AppInternalError(AppInternalErrorReason.CloudzApiError.withDetails(TicketUtil.cloudzErrorText(details)));
    }

    public static AppInternalError cloudzApiError(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.CloudzApiError.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.CloudzApiError);
    }

    public static AppInternalError cloudzApiError(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.CloudzApiError.withDetailsException(TicketUtil.cloudzErrorText(details), e));
    }

    public static AppInternalError opsgenieApiError() {
        return new AppInternalError(AppInternalErrorReason.OpsgenieApiError);
    }

    public static AppInternalError opsgenieApiError(String details) {
        return new AppInternalError(AppInternalErrorReason.OpsgenieApiError.withDetails(TicketUtil.opsgenieErrorText(details)));
    }

    public static AppInternalError opsgenieApiError(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.OpsgenieApiError.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.OpsgenieApiError);
    }

    public static AppInternalError opsgenieApiError(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.OpsgenieApiError.withDetailsException(TicketUtil.opsgenieErrorText(details), e));
    }

    public static AppInternalError slackApiError() {
        return new AppInternalError(AppInternalErrorReason.SlackApiError);
    }

    public static AppInternalError slackApiError(String details) {
        return new AppInternalError(AppInternalErrorReason.SlackApiError.withDetails(TicketUtil.slackErrorText(details)));
    }

    public static AppInternalError slackApiError(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.SlackApiError.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.SlackApiError);
    }

    public static AppInternalError slackApiError(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.SlackApiError.withDetailsException(TicketUtil.slackErrorText(details), e));
    }

    public static AppInternalError partialFailure() {
        return new AppInternalError(AppInternalErrorReason.PartialFailure);
    }

    public static AppInternalError partialFailure(String details) {
        return new AppInternalError(AppInternalErrorReason.PartialFailure.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError internalProcessingError() {
        return new AppInternalError(AppInternalErrorReason.InternalProcessingError);
    }

    public static AppInternalError internalProcessingError(String details) {
        return new AppInternalError(AppInternalErrorReason.InternalProcessingError.withDetails(TicketUtil.internalErrorText(details)));
    }

    public static AppInternalError internalProcessingError(Exception e) {
        if (e != null) {
            return new AppInternalError(AppInternalErrorReason.InternalProcessingError.withException(e));
        }
        return new AppInternalError(AppInternalErrorReason.InternalProcessingError);
    }

    public static AppInternalError internalProcessingError(String details, Exception e) {
        return new AppInternalError(AppInternalErrorReason.InternalProcessingError.withDetailsException(TicketUtil.internalErrorText(details), e));
    }

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
        return "AppInternalError{" +
                "errorReason=" + errorReason +
                ", noteEnabled=" + noteEnabled +
                ", tags=" + tags +
                '}';
    }
}
