package com.sk.bds.ticket.api.exception;

import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.LogItem;
import com.sk.bds.ticket.api.response.AppErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class AppErrorHandler {

    @Value("${exception.debug}")
    boolean exceptionDebugging;

    @ExceptionHandler(Exception.class)
    public AppErrorResponse exceptionHandler(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        if (exceptionDebugging) {
            exception.printStackTrace();
        }
        response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
        LogItem logItem = (LogItem) request.getAttribute(AppConstants.ATTR_LOG_ITEM);
        if (logItem != null) {
            logItem.setException(exception);
        }
        if (exception instanceof AppError) {
            response.setStatus(((AppError) exception).getStatusCode());
            return AppErrorResponse.from(request, (AppError) exception);
        } else if (exception instanceof NoHandlerFoundException) {
            AppError error = new AppError.NotFound();
            response.setStatus(error.getStatusCode());
            return AppErrorResponse.from(request, error);
        } else {
            AppError error = new AppError.InternalServerError(exception);
            response.setStatus(error.getStatusCode());
            return AppErrorResponse.from(request, error);
        }
    }
}
