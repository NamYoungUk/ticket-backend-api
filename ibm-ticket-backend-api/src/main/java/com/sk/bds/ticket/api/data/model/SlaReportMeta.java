package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sk.bds.ticket.api.util.JsonUtil;
import lombok.Data;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Data
public class SlaReportMeta {
    public static final String INPUT_DATE_FORMAT = "yyyyMMdd";
    public static final String OUTPUT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DETAILS_DATE_FORMAT = AppConstants.LOCAL_TIME_FORMAT;

    //Requested parameters
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DETAILS_DATE_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date requestDate; //리포트 생성을 요청한 일시(createSlaReport 요청 시간)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OUTPUT_DATE_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date ticketTimeFrom;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OUTPUT_DATE_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date ticketTimeTo;

    String reportId;
    String status;
    String freshdeskTicketId;

    @JsonIgnore
    public boolean isDone() {
        if (status != null) {
            return SlaReport.ReportStatus.Done.name().equals(status);
        }
        return false;
    }

    @JsonIgnore
    public String getJSONReportName() {
        //"SlaReport-20200825.json"
        SimpleDateFormat sf = new SimpleDateFormat(INPUT_DATE_FORMAT);
        return "SlaReport-" + sf.format(ticketTimeTo) + ".json";
    }

    @JsonIgnore
    public String getExcelReportName() {
        //"SLAReport-20200825.xls"
        SimpleDateFormat sf = new SimpleDateFormat(INPUT_DATE_FORMAT);
        return "SlaReport-" + sf.format(ticketTimeTo) + ".xls";
    }

    @JsonIgnore
    public JSONObject export() {
        String endPoint = AppConfig.getInstance().getServiceEndPoint();
        DateFormat localTimeFormat = new SimpleDateFormat(DETAILS_DATE_FORMAT);
        localTimeFormat.setTimeZone(AppConstants.getLocalTimeZone());
        //SimpleDateFormat reportTimeFormat = new SimpleDateFormat(OUTPUT_DATE_FORMAT);
        //reportTimeFormat.setTimeZone(AppConstants.getLocalTimeZone());
        JSONObject object = new JSONObject();
        object.put("status", getStatus());
        object.put("report-id", getReportId());
        object.put("report-ticket-id", getFreshdeskTicketId());
        object.put("request-time", localTimeFormat.format(getRequestDate()));
        object.put("ticket-time-from", localTimeFormat.format(getTicketTimeFrom()));
        object.put("ticket-time-to", localTimeFormat.format(getTicketTimeTo()));
        object.put("download-json", String.format("%s/sla/reports/%s/%s", endPoint, getReportId(), SlaReport.ReportType.json.name()));
        object.put("download-excel", String.format("%s/sla/reports/%s/%s", endPoint, getReportId(), SlaReport.ReportType.excel.name()));

        if (SlaReport.REPORT_SAMPLE_SAVE) {
            object.put("download-sample", String.format("%s/sla/reports/%s/%s", endPoint, getReportId(), SlaReport.ReportType.sample.name()));
        }
        return object;
    }

    @JsonIgnore
    public String exportText() {
        return JsonUtil.prettyPrint(export());
    }
}
