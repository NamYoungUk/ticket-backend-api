package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.exception.AppInternalErrorReason;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessResult {
    public enum StatusCode {
        success,
        successPartially,
        canceled,
        aborted,
        rejected,
        failed
    }

    public enum RejectCause {
        none,
        notBetaTester
    }

    StatusCode statusCode;
    List<Object> succeeds;
    List<AppInternalError> errors;
    RejectCause rejectCause = RejectCause.none;

    public ProcessResult() {
        statusCode = StatusCode.success;
        succeeds = new ArrayList<>();
        errors = new ArrayList<>();
    }

    public ProcessResult(ProcessResult another) {
        statusCode = StatusCode.success;
        succeeds = new ArrayList<>();
        errors = new ArrayList<>();
        merge(another);
    }

    public void merge(ProcessResult another) {
        if (another != null) {
            if (another.hasSuccess()) {
                for (Object success : another.getSucceeds()) {
                    addSuccess(success);
                }
                for (AppInternalError error : another.getErrors()) {
                    addError(error);
                }
            }
        }
    }

    public void onCanceled() {
        statusCode = StatusCode.canceled;
    }

    public void onAborted() {
        statusCode = StatusCode.aborted;
    }

    public void onRejected(RejectCause cause) {
        statusCode = StatusCode.rejected;
        rejectCause = cause;
    }

    public boolean isSuccess() {
        return (statusCode == StatusCode.success);
    }

    public boolean isCanceled() {
        return (statusCode == StatusCode.canceled);
    }

    public boolean isAborted() {
        return (statusCode == StatusCode.aborted);
    }

    public boolean isRejected() {
        return (statusCode == StatusCode.rejected);
    }

    public boolean hasSuccess() {
        return (succeeds.size() == 0);
    }

    public boolean hasError() {
        return (errors.size() > 0);
    }

    public boolean hasErrorReason(AppInternalErrorReason reason) {
        if (reason != null && errors.size() > 0) {
            for (AppInternalError error : errors) {
                if (error.equalsReason(reason)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasNoteEnabledError() {
        for (AppInternalError error : errors) {
            if (error.isNoteEnabled()) {
                return true;
            }
        }
        return false;
    }

    public void addSuccess(Object result) {
        if (result != null && !succeeds.contains(result)) {
            succeeds.add(result);

            if (hasError()) {
                statusCode = StatusCode.successPartially;
            } else {
                statusCode = StatusCode.success;
            }
        }
    }

    public void addError(AppInternalError error) {
        if (error != null && !errors.contains(error)) {
            errors.add(error);
            if (hasSuccess()) {
                statusCode = StatusCode.successPartially;
            } else {
                statusCode = StatusCode.failed;
            }
        }
    }

    public void addErrors(List<AppInternalError> errors) {
        for (AppInternalError error : errors) {
            addError(error);
        }
    }

    public String getErrorCauseForErrorNote() {
        if (errors.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (AppInternalError error : errors) {
                if (error.isNoteEnabled()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(error.getErrorReason().output());
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        if (rejectCause != RejectCause.none) {
            return "Rejected by " + rejectCause.name();
        }
        return "Detail cause not specified.";
    }

    public static ProcessResult base() {
        return new ProcessResult();
    }
}
