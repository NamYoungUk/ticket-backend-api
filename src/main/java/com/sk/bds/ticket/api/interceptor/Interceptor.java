package com.sk.bds.ticket.api.interceptor;

import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.LogItem;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.util.AnonymousCallable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;

@Slf4j
@Component
public class Interceptor implements HandlerInterceptor {
    AppConfig config;

    @PostConstruct
    public void init() {
        config = AppConfig.getInstance();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws AppError.Unauthorized {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/swagger") || uri.startsWith("/webjars") || uri.startsWith("/v2") || uri.startsWith("/csrf") || "/".equals(uri)) {
            return true;
        }
        switch (request.getDispatcherType()) {
            case ASYNC:
                return true;       // 비동기 작업 완료 후 dispatch 된 경우, 그냥 흘려보낸다.
            case REQUEST:
                log.info("Request URI:{}, method:{}", uri, request.getMethod());
                //printRequest(request);
                request.setAttribute(AppConstants.ATTR_LOG_ITEM, LogItem.from(request));
                AnonymousCallable anonymousCallable = ((HandlerMethod) handler).getMethodAnnotation(AnonymousCallable.class);
                if (anonymousCallable != null) {
                    return true;
                }
                String authorization = request.getHeader("authorization");
                if (config.getServiceApiAccessKey().equals(authorization)) {
                    return true;
                } else {
                    log.error("Unauthorized request");
                    printRequest(request);
                    throw new AppError.Unauthorized();
                }
            case FORWARD:
            case ERROR:
            case INCLUDE:
            default:
                return false;
        }
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (request.getAttribute(AppConstants.ATTR_LOG_ITEM) != null) {
            ((LogItem) request.getAttribute(AppConstants.ATTR_LOG_ITEM)).write();
        }
    }

    private void printRequest(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        log.debug("Request Headers");
        while (names.hasMoreElements()) {
            String header = names.nextElement();
            log.debug("{} : {}", header, request.getHeader(header));
        }

        names = request.getParameterNames();
        log.debug("Request Parameters");
        while (names.hasMoreElements()) {
            String param = names.nextElement();
            log.debug("{} : {}", param, request.getParameter(param));
        }

        log.debug("Request getMethod:{}", request.getMethod());
        log.debug("Request getContentType:{}", request.getContentType());
        log.debug("Request getContextPath:{}", request.getContextPath());
        log.debug("Request getContentLength:{}", request.getContentLength());
        log.debug("Request getAuthType:{}", request.getAuthType());
        log.debug("Request getQueryString:{}", request.getQueryString());
        log.debug("Request getRequestURL:{}", request.getRequestURL().toString());
        log.debug("Request getRequestURI:{}", request.getRequestURI());
        log.debug("Request getServerName:{}", request.getServerName());
        log.debug("Request getServletPath:{}", request.getServletPath());
        log.debug("Request getScheme:{}", request.getScheme());
        log.debug("Request getRemoteUser:{}", request.getRemoteUser());
        log.debug("Request getRemoteAddr:{}", request.getRemoteAddr());
        log.debug("Request getRemoteHost:{}", request.getRemoteHost());
        log.debug("Request getRemotePort:{}", request.getRemotePort());
    }

}
