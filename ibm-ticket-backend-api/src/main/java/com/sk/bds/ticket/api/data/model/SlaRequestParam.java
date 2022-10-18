package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.Data;

import java.text.SimpleDateFormat;
import java.util.Date;

@Data
public class SlaRequestParam {
    public static final String DATE_FORMAT = "yyyyMMdd";
    //@DateTimeFormat(pattern = DATE_FORMAT)
    //@JsonFormat(pattern=DATE_FORMAT", timezone="Asia/Seoul")
    //@JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss", timezone="Asia/Seoul")
    //LocalDate targetPeriodStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date targetPeriodStart;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date targetPeriodEnd;

    TicketStatus ticketStatus;
    String requesterName;
    String requesterEmail;

    public SlaRequestParam() {
        ticketStatus = TicketStatus.all;
    }

    public boolean isValidRequest() {
        return targetPeriodStart != null & targetPeriodEnd != null && requesterName != null && requesterEmail != null;
    }

    public String periodText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(AppConstants.getLocalTimeZone());
        String start = dateFormat.format(targetPeriodStart);
        String end = dateFormat.format(targetPeriodEnd);
        return start + " ~ " + end;
    }

    public String descriptionForTicket() {
        StringBuilder sb = new StringBuilder();
        sb.append("SLA Report 생성 요청이 접수되었습니다.<br>");
        sb.append("-----<br>");
        sb.append("[SLA Report 생성 정보]<br>");
        sb.append("요청자: " + requesterName + " (" + requesterEmail + ")<br>");
        sb.append("요청 일시: " + TicketUtil.getLocalTimeString(new Date()) + "<br>");
        sb.append("대상 티켓 기간: " + periodText() + "<br>");
        sb.append("대상 티켓 상태: " + ticketStatus.korText() + "<br>");
        sb.append("-----<br><br>");
        sb.append("SLA Report는 원할한 티켓 서비스를 위해 심야 시간(23시~05시)에 작업이 진행되며,<br>티켓 처리량이 많은 경우 SLA Report 작업이 지연될 수 있습니다.<br>SLA Report가 생성되면 본 티켓의 대화에 추가됩니다.<br>");
        return sb.toString();
    }
}
