package com.sk.bds.ticket.api.response;

import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.LogItem;
import com.sk.bds.ticket.api.exception.AppError;
import lombok.Data;

import javax.servlet.http.HttpServletRequest;

@Data
public class AppErrorResponse {

    private String id;
    private String status;
    private int statusCode;
    private String error;
    private String message;

    private AppErrorResponse(String id, AppError appError) {
        this.id = id;
        status = AppConstants.STATUS_FAIL;
        statusCode = appError.getStatusCode();
        this.error = appError.getErrorCause();
        if (appError.getErrorCause() != null && appError.getErrorDetails() != null) {
            this.message = String.format("%s\t%s", appError.getErrorCause(), appError.getErrorDetails());
        } else if (appError.getErrorDetails() != null) {
            this.message = appError.getErrorDetails();
        } else if (appError.getErrorCause() != null) {
            this.message = appError.getErrorCause();
        } else {
            this.message = "";
        }
    }

    public static AppErrorResponse from(HttpServletRequest request, AppError error) {
        String id = null;
        LogItem logItem = (LogItem) request.getAttribute(AppConstants.ATTR_LOG_ITEM);
        if (logItem != null) {
            id = logItem.getId();
            logItem.setException(error);
            logItem.write();
        }
        return new AppErrorResponse(id, error);
    }
}
