package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.util.Util;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Data
public class LogItem {
    private static final int ETC_MESSAGE_NUMBER = 10;
    private static final SimpleDateFormat LOG_DATE_FORM = new SimpleDateFormat(AppConstants.LOG_DATETIME_FORMAT);

    private static final String LOG_NULL_MESSAGE = "";

    private String id;
    private Date startAt;
    private Date endAt;
    private String clientIP;
    private static String serverIP;
    private String url;
    private String method;

    private String errMsg = "";
    private Object[] etcMessage;

    private boolean isWrite = false;

    private LogItem(HttpServletRequest request) {
        this.id = UUID.randomUUID().toString();
        this.startAt = new Date();
        this.etcMessage = new Object[ETC_MESSAGE_NUMBER];
        this.clientIP = getRemoteIP(request);
        if (this.serverIP == null) {
            this.serverIP = getServerIP();
        }
        this.url = request.getRequestURL().toString();
        this.method = request.getMethod();
    }

    public static LogItem from(HttpServletRequest request) {
        return new LogItem(request);
    }

    public void setMessage(int index, Object message) {
        if (index < etcMessage.length) {
            etcMessage[index] = message;
        }
    }

    public void setException(Exception e) {
        errMsg = e.getMessage();
        write(e);
    }

    private void write(Exception e) {
        log.error(id, e);
    }

    public synchronized void write() {
        if (!isWrite) {
            isWrite = true;
            log.trace("\n" + makeLine(Arrays.asList(this.etcMessage).stream()
                    .map(a -> Objects.isNull(a) ? LOG_NULL_MESSAGE : a.toString())
                    .collect(Collectors.toList())));
        }
    }

    private String makeLine(List<String> items) {
        this.endAt = new Date();

        List<String> inLine = new ArrayList<>();

        inLine.add(this.id);
        inLine.add(LOG_DATE_FORM.format(this.startAt));
        inLine.add(LOG_DATE_FORM.format(this.endAt));
        inLine.add(String.valueOf(this.endAt.getTime() - this.startAt.getTime()));
        inLine.add(serverIP);
        inLine.add(clientIP);
        inLine.add(url);
        inLine.add(method);
        inLine.add(errMsg);
        inLine.addAll(items);

        return inLine.stream().collect(Collectors.joining(AppConstants.LOG_DELIMITER));
    }

    private String getRemoteIP(HttpServletRequest request) {
        String ip = request.getHeader("X-FORWARDED-FOR");

        //proxy 환경일 경우
        if (ip == null || ip.length() == 0) {
            ip = request.getHeader("Proxy-Client-IP");
        }

        //웹로직 서버일 경우
        if (ip == null || ip.length() == 0) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }

        if (ip == null || ip.length() == 0) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }

    private String getServerIP() {
        String ip = null;
        try {
            InetAddress ia = InetAddress.getLocalHost();
            ip = ia.getHostAddress();
        } catch (UnknownHostException e) {
            Util.ignoreException(e);
        }
        return ip;
    }
}
