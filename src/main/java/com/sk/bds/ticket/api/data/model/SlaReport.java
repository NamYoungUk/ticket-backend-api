package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZUser;
import com.sk.bds.ticket.api.data.model.freshdesk.*;
import com.sk.bds.ticket.api.data.model.ibm.IbmTicketEditorType;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.service.CloudZService;
import com.sk.bds.ticket.api.service.FreshdeskService;
import com.sk.bds.ticket.api.util.FreshdeskTicketLoader;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.ticket.Update;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.ss.util.RegionUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SlaReport {
    AppConfig config;
    Date ticketTimeFrom;
    Date ticketTimeTo;
    List<SampleGroup> sampleGroups;
    SlaReportMeta reportMeta;
    DateFormat fdTimeFormat;
    DateFormat localTimeFormat;

    public SlaReport(SlaReportMeta meta) {
        config = AppConfig.getInstance();
        reportMeta = meta;
        ticketTimeFrom = reportMeta.getTicketTimeFrom();
        ticketTimeTo = reportMeta.getTicketTimeTo();
        log.debug("{} : {}", ticketTimeFrom, ticketTimeTo);
        Util.resetTimeToZero(ticketTimeFrom);
        Util.resetTimeToMax(ticketTimeTo);
        fdTimeFormat = TicketUtil.getFreshdeskDateFormat();
        localTimeFormat = TicketUtil.getLocalDateFormat();
    }

    public enum ReportType {
        meta,
        sample,
        json,
        excel;

        public boolean isMeta() {
            return this == meta;
        }

        public boolean isSample() {
            return this == sample;
        }

        public boolean isJson() {
            return this == json;
        }

        public boolean isExcel() {
            return this == excel;
        }
    }

    public enum ReportStatus {
        Reserved,
        Loading,
        Calculating,
        Done
    }

    public enum PeriodType {
        Week,
        Month,
        Year
    }

    enum CellPosition {
        TopLeft,
        TopCenter,
        TopRight,
        MiddleLeft,
        MiddleCenter,
        MiddleRight,
        BottomLeft,
        BottomCenter,
        BottomRight,
        All
    }

    public enum Tribe {
        Compute,
        Storage,
        Network,
        Security,
        Etc,
        Unknown;

        private String getDisplayLabel() {
            if (this == Compute) {
                return "Compute";
            } else if (this == Storage) {
                return "Storage";
            } else if (this == Network) {
                return "Network";
            } else if (this == Security) {
                return "Security";
            } else if (this == Etc) {
                return "Etc";
            } else if (this == Unknown) {
                return "Unknown";
            }
            return "";
        }
    }

    public enum Severity {
        SEV1,
        SEV2,
        SEV3,
        Unknown;

        final int Sev1ResponseTime = (15 * 60 * 1000); // response time < 15 minutes
        final int Sev2ResponseTime = (60 * 60 * 1000); // response time < 1 hour
        final int Sev3ResponseTime = (120 * 60 * 1000); // response time < 2 hours

        private String getDisplayLabel() {
            if (this == SEV1) {
                return "SEV 1 (< 15 mins)";
            } else if (this == SEV2) {
                return "SEV 2 (< 1 hour)";
            } else if (this == SEV3) {
                return "SEV 3 (< 2 hours)";
            } else if (this == Unknown) {
                return "Unknown";
            }
            return "";
        }

        private long getRequiredResponseTime() {
            if (this == SEV1) {
                return Sev1ResponseTime;
            } else if (this == SEV2) {
                return Sev2ResponseTime;
            } else if (this == SEV3) {
                return Sev3ResponseTime;
            }
            return 0;
        }
    }

    enum StatsType {
        CreationCount,
        EscalationCount,
        ElapsedL1ResponseTimeAverage,
        ElapsedCspResponseTimeAverage;

        private String getDisplayLabel() {
            if (this == CreationCount) {
                return "신규 생성 티켓 수";
            } else if (this == EscalationCount) {
                return "Escalation 티켓 수";
            } else if (this == ElapsedL1ResponseTimeAverage) {
                return "SK L1 평균 응답";
            } else if (this == ElapsedCspResponseTimeAverage) {
                return "IBM 평균 응답 ";
            }
            return "";
        }
    }

    @Data
    private class TicketStats {
        Map<Long, Sample> samples;
        int ticketCount;
        int l1SolvedCount;
        int escalationCount; //IBM 직원에게 Escalation된 티켓 수(IBM 직원이 응답한 티켓)
        long l1ResponseTimeSum;
        int l1ResponseCount;
        long cspResponseTimeSum;
        int cspResponseCount;
        long elapsedL1ResponseTimeAverage;
        long elapsedCspResponseTimeAverage;

        public TicketStats() {
            elapsedL1ResponseTimeAverage = 0;
            elapsedCspResponseTimeAverage = 0;
            l1SolvedCount = 0;
            escalationCount = 0;
            l1ResponseTimeSum = 0;
            l1ResponseCount = 0;
            cspResponseTimeSum = 0;
            cspResponseCount = 0;

            samples = new ConcurrentHashMap<>();
        }

        private void addSample(Sample sample) {
            samples.put(sample.getFdTicketId(), sample);
        }

        private int getTicketCount() {
            return samples.size();
        }

        private void calculate() {
            l1SolvedCount = 0;
            escalationCount = 0;
            l1ResponseTimeSum = 0;
            l1ResponseCount = 0;
            cspResponseTimeSum = 0;
            cspResponseCount = 0;
            ticketCount = samples.size();

            for (Long ticketId : samples.keySet()) {
                Sample sample = samples.get(ticketId);
                if (!sample.isL1Responded()) {
                    //아직 agent 응답이 없다면? 통계 계산에서 제외.
                    continue;
                }

                // L1 응답시간 확인
                l1ResponseTimeSum += sample.getElapsedL1ResponseTime();
                l1ResponseCount++;

                // L1이 해결한 티켓 여부 확인.
                // L1 답변이 달렸지만 아직 해결이 안된 상태이고 IBM 직원의 답변이 없는 티켓
                // L1 자체 해결 티켓 : 아니오.
                // Escalation 여부 : 예. Escalation 됨.
                if (sample.isSolvedL1Level()) {
                    l1SolvedCount++;
                } else {
                    escalationCount++;
                }

                // CSP(IBM)응답 시간 확인
                if (sample.isEscalated() && sample.isCspResponded()) {
                    cspResponseCount++;
                    cspResponseTimeSum += sample.getElapsedCspResponseTime();
                }
            }

            if (l1ResponseCount > 0) {
                elapsedL1ResponseTimeAverage = (l1ResponseTimeSum / l1ResponseCount);
            } else {
                elapsedL1ResponseTimeAverage = 0;
            }

            if (cspResponseCount > 0) {
                elapsedCspResponseTimeAverage = (cspResponseTimeSum / cspResponseCount);
            } else {
                elapsedCspResponseTimeAverage = 0;
            }
        }

        private JSONObject buildReportObject() {
            JSONObject reportObject = new JSONObject();
            reportObject.put("ticketCount", ticketCount);
            reportObject.put("l1SolvedCount", l1SolvedCount);
            reportObject.put("escalationCount", escalationCount);
            reportObject.put("l1ResponseTimeSum", l1ResponseTimeSum);
            reportObject.put("l1ResponseCount", l1ResponseCount);
            reportObject.put("cspResponseTimeSum", cspResponseTimeSum);
            reportObject.put("cspResponseCount", cspResponseCount);
            reportObject.put("l1ResponseTimeAverage", elapsedL1ResponseTimeAverage);
            reportObject.put("cspResponseTimeAverage", elapsedCspResponseTimeAverage);
            return reportObject;
        }
    }

    @Data
    private class Sample {
        long fdTicketId;
        long cspTicketId;
        long createdTime;
        long l1ResponseTime;
        long escalationTime;
        long cspResponseTime;
        String cspTicketDisplayId;
        String customerId;
        String customerName;
        String customerEmail;
        String title;
        //String description;
        Severity severity;
        Tribe tribe;
        int status;

        private Sample() {
            severity = Severity.Unknown;
            tribe = Tribe.Unknown;
            fdTicketId = 0;
            cspTicketId = 0;
            customerId = null;
            customerName = null;
            customerEmail = null;
            cspTicketDisplayId = null;
            title = null;
            //description = null;
            createdTime = 0;
            l1ResponseTime = 0;
            escalationTime = 0;
            cspResponseTime = 0;
            status = FreshdeskTicketStatus.Open;
        }

        public boolean isSolved() {
            return FreshdeskTicketStatus.isClosed(status);
        }

        public boolean isSolvedL1Level() {
            //Employee 응답 시간 없이 종료된 티켓은 L1이 해결한 티켓이다.
            return isSolved() && (getElapsedL1ResponseTime() > 0) && (cspResponseTime == 0);
        }

        public boolean isL1Responded() {
            return (getElapsedL1ResponseTime() > 0);
        }

        public long getElapsedL1ResponseTime() {
            if (createdTime > 0 && l1ResponseTime > 0) {
                long elapsedTime = (l1ResponseTime - createdTime);
                if (elapsedTime < 0) {
                    log.debug("fdTicketId:{} Invalid l1ResponseTime - createdTime:{}, l1ResponseTime:{}", fdTicketId, createdTime, l1ResponseTime);
                    elapsedTime = 0;
                }
                return elapsedTime;
            }
            return 0;
        }

        public boolean isEscalated() {
            return isL1Responded();
        }

        public long getElapsedEscalationTime() {
            if (l1ResponseTime > 0 && escalationTime > 0) {
                long elapsedTime = (escalationTime - l1ResponseTime);
                if (elapsedTime < 0) {
                    elapsedTime = 0;
                }
                return elapsedTime;
            }
            return 0;
        }

        public boolean isCspResponded() {
            return (getElapsedCspResponseTime() > 0);
        }

        public long getElapsedCspResponseTime() {
            if (escalationTime > 0 && cspResponseTime > 0) {
                long elapsedTime = (cspResponseTime - escalationTime);
                if (elapsedTime < 0) {
                    log.debug("fdTicketId:{} Invalid cspResponseTime - escalationTime:{}, cspResponseTime:{}", fdTicketId, escalationTime, cspResponseTime);
                    elapsedTime = 0;
                }
                return elapsedTime;
            }
            return 0;
        }

        private JSONObject buildTimeObject() {
            JSONObject out = new JSONObject();
            out.put("fdTicketId", fdTicketId);
            out.put("createdTime", createdTime);
            out.put("l1ResponseTime", l1ResponseTime);
            out.put("escalationTime", escalationTime);
            out.put("cspResponseTime", cspResponseTime);
            return out;
        }
    }

    @Data
    private class SampleGroup {
        PeriodType periodType;
        long startTime;
        long endTime;
        int ticketCount;
        Map<Long, Sample> samples;
        TicketStats groupStats;
        Map<Tribe, TicketStats> tribeStats;
        Map<Severity, TicketStats> severityStats;
        Map<Severity, List<Sample>> sloSamples;

        private SampleGroup(long startTime, long endTime, PeriodType periodType) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.periodType = periodType;
            groupStats = new TicketStats();
            samples = new ConcurrentHashMap<>();
            tribeStats = new ConcurrentHashMap<>();
            severityStats = new ConcurrentHashMap<>();
            sloSamples = new ConcurrentHashMap<>();
            for (Tribe tribe : Tribe.values()) {
                tribeStats.put(tribe, new TicketStats());
            }
            for (Severity severity : Severity.values()) {
                severityStats.put(severity, new TicketStats());
            }
        }

        private int getYear() {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(startTime);
            return cal.get(Calendar.YEAR);
        }

        private boolean isMonthType() {
            return (this.periodType == PeriodType.Month);
        }

        private boolean isWithinRange(long createdTime) {
            return (createdTime >= startTime) && (createdTime <= endTime);
        }

        private void addSample(Sample sample) {
            samples.put(sample.getFdTicketId(), sample);
        }

        private List<Sample> getSloSamples(Severity severity) {
            return sloSamples.get(severity);
        }

        private void calculateStats() {
            //그룹 통계용 샘플
            ticketCount = samples.size();
            groupStats.setSamples(samples);
            groupStats.calculate();

            //데이터 샘플 분류
            for (long ticketId : samples.keySet()) {
                Sample sample = samples.get(ticketId);
                TicketStats stats;
                stats = tribeStats.get(sample.getTribe());
                stats.addSample(sample);
                stats = severityStats.get(sample.getSeverity());
                stats.addSample(sample);
            }

            //각 도메인별 통계 연산
            for (Tribe tribe : tribeStats.keySet()) {
                tribeStats.get(tribe).calculate();
            }

            //각 심각도별 통계 연산
            for (Severity severity : severityStats.keySet()) {
                severityStats.get(severity).calculate();
            }

            //SLO 티켓
            for (Severity severity : severityStats.keySet()) {
                Map<Long, Sample> samples = severityStats.get(severity).getSamples();
                long requiredResponseTime = severity.getRequiredResponseTime();
                List<Sample> sampleTickets = new ArrayList<>();
                int marginTime = 60000; //1분이상 초과된 티켓만.
                for (Long ticketId : samples.keySet()) {
                    Sample ticket = samples.get(ticketId);
                    if (ticket.getElapsedCspResponseTime() > requiredResponseTime + marginTime) {
                        String customerName = getCustomerName(ticket.getCustomerEmail(), ticket.getCustomerId());
                        if (customerName != null) {
                            ticket.setCustomerName(customerName);
                        } else {
                            ticket.setCustomerName(ticket.getCustomerId());
                        }
                        sampleTickets.add(ticket);
                    }
                }
                sloSamples.put(severity, sampleTickets);
            }
        }

        private int getTicketCount() {
            return samples.size();
        }

        private int getTicketCount(Tribe tribe) {
            TicketStats stats = tribeStats.get(tribe);
            if (stats != null) {
                return stats.getTicketCount();
            }
            return 0;
        }

        private int getTicketCount(Severity severity) {
            TicketStats stats = severityStats.get(severity);
            if (stats != null) {
                return stats.getTicketCount();
            }
            return 0;
        }

        private int getL1SolvedTicketCount() {
            /*int count = 0;
            for (long ticketId : samples.keySet()) {
                Sample sample = samples.get(ticketId);
                if (sample.isSolvedL1Level()) {
                    count++;
                }
            }
            return count;*/
            return groupStats.getL1SolvedCount();
        }

        private int getEscalatedTicketCount() {
            return groupStats.getEscalationCount();
        }

        private int getEscalatedTicketCount(Tribe tribe) {
            TicketStats stats = tribeStats.get(tribe);
            if (stats != null) {
                return stats.getEscalationCount();
            }
            return 0;
        }

        private int getEscalatedTicketCount(Severity severity) {
            TicketStats stats = severityStats.get(severity);
            if (stats != null) {
                return stats.getEscalationCount();
            }
            return 0;
        }

        private long getElapsedL1ResponseTimeAverage() {
            /*int count = 0;
            long elapsedTimeSum = 0;
            for (long ticketId : samples.keySet()) {
                Sample sample = samples.get(ticketId);
                long elapsedTime = sample.getElapsedL1ResponseTime();
                if (elapsedTime > 0) {
                    count++;
                    elapsedTimeSum += elapsedTime;
                }
            }
            if (count > 0) {
                return elapsedTimeSum / count;
            }
            return 0;*/
            return groupStats.getElapsedL1ResponseTimeAverage();
        }

        private long getElapsedL1ResponseTimeAverage(Tribe tribe) {
            TicketStats stats = tribeStats.get(tribe);
            if (stats != null) {
                return stats.getElapsedL1ResponseTimeAverage();
            }
            return 0;
        }

        private long getElapsedL1ResponseTimeAverage(Severity severity) {
            TicketStats stats = severityStats.get(severity);
            if (stats != null) {
                return stats.getElapsedL1ResponseTimeAverage();
            }
            return 0;
        }

        private long getElapsedCspResponseTimeAverage() {
            return groupStats.getElapsedCspResponseTimeAverage();
        }

        private long getElapsedCspResponseTimeAverage(Tribe tribe) {
            TicketStats stats = tribeStats.get(tribe);
            if (stats != null) {
                return stats.getElapsedCspResponseTimeAverage();
            }
            return 0;
        }

        private long getElapsedCspResponseTimeAverage(Severity severity) {
            TicketStats stats = severityStats.get(severity);
            if (stats != null) {
                return stats.getElapsedCspResponseTimeAverage();
            }
            return 0;
        }

        private String yearLabel() {
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy년");
            return dateFormatter.format(new Date(getStartTime()));
        }

        private String periodLabel() {
            String label;
            if (isMonthType()) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("MM월");
                label = dateFormatter.format(new Date(getStartTime()));
            } else {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("MM/dd");
                String from = dateFormatter.format(new Date(getStartTime()));
                String to = dateFormatter.format(new Date(getEndTime()));
                label = from + "~" + to;
            }
            return label;
        }

        private String getDatePeriodString() {
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd"); //"yyyy-MM-dd HH:mm:ss.SSS"
            String start = timeFormat.format(new Date(getStartTime()));
            String end = timeFormat.format(new Date(getEndTime()));
            return start + " ~ " + end;
        }

        private JSONObject buildReportObject() {
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
            JSONObject reportObject = new JSONObject();
            reportObject.put("periodType", periodType.name());
            reportObject.put("startTime", timeFormat.format(new Date(getStartTime())));
            reportObject.put("endTime", timeFormat.format(new Date(getEndTime())));
            reportObject.put("ticketCount", ticketCount);
            reportObject.put("groupStats", groupStats.buildReportObject());

            JSONObject tribeStatsReport = new JSONObject();
            for (Tribe tribe : tribeStats.keySet()) {
                JSONObject tribeReport = tribeStats.get(tribe).buildReportObject();
                tribeStatsReport.put(tribe.name(), tribeReport);
            }
            reportObject.put("tribeStats", tribeStatsReport);

            JSONObject severityStatsReport = new JSONObject();
            for (Severity severity : severityStats.keySet()) {
                JSONObject severityReport = severityStats.get(severity).buildReportObject();
                severityStatsReport.put(severity.name(), severityReport);
            }
            reportObject.put("severityStats", severityStatsReport);

            if (sloSamples.size() > 0) {
                try {
                    String jsonText = JsonUtil.marshal(sloSamples);
                    JSONObject sloTicketMap = new JSONObject(jsonText);
                    reportObject.put("sloTickets", sloTicketMap);
                } catch (JsonProcessingException e) {
                    log.error("Failed to marshalling sloSamples");
                }
            } else {
                reportObject.put("sloTickets", new JSONObject());
            }
            return reportObject;
        }
    }

    public void generateSlaReport() {
        log.info("SLA report generation started. {}", reportMeta);
        createSampleGroups();
        loadFreshdeskTicketSamples();
        calculateStats();
        writeResult();
        log.info("SLA report generation finished. {}", reportMeta);
    }

    private void updateSlaReportMetaStatus() throws IOException {
        String reportMetaFile = getMetaFilePath(reportMeta.getReportId());
        String jsonText = Util.readFile(reportMetaFile);
        SlaReportMeta meta = JsonUtil.unmarshal(jsonText, SlaReportMeta.class);
        meta.setStatus(reportMeta.getStatus());
        try {
            String metaContent = JsonUtil.marshal(meta);
            Util.writeFile(reportMetaFile, metaContent);
        } catch (IOException e) {
            log.error("failed. {}", e.getMessage());
        }
    }

    private void createSampleGroups() {
        log.debug("{} ~ {}", ticketTimeFrom, ticketTimeTo);
        sampleGroups = new LinkedList<>();
        int monthDifference = Util.getMonthsDifference(ticketTimeFrom, ticketTimeTo);
        int monthGroupCount = monthDifference - 1;

        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);

        ///////////////
        //Create Month Sample Group
        for (int diff = 0; diff < monthGroupCount; diff++) {
            cal.setTimeInMillis(ticketTimeFrom.getTime());
            cal.add(Calendar.MONTH, diff);
            if (diff > 0) {
                cal.set(Calendar.DAY_OF_MONTH, 1); //1일로 변경.
            }
            //Reset Time to 00:00:00 000
            Util.resetTimeToZero(cal);
            long start = cal.getTimeInMillis();
            cal.set(Calendar.DAY_OF_MONTH, 1); //1일로 변경.
            cal.add(Calendar.MONTH, 1); //다음달 1일 00:00:00 000
            cal.add(Calendar.MILLISECOND, -1); //전달 마지막일 23:59:59 999
            long end = cal.getTimeInMillis();
            SampleGroup sampleGroup = new SampleGroup(start, end, PeriodType.Month);
            sampleGroups.add(sampleGroup);
        }

        ///////////////
        //Create Week Sample Group
        if (monthDifference > 1) { //마지막 달의 1일로 변경.
            cal.setTimeInMillis(ticketTimeTo.getTime());
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else { //요청 받은 기간의 시작일로 설정.
            cal.setTimeInMillis(ticketTimeFrom.getTime());
        }
        Util.resetTimeToZero(cal);

        int firstDayOfWeek = cal.getFirstDayOfWeek();
        while (cal.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) { //Calendar.MONDAY
            cal.add(Calendar.DATE, -1);
        }
        while (cal.getTimeInMillis() < ticketTimeTo.getTime()) {
            long start = cal.getTimeInMillis();
            cal.add(Calendar.DATE, 7); //일주일 뒤
            cal.add(Calendar.MILLISECOND, -1); //마지막일 23:59:59 999
            long end = cal.getTimeInMillis();
            if (end > ticketTimeTo.getTime()) {
                end = ticketTimeTo.getTime();
            }
            SampleGroup sampleGroup = new SampleGroup(start, end, PeriodType.Week);
            sampleGroups.add(sampleGroup);
            cal.add(Calendar.MILLISECOND, 1); //00:00:00 000
        }

        //생성된 리포트 샘플 기간 확인.
        for (SampleGroup group : sampleGroups) {
            log.debug("Sample group - {} : {}", group.getPeriodType().name(), group.getDatePeriodString());
        }
    }

    private void loadFreshdeskTicketSamples() {
        log.debug("{}", localTimeFormat.format(new Date()));
        reportMeta.setStatus(ReportStatus.Loading.name());
        writeReportMeta(reportMeta);

        for (SampleGroup group : sampleGroups) {
            loadFreshdeskTicketSamples(group);
        }
    }

    private void loadFreshdeskTicketSamples(SampleGroup group) {
        final long startTime = group.getStartTime();
        final long endTime = group.getEndTime();
        int size;
        int totalTicketCount = 0;

        log.debug("group:{}", group.getDatePeriodString());
        FreshdeskTicketLoader loader = FreshdeskTicketLoader.byPeriod(AppConstants.CSP_NAME, new Date(startTime), new Date(endTime), TicketStatus.all);
        while (loader.hasNext()) {
            JSONArray ticketArray = loader.next();
            if (ticketArray != null && ticketArray.length() > 0) {
                size = ticketArray.length();
                totalTicketCount += size;
                for (int i = 0; i < size; i++) {
                    JSONObject ticket = ticketArray.getJSONObject(i);
                    if (TicketUtil.isCreatedByUser(ticket)) {
                        if (!isFilledSlaInformation(ticket)) {
                            log.debug("trying to check SLA Information.");
                            checkSlaInformation(ticket);
                        }
                        Sample sample = buildSample(ticket);
                        if (sample != null) {
                            group.addSample(sample);
                        }
                    }
                }
            }
        }
        log.debug("group:{} - total tickets : {}", group.getDatePeriodString(), totalTicketCount);
    }

    private String getCustomerName(String email, String accountId) {
        CloudZService cloudZService = new CloudZService();
        try {
            JSONArray infoArray = cloudZService.getUserApiInfoListByEmail(email);
            if (infoArray != null) {
                for (int i = 0; i < infoArray.length(); i++) {
                    JSONObject item = infoArray.getJSONObject(i);
                    if (item.has(CloudZService.KeySlApiInfo)) {
                        if (accountId.equals(item.getJSONObject(CloudZService.KeySlApiInfo).optString(CloudZService.KeyApiId))) {
                            return item.getString(CloudZService.KeyCustomerName);
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            log.error("CloudZ getUser failed. {}", e);
        }
        return null;
    }

    private boolean isEscalationCheckEnabled() {
        return config.isEscalationCheckEnabled();
    }

    private boolean isValidEscalationField(String escalation) {
        if (isEscalationCheckEnabled()) {
            return "Y".equals(escalation);
        }
        return true;
    }

    private boolean isValidCustomField(JSONObject ticketObj) {
        if (ticketObj != null) {
            JSONObject customData = ticketObj.getJSONObject(FreshdeskTicketField.CustomFields);
            String escalation = customData.optString(FreshdeskTicketField.CfEscalation);
            String cspAccount = customData.optString(FreshdeskTicketField.CfCspAccount);
            if (!isValidEscalationField(escalation)) {
                log.error("isValidCustomField() - invalid escalation field. ticket id:{}, escalation:{}", ticketObj.optString(FreshdeskTicketField.Id), escalation);
                return false;
            }
            FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(cspAccount);
            return accountField.isValid();
        }
        return false;
    }

    private TicketMetadata buildTicketMetadata(JSONObject ticketData, boolean cspCaseIdRequired) {
        if (ticketData == null) {
            log.error("=== invalid ticket data.");
            return null;
        }
        if (!isValidCustomField(ticketData)) {
            log.error("=== invalid custom field. freshdesk id:{}", ticketData.optString(FreshdeskTicketField.Id));
            return null;
        }
        DateFormat localTimeFormat = TicketUtil.getLocalDateFormat();
        TicketMetadata ticket = new TicketMetadata();
        JSONObject customData = ticketData.optJSONObject(FreshdeskTicketField.CustomFields);
        String descriptionHtml = ticketData.optString(FreshdeskTicketField.DescriptionHtml);
        boolean cspTagged = TicketUtil.isTaggedCsp(descriptionHtml);
        boolean isUserTicket = TicketUtil.isCreatedByUser(ticketData);
        int ticketStatus = ticketData.optInt(FreshdeskTicketField.Status);
        //String brandId = getIdFromBodyTag(descriptionHtml, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
        ticket.setFreshdeskTicketId(ticketData.optString(FreshdeskTicketField.Id));
        ticket.setFreshdeskTicketStatus(ticketStatus);
        ticket.setCreatedByCsp(cspTagged);
        ticket.setCreatedByUser(isUserTicket);
        //ticket.setBrandId(brandId);
        if (customData != null) {
            ticket.setCspTicketId(customData.optString(FreshdeskTicketField.CfCspCaseId));
            FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(customData.optString(FreshdeskTicketField.CfCspAccount));
            if (accountField.isValid()) {
                ticket.setCspAccountEmail(accountField.getEmail());
                ticket.setCspAccountId(accountField.getAccountId());
            }
            ///////Optional for SLA Report
            if (customData.has(FreshdeskTicketField.Priority)) {
                ticket.setSeverity(customData.optInt(FreshdeskTicketField.Priority, FreshdeskTicketPriority.Low));
            }
            if (customData.has(FreshdeskTicketField.CfTribe)) {
                ticket.setTribe(customData.optString(FreshdeskTicketField.CfTribe));
            }
            if (cspTagged) {
                String freshdeskBodyHtml = ticketData.optString(FreshdeskTicketField.DescriptionHtml);
                String timeString = TicketUtil.getTimeFromBodyTag(freshdeskBodyHtml, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
                if (timeString != null) {
                    try {
                        Date parsedTime = localTimeFormat.parse(timeString);
                        ticket.setCspCreatedTime(parsedTime.getTime());
                    } catch (ParseException e) {
                        Util.ignoreException(e);
                    }
                }
            } else {
                if (ticketData.has(FreshdeskTicketField.CreatedAt)) {
                    try {
                        String timeString = ticketData.optString(FreshdeskTicketField.CreatedAt);
                        DateFormat timeFormat = TicketUtil.getFreshdeskDateFormat();
                        Date parsedTime = timeFormat.parse(timeString);
                        ticket.setFreshdeskCreatedTime(parsedTime.getTime());
                    } catch (ParseException e) {
                        Util.ignoreException(e);
                    }
                }
            }
            if (customData.has(FreshdeskTicketField.CfL1ResponseTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfL1ResponseTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    ticket.setL1ResponseTime(parsedTime.getTime());
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }

            if (customData.has(FreshdeskTicketField.CfEscalationTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfEscalationTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    ticket.setEscalationTime(parsedTime.getTime());
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }
            if (customData.has(FreshdeskTicketField.CfCspResponseTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfCspResponseTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    ticket.setCspResponseTime(parsedTime.getTime());
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }
        }
        if (ticket.getFreshdeskTicketId() == null || ticket.getFreshdeskTicketId() == "") {
            log.error(TicketUtil.internalErrorText("Empty freshdesk ticket Id. ibm ticket id:{}"), ticket.getCspTicketId());
            return null;
        }
        if (ticket.getCspAccountId() == null || ticket.getCspAccountEmail() == null) {
            log.error(TicketUtil.internalErrorText("Invalid ibm account. freshdesk id:{}"), ticket.getFreshdeskTicketId());
            return null;
        }
        if (cspCaseIdRequired && (ticket.getCspTicketId() == null || ticket.getCspTicketId() == "")) {
            log.error(TicketUtil.internalErrorText("Empty ibm ticket Id. freshdesk id:{}"), ticket.getFreshdeskTicketId());
            return null;
        }
        return ticket;
    }

    private void checkSlaInformation(JSONObject ticketData) {
        if (ticketData != null) {
            TicketMetadata ticketMetadata = buildTicketMetadata(ticketData, false);
            checkSlaInformation(ticketMetadata);
        }
    }

    private CloudZCspApiInfo getCspApiInfo(String email, String apiId) throws AppError.BadRequest {
        log.debug("email:{}, accountId:{}, account:{}", email, apiId);
        List<CloudZUser> czUsers = CloudZService.getCloudZUserListByEmail(email, true);
        if (czUsers != null) {
            for (CloudZUser user : czUsers) {
                if (user.equalsApiId(apiId)) {
                    return user.getCspApiInfo();
                }
            }
            log.error(TicketUtil.internalErrorText("Not found accessible api info for {}-{}"), email, apiId);
            return null;
        } else {
            log.error(TicketUtil.internalErrorText("Cannot found accessible api info for {}-{}"), email, apiId);
            throw new AppError.BadRequest(TicketUtil.internalErrorText("Unavailable ibm account - email:" + email + ", account:" + apiId));
        }
    }

    private void checkSlaInformation(TicketMetadata ticketMetadata) {
        if (ticketMetadata != null && ticketMetadata.isCreatedByUser() && !ticketMetadata.isSetAllSLATimes()) {
            if (ticketMetadata.getCspTicketId() != null && ticketMetadata.getCspTicketId().length() > 1) {
                try {
                    CloudZCspApiInfo apiInfo = getCspApiInfo(ticketMetadata.getCspAccountEmail(), ticketMetadata.getCspAccountId());
                    if (apiInfo != null) {
                        try {
                            ApiClient ibmClient = apiInfo.buildApiClient();
                            if (ibmClient != null) {
                                try {
                                    Ticket.Service service = Ticket.service(ibmClient, Long.valueOf(ticketMetadata.getCspTicketId()));
                                    List<Update> ibmTicketUpdates = service.getUpdates();
                                    TicketUtil.sortIbmUpdates(ibmTicketUpdates);
                                    checkSlaInformation(ticketMetadata, ibmTicketUpdates);
                                } catch (NumberFormatException e) {
                                    log.error(TicketUtil.internalErrorText("Invalid case id. {}"), e);
                                }
                            } else {
                                log.error("Cannot build ApiClient. ticket freshdeskTicketId:{}, accountId:{}, accessKey:{}", ticketMetadata.getFreshdeskTicketId(), apiInfo.getApiId(), apiInfo.coveredKey());
                            }
                        } catch (com.softlayer.api.ApiException e) {
                            log.error(TicketUtil.cspErrorText("Cannot generate IBM RestApiClient. {}"), e);
                        }
                    } else {
                        log.error(TicketUtil.internalErrorText("Not found accessible api info for ticket : {}"), ticketMetadata.getFreshdeskTicketId());
                    }
                } catch (AppError.BadRequest e) {
                    log.error(TicketUtil.internalErrorText("Invalid account. {} - error: {}"), ticketMetadata.getFreshdeskTicketId(), e);
                }
            }
        }
    }

    private void checkSlaInformation(TicketMetadata ticketMetadata, List<Update> ibmUpdates) {
        log.debug("ticketObject: {}\nibmUpdates size: {}", ticketMetadata, ibmUpdates.size());
        if (ticketMetadata != null && !ticketMetadata.isSetAllSLATimes() && ibmUpdates != null && ibmUpdates.size() > 1) {
            long l1ResponseTime = 0;
            long escalationTime = 0;
            long cspResponseTime = 0;
            boolean l1ResponseTimeFound = ticketMetadata.isSetL1ResponseTime();
            boolean escalationTimeFound = ticketMetadata.isSetEscalationTime();
            boolean cspResponseTimeFound = ticketMetadata.isSetCspResponseTime();
            log.debug("ticketObject initialized l1ResponseTime: {}, escalationTime: {}, cspResponseTime: {}", l1ResponseTime, escalationTime, cspResponseTime);

            if (!FreshdeskService.canApiCall()) {
                log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
                return;
            }
            for (Update update : ibmUpdates) {
                String ibmBodyContent = update.getEntry();
                if (TicketUtil.isTaggedFreshdesk(ibmBodyContent)) {
                    log.debug("freshdesk' note. skipped.", ibmBodyContent);
                    continue;
                }
                if (TicketUtil.isAttachmentNote(update)) {
                    log.debug("attachment note. skipped. {}", update.getEntry());
                    continue;
                }

                String editorType = update.getEditorType();
                if (IbmTicketEditorType.isAgent(editorType)) {
                    if (!l1ResponseTimeFound) {
                        l1ResponseTimeFound = true;
                        l1ResponseTime = update.getCreateDate().getTimeInMillis();
                        log.debug("l1ResponseTimeFound {}", l1ResponseTime);
                    }
                    if (!escalationTimeFound) {
                        escalationTimeFound = true;
                        escalationTime = update.getCreateDate().getTimeInMillis();
                        log.debug("escalationTimeFound {}", escalationTime);
                    }
                } else if (IbmTicketEditorType.isEmployee(editorType)) {
                    if (!cspResponseTimeFound) {
                        cspResponseTimeFound = true;
                        cspResponseTime = update.getCreateDate().getTimeInMillis();
                        log.debug("cspResponseTimeFound {}", cspResponseTime);
                    }
                } else {
                    log.debug("unknown editorType: {}", editorType);
                }
                if (l1ResponseTimeFound && escalationTimeFound && cspResponseTimeFound) {
                    log.debug("all information found");
                    break;
                }
            }
            log.debug("l1ResponseTime: {}, escalationTime: {}, cspResponseTime: {}", l1ResponseTime, escalationTime, cspResponseTime);
            if ((l1ResponseTime > 0) || (escalationTime > 0) || (cspResponseTime > 0)) {
                JSONObject updateData = new JSONObject();
                JSONObject updateCustomData = new JSONObject();
                try {
                    DateFormat localTimeFormat = TicketUtil.getLocalDateFormat();
                    if (l1ResponseTime > 0) {
                        String timeString = localTimeFormat.format(new Date(l1ResponseTime));
                        updateCustomData.put(FreshdeskTicketField.CfL1ResponseTime, timeString);
                    }
                    if (escalationTime > 0) {
                        String timeString = localTimeFormat.format(new Date(escalationTime));
                        updateCustomData.put(FreshdeskTicketField.CfEscalationTime, timeString);
                    }
                    if (cspResponseTime > 0) {
                        String timeString = localTimeFormat.format(new Date(cspResponseTime));
                        updateCustomData.put(FreshdeskTicketField.CfCspResponseTime, timeString);
                    }
                    updateData.put(FreshdeskTicketField.CustomFields, updateCustomData);
                    log.debug("{} - updateTicket {}", ticketMetadata.getFreshdeskTicketId(), updateData.toString());

                    FreshdeskService.updateTicket(ticketMetadata.getFreshdeskTicketId(), updateData);

                    //Freshdesk에 성공적으로 업데이트 되면 TicketMetadata에 업데이트.
                    if (l1ResponseTime > 0) {
                        ticketMetadata.setL1ResponseTime(l1ResponseTime);
                    }
                    if (escalationTime > 0) {
                        ticketMetadata.setEscalationTime(escalationTime);
                    }
                    if (cspResponseTime > 0) {
                        ticketMetadata.setCspResponseTime(cspResponseTime);
                    }
                } catch (AppInternalError e) {
                    log.error("Update SLA information is failed. {} - {}", ticketMetadata.getFreshdeskTicketId(), e.getMessage());
                }
            }
        }
    }

    private Date parseTime(String timeString, DateFormat timeFormatter) {
        if (timeString != null && timeFormatter != null) {
            try {
                return timeFormatter.parse(timeString);
            } catch (ParseException e) {
                Util.ignoreException(e);
            }
        }
        return null;
    }

    private Sample buildSample(JSONObject ticketData) {
        if (ticketData != null) {
            Sample sample = new Sample();
            JSONObject customData = ticketData.optJSONObject(FreshdeskTicketField.CustomFields);
            sample.setFdTicketId(ticketData.optLong(FreshdeskTicketField.Id));
            sample.setCspTicketId(customData.optLong(FreshdeskTicketField.CfCspCaseId));
            FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(customData.optString(FreshdeskTicketField.CfCspAccount));
            if (accountField.isValid()) {
                sample.setCustomerId(accountField.getAccountId());
                sample.setCustomerEmail(accountField.getEmail());
                sample.setCustomerName(accountField.getAccountId());
            }
            sample.setStatus(ticketData.optInt(FreshdeskTicketField.Status, FreshdeskTicketStatus.Open));
            sample.setTitle(ticketData.optString(FreshdeskTicketField.Subject));

            int priority = ticketData.optInt(FreshdeskTicketField.Priority, FreshdeskTicketPriority.Low);
            switch (priority) {
                case FreshdeskTicketPriority.Medium:
                    sample.setSeverity(Severity.SEV2);
                    break;
                case FreshdeskTicketPriority.High:
                case FreshdeskTicketPriority.Urgent:
                    sample.setSeverity(Severity.SEV1);
                    break;
                case FreshdeskTicketPriority.Low:
                default:
                    sample.setSeverity(Severity.SEV3);
                    break;
            }

            if (customData.has(FreshdeskTicketField.CfTribe)) {
                String tribe = customData.optString(FreshdeskTicketField.CfTribe);
                try {
                    Tribe domain = Tribe.valueOf(tribe);
                    sample.setTribe(domain);
                } catch (IllegalArgumentException e) {
                    Util.ignoreException(e);
                }
            }

            String descriptionHtml = ticketData.optString(FreshdeskTicketField.DescriptionHtml);
            boolean ibmTagged = TicketUtil.isTaggedCsp(descriptionHtml);

            if (ibmTagged) {
                String freshdeskBodyHtml = ticketData.optString(FreshdeskTicketField.DescriptionHtml);
                String timeString = TicketUtil.getTimeFromBodyTag(freshdeskBodyHtml, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
                Date parsedTime = parseTime(timeString, localTimeFormat);
                if (parsedTime != null) {
                    sample.setCreatedTime(parsedTime.getTime());
                }
            } else if (ticketData.has(FreshdeskTicketField.CreatedAt)) {
                String timeString = ticketData.optString(FreshdeskTicketField.CreatedAt);
                Date parsedTime = parseTime(timeString, fdTimeFormat);
                if (parsedTime != null) {
                    sample.setCreatedTime(parsedTime.getTime());
                }
            }

            if (customData.has(FreshdeskTicketField.CfL1ResponseTime)) {
                String timeString = customData.optString(FreshdeskTicketField.CfL1ResponseTime);
                Date parsedTime = parseTime(timeString, localTimeFormat);
                if (parsedTime != null) {
                    sample.setL1ResponseTime(parsedTime.getTime());
                }
            }

            if (customData.has(FreshdeskTicketField.CfEscalationTime)) {
                String timeString = customData.optString(FreshdeskTicketField.CfEscalationTime);
                Date parsedTime = parseTime(timeString, localTimeFormat);
                if (parsedTime != null) {
                    sample.setEscalationTime(parsedTime.getTime());
                }
            }

            if (customData.has(FreshdeskTicketField.CfCspResponseTime)) {
                String timeString = customData.optString(FreshdeskTicketField.CfCspResponseTime);
                Date parsedTime = parseTime(timeString, localTimeFormat);
                if (parsedTime != null) {
                    sample.setCspResponseTime(parsedTime.getTime());
                }
            }
            return sample;
        }
        return null;
    }

    private void calculateStats() {
        log.debug("{}", localTimeFormat.format(new Date()));
        reportMeta.setStatus(ReportStatus.Calculating.name());
        writeReportMeta(reportMeta);
        for (SampleGroup group : sampleGroups) {
            group.calculateStats();
        }
        reportMeta.setStatus(ReportStatus.Done.name());
    }

    private void writeResult() {
        log.debug("{}", localTimeFormat.format(new Date()));
        if (reportMeta.isDone()) {
            try {
                if (REPORT_SAMPLE_SAVE) {
                    JSONObject sampleInfo = buildReportSampleInformation();
                    writeSlaReportSampleInformation(reportMeta, sampleInfo);
                }
                JSONObject reportResult = buildJSONResult();
                String jsonFile = writeSlaReportJson(reportMeta, reportResult);
                Workbook excelWorkbook = buildExcelDocument();
                String excelFile = writeSlaReportExcel(reportMeta, excelWorkbook);
                String metaFile = writeReportMeta(reportMeta);
                String bodyText = "SLA Report 생성이 완료되었습니다.<br><br>";
                bodyText += "완료일시: " + localTimeFormat.format(new Date()) + "<br>";

                List<FreshdeskAttachment> attachments = new ArrayList<>();
                if (excelFile != null) {
                    attachments.add(new FreshdeskAttachment(new File(excelFile)));
                    //bodyText += "Report: " + excelFile + "<br>";
                }
                /*
                if (jsonFile != null) {
                    attachments.add(new FreshdeskAttachment(new File(jsonFile)));
                    bodyText += "Report data: " + jsonFile + "<br>";
                }
                if (metaFile != null) {
                    attachments.add(new FreshdeskAttachment(new File(metaFile)));
                    bodyText += "Metadata: " + metaFile + "<br>";
                }
                */
                JSONObject conversationData = new JSONObject();
                conversationData.put(FreshdeskTicketField.ConversationBodyHtml, bodyText);
                FreshdeskService.createReply(reportMeta.getFreshdeskTicketId(), conversationData, attachments);
                FreshdeskService.closeTicketForIbm(reportMeta.getFreshdeskTicketId(), null);
            } catch (IOException | IllegalStateException | AppInternalError e) {
                log.error("failed. {}", e.getMessage());
            }
        }
        log.debug("Complete. - {}", localTimeFormat.format(new Date()));
    }

    private int getTotalTicketCount() {
        int count = 0;
        for (SampleGroup group : sampleGroups) {
            count += group.getTicketCount();
        }
        return count;
    }

    private int getL1SolvedTicketCount() {
        int count = 0;
        for (SampleGroup group : sampleGroups) {
            count += group.getL1SolvedTicketCount();
        }
        return count;
    }

    private int getEscalatedTicketCount() {
        int count = 0;
        for (SampleGroup group : sampleGroups) {
            count += group.getEscalatedTicketCount();
        }
        return count;
    }

    private List<Sample> getSloTickets() {
        List<Sample> samples = new ArrayList<>();
        for (SampleGroup group : sampleGroups) {
            Map<Severity, List<Sample>> sloSamples = group.getSloSamples();
            for (Severity severity : sloSamples.keySet()) {
                samples.addAll(sloSamples.get(severity));
            }
        }
        Collections.sort(samples, new Comparator<Sample>() {
            @Override
            public int compare(Sample o1, Sample o2) {
                return o1.getSeverity().compareTo(o2.getSeverity());
            }
        });
        return samples;
    }

    private List<Sample> getSloTickets(Severity severity) {
        List<Sample> samples = new ArrayList<>();
        for (SampleGroup group : sampleGroups) {
            List<Sample> sloSamples = group.getSloSamples(severity);
            samples.addAll(sloSamples);
        }
        return samples;
    }

    private JSONObject buildReportSampleInformation() {
        JSONObject result = new JSONObject();
        JSONArray groupArray = new JSONArray();
        try {
            result.put("metadata", new JSONObject(reportMeta.exportText()));
            for (SampleGroup group : sampleGroups) {
                JSONObject jsonObject = new JSONObject(JsonUtil.marshal(group));
                groupArray.put(jsonObject);
            }
        } catch (JsonProcessingException e) {
            log.error("failed. {}", e.getMessage());
        }
        result.put("data", groupArray);
        return result;
    }

    private JSONObject buildJSONResult() {
        JSONObject result = new JSONObject();
        JSONArray groupArray = new JSONArray();
        result.put("metadata", new JSONObject(reportMeta.exportText()));
        for (SampleGroup group : sampleGroups) {
            //groupArray.put(new JSONObject(JsonUtil.marshal(group)));
            groupArray.put(group.buildReportObject());
        }
        result.put("data", groupArray);
        return result;
    }

    public static final boolean REPORT_SAMPLE_SAVE = true;

    public static String getSlaReportRootPath() {
        return AppConfig.getAppReportPath();
    }

    public static String getReportPath(String reportId) {
        return Util.pathName(AppConfig.getAppReportPath(), reportId);
    }

    public static final String getMetaFilePath(String reportId) {
        return getReportPath(reportId) + "/" + ReportType.meta.name();
    }

    public static final String getSampleFilePath(String reportId) {
        return getReportPath(reportId) + "/" + ReportType.sample.name();
    }

    public static final String getJsonFilePath(String reportId) throws IOException {
        SlaReportMeta meta = getSlaReportMeta(reportId);
        return getReportPath(reportId) + "/" + meta.getJSONReportName();
    }

    public static final String getExcelFilePath(String reportId) throws IOException {
        SlaReportMeta meta = getSlaReportMeta(reportId);
        return getReportPath(reportId) + "/" + meta.getExcelReportName();
    }

    public static boolean isFilledSlaInformation(JSONObject ticketData) {
        if (ticketData != null) {
            JSONObject customData = ticketData.optJSONObject(FreshdeskTicketField.CustomFields);
            DateFormat localTimeFormat = TicketUtil.getLocalDateFormat();
            int ticketStatus = ticketData.optInt(FreshdeskTicketField.Status);
            long l1ResponseTime = 0;
            long escalationTime = 0;
            long cspResponseTime = 0;
            if (customData.has(FreshdeskTicketField.CfL1ResponseTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfL1ResponseTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    l1ResponseTime = parsedTime.getTime();
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }

            if (customData.has(FreshdeskTicketField.CfEscalationTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfEscalationTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    escalationTime = parsedTime.getTime();
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }

            if (FreshdeskTicketStatus.isClosed(ticketStatus)) {
                return (l1ResponseTime > 0) && (escalationTime > 0);
            } else if ((l1ResponseTime == 0) && (escalationTime == 0)) {
                return false;
            }

            if (customData.has(FreshdeskTicketField.CfCspResponseTime)) {
                try {
                    String timeString = customData.optString(FreshdeskTicketField.CfCspResponseTime);
                    Date parsedTime = localTimeFormat.parse(timeString);
                    cspResponseTime = parsedTime.getTime();
                } catch (ParseException e) {
                    Util.ignoreException(e);
                }
            }
            return (l1ResponseTime > 0) && (escalationTime > 0) && (cspResponseTime > 0);
        }
        return false;
    }

    /**
     * 모든 SLAReport 항목을 조회한다.
     */
    public static List<SlaReportMeta> getAllSLAReports() {
        List<SlaReportMeta> slaReports = new ArrayList<>();
        File reportRootDir = new File(getSlaReportRootPath());
        if (reportRootDir.exists() && reportRootDir.isDirectory()) {
            File[] reportDirs = reportRootDir.listFiles();
            if (reportDirs != null) {
                for (File reportDir : reportDirs) {
                    if (reportDir.isDirectory()) {
                        String reportId = reportDir.getName();
                        //if(Util.isValidUUID(reportId)) {
                        String reportPath = getReportPath(reportId);
                        File reportTargetDir = new File(reportPath);
                        if (reportTargetDir.exists() && reportTargetDir.isDirectory()) {
                            try {
                                SlaReportMeta meta = getSlaReportMeta(reportId);
                                slaReports.add(meta);
                            } catch (IOException e) {
                                Util.ignoreException(e);
                            }
                        }
                        //}
                    }
                }
            }
        }

        return slaReports;
    }

    /**
     * 요청받은 SLA Report 생성 요청 중 미완료된 SLAReport가 있으면 계속 생성을 진행한다.
     */
    public static List<SlaReportMeta> getInCompletedSlaReport() {
        File reportDir = new File(getSlaReportRootPath());
        List<SlaReportMeta> incompleteSlaReports = new ArrayList<>();
        if (reportDir.exists() && reportDir.isDirectory()) {
            File[] reports = reportDir.listFiles();
            for (File report : reports) {
                if (report.isDirectory()) {
                    File metaFile = new File(getMetaFilePath(report.getName()));
                    if (metaFile.exists()) {
                        try {
                            String jsonText = Util.readFile(metaFile.getPath());
                            SlaReportMeta meta = JsonUtil.unmarshal(jsonText, SlaReportMeta.class);
                            log.info("found SLA Report meta {}", meta);
                            if (!meta.isDone()) {
                                incompleteSlaReports.add(meta);
                            }
                        } catch (IOException e) {
                            log.error("Can not read meta file. {}", metaFile.getPath());
                        }
                    } else {
                        log.error("Does not exists meta file. {}", metaFile.getPath());
                    }
                }
            }
        }
        return incompleteSlaReports;
    }

    public static SlaReportMeta buildReportMeta(SlaRequestParam param, String freshdeskTicketId) {
        if (param != null) {
            Calendar cal = Calendar.getInstance();
            Date timeFrom = param.getTargetPeriodStart();
            cal.setTimeInMillis(timeFrom.getTime());
            //Reset Time to maximum.
            Util.resetTimeToZero(cal);
            timeFrom.setTime(cal.getTimeInMillis());
            param.setTargetPeriodStart(timeFrom);

            Date timeTo = param.getTargetPeriodEnd();
            cal.setTimeInMillis(timeTo.getTime());
            //Reset Time to maximum.
            Util.resetTimeToMax(cal);
            timeTo.setTime(cal.getTimeInMillis());
            param.setTargetPeriodEnd(timeTo);

            SlaReportMeta meta = new SlaReportMeta();
            UUID reportId = UUID.randomUUID();
            meta.setReportId(reportId.toString());
            meta.setRequestDate(new Date());
            meta.setTicketTimeFrom(param.getTargetPeriodStart());
            meta.setTicketTimeTo(param.getTargetPeriodEnd());
            meta.setFreshdeskTicketId(freshdeskTicketId);
            meta.setStatus(ReportStatus.Reserved.name());
            return meta;
        }
        return null;
    }

    public static SlaReportMeta writeReportMeta(SlaRequestParam param, String freshdeskTicketId) {
        SlaReportMeta meta = buildReportMeta(param, freshdeskTicketId);
        if (meta != null) {
            writeReportMeta(meta);
        }
        return meta;
    }

    public static String writeReportMeta(SlaReportMeta meta) {
        if (meta != null) {
            String reportPath = getReportPath(meta.getReportId());
            File reportDir = new File(reportPath);
            if (!reportDir.exists()) {
                log.info("create report directory " + reportDir.getPath());
                reportDir.mkdirs();
            }
            try {
                String reportMetaFile = getMetaFilePath(meta.getReportId());
                String metaContent = JsonUtil.marshal(meta);
                Util.writeFile(reportMetaFile, metaContent);
                return reportMetaFile;
            } catch (IOException e) {
                log.error("failed. {}", e.getMessage());
            }
        }
        return null;
    }

    public static SlaReportMeta getSlaReportMeta(String reportId) throws IOException {
        String reportMetaFile = getMetaFilePath(reportId);
        String jsonText = Util.readFile(reportMetaFile);
        SlaReportMeta meta = JsonUtil.unmarshal(jsonText, SlaReportMeta.class);
        return meta;
    }

    public static String writeSlaReportSampleInformation(SlaReportMeta meta, JSONObject sampleInfo) throws IOException {
        if (meta != null && sampleInfo != null) {
            String jsonFile = getSampleFilePath(meta.getReportId());
            Util.writeFile(jsonFile, sampleInfo.toString());
            return jsonFile;
        }
        return null;
    }

    public static String writeSlaReportJson(SlaReportMeta meta, JSONObject reportResult) throws IOException {
        if (meta != null && reportResult != null) {
            String jsonFile = getReportPath(meta.getReportId()) + "/" + meta.getJSONReportName();
            Util.writeFile(jsonFile, reportResult.toString());
            return jsonFile;
        }
        return null;
    }

    public static String writeSlaReportExcel(SlaReportMeta meta, Workbook excelWorkbook) throws IOException {
        if (meta != null && excelWorkbook != null) {
            String excelFile = getReportPath(meta.getReportId()) + "/" + meta.getExcelReportName();
            FileOutputStream outputStream = new FileOutputStream(excelFile);
            excelWorkbook.write(outputStream);
            excelWorkbook.close();
            return excelFile;
        }
        return null;
    }

    //////////////////////////////////
    //////Excel
    public static void exportToExcelFile(File excelFile, Workbook excelWorkbook) throws IOException {
        if (excelFile != null && excelWorkbook != null) {
            FileOutputStream outputStream = new FileOutputStream(excelFile);
            excelWorkbook.write(outputStream);
            excelWorkbook.close();
        }
    }

    public static void exportToExcelFile(OutputStream outputStream, Workbook excelWorkbook) throws IOException {
        if (outputStream != null && excelWorkbook != null) {
            excelWorkbook.write(outputStream);
            excelWorkbook.close();
        }
    }

    public static void exportToExcelDocument(HttpServletResponse response, Workbook excelWorkbook, String fileName) throws IOException {
        if (response != null && excelWorkbook != null) {
            // 컨텐츠 타입과 파일명 지정
            response.setContentType("ms-vnd/excel");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            // 엑셀 출력
            excelWorkbook.write(response.getOutputStream());
            excelWorkbook.close();
        }
    }

    private Row createExcelRow(Sheet sheet, int rowNumber, short rowHeight) {
        Row row = sheet.createRow(rowNumber);
        row.setHeight(rowHeight);
        return row;
    }

    private Workbook buildExcelDocument() {
        Workbook excelWorkbook;
        CellRangeAddress cellMergeRange;
        if (!reportMeta.isDone()) {
            return null;
        }

        final int[] LabelCellWidths = {
                2668, //space
                6700,
                7700
        };
        //final short DefaultColumnWidth = 3000; // 1000 = 열 너비 3.14
        final short DefaultRowHeight = 500; //1000 = 행높이 50
        final int DataCellWidth = 4140;
        log.debug("buildExcelDocument()");

        DateFormat sheetNameFormat = new SimpleDateFormat("MM월dd일");
        String sheetName = sheetNameFormat.format(ticketTimeTo);

        // 워크북 생성
        excelWorkbook = new HSSFWorkbook(); //.xls
        //Workbook wb = new XSSFWorkbook(); //.xlsx
        Sheet sheet = excelWorkbook.createSheet(sheetName);
        //sheet.setDefaultColumnWidth(DataCellWidth);
        sheet.setDefaultRowHeight(DefaultRowHeight);
        //Column 폭 설정.
        for (int i = 0; i < LabelCellWidths.length; i++) {
            //sheet.autoSizeColumn(i);
            int width = LabelCellWidths[i];
            sheet.setColumnWidth(i, width);
        }
        int dataCellCount = Math.max(8, sampleGroups.size()); //cells are required minimum 8 for SLA part.
        for (int i = LabelCellWidths.length; i < LabelCellWidths.length + dataCellCount; i++) {
            sheet.setColumnWidth(i, DataCellWidth);
        }

        Row row;
        Cell cell;
        int rowNumber = 0;
        BorderStyle tableBorder = BorderStyle.MEDIUM;

        Font bold11 = excelWorkbook.createFont();
        bold11.setFontName("맑은 고딕");
        bold11.setFontHeight((short) 220); //11pt
        //headerFont.setColor(IndexedColors.GREEN.getIndex());
        bold11.setBold(true);

        Font regular11 = excelWorkbook.createFont();
        regular11.setFontName("맑은 고딕");
        regular11.setFontHeight((short) 220); //11pt
        //regular11.setColor(IndexedColors.GREEN.getIndex());
        regular11.setBold(false);

        CellStyle boldStyle = excelWorkbook.createCellStyle();
        boldStyle.setFont(bold11);

        CellStyle regularStyle = excelWorkbook.createCellStyle();
        regularStyle.setFont(regular11);

        // 테이블 헤더용 스타일
        CellStyle tableStyle = excelWorkbook.createCellStyle();
        // 가는 경계선을 가집니다.
        tableStyle.setBorderTop(tableBorder);
        tableStyle.setBorderBottom(tableBorder);
        tableStyle.setBorderLeft(tableBorder);
        tableStyle.setBorderRight(tableBorder);
        tableStyle.setFont(bold11);
        tableStyle.setWrapText(true);

        // 배경색은 GREY 입니다.
        //tableStyle.setFillForegroundColor(HSSFColor.HSSFColorPredefined.GREY_40_PERCENT.getIndex());
        //tableStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // 데이터는 가운데 정렬합니다.
        //tableStyle.setAlignment(HorizontalAlignment.CENTER);

        /////////////////////////////////////////////////////////////////
        //· SK L1 Support 티켓 처리 실적 (6/2 ~ 6/8)
        SampleGroup lastGroup = sampleGroups.get(sampleGroups.size() - 1);
        rowNumber++;
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        cell.setCellValue(String.format("o SK L1 Support 티켓 처리 실적 (%s)", lastGroup.periodLabel()));
        cell.setCellStyle(boldStyle);

        //: 총 티켓 24개 (에스컬레이션 된 분석대상 13개 + 에스컬레이션 된 Sales 분류티켓 9개 + L1 자체 처리 티켓 2개) 접수, 13개 지원티켓을 IBM Cloud로 Escalation.
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        cell.setCellValue(String.format(": 총 티켓 %d개 (에스컬레이션 된 분석대상 %d개 + L1 자체 처리 티켓 %d개) 접수, %d개 지원티켓을 IBM Cloud로 Escalation.",
                getTotalTicketCount(),
                (getTotalTicketCount() - getL1SolvedTicketCount()),
                getL1SolvedTicketCount(),
                getEscalatedTicketCount()));
        cell.setCellStyle(regularStyle);

        int prevYear = 0;
        List<Integer> yearGrouping = new ArrayList<>();
        for (int i = 0; i < sampleGroups.size(); i++) {
            int year = sampleGroups.get(i).getYear();
            if (prevYear != year) {
                yearGrouping.add(i);
                prevYear = year;
            }
        }
        /////////////////////////////////////////////////////////////////
        // 기간별 티켓 분석
        StatsType[] skL1StatsTypes = {StatsType.CreationCount, StatsType.EscalationCount, StatsType.ElapsedL1ResponseTimeAverage, StatsType.ElapsedCspResponseTimeAverage};
        rowNumber++;
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        cell.setCellValue("o 기간별 티켓 분석");
        cell.setCellStyle(boldStyle);
        int skL1TableRow = rowNumber;

        //Table 1 - Headers
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        setCellToCenterMiddleText(cell, "구분", tableStyle);
        int columnStart = 3;
        for (SampleGroup group : sampleGroups) {
            String label = group.periodLabel();
            cell = row.createCell(columnStart++);
            setCellToCenterMiddleText(cell, label, tableStyle);
        }
        cellMergeRange = new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, 2); //merge column 1&2
        sheet.addMergedRegion(cellMergeRange);
        setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
        //Table 1 - Data
        for (StatsType type : skL1StatsTypes) {
            String label = type.getDisplayLabel();
            row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
            cell = row.createCell(1);
            setCellToCenterMiddleText(cell, label, tableStyle);
            columnStart = 3;
            for (SampleGroup group : sampleGroups) {
                cell = row.createCell(columnStart++);
                if (type == StatsType.CreationCount) {
                    setCellToCenterMiddleText(cell, countToString(group.getTicketCount()), tableStyle);
                } else if (type == StatsType.EscalationCount) {
                    setCellToCenterMiddleText(cell, countToString(group.getEscalatedTicketCount()), tableStyle);
                } else if (type == StatsType.ElapsedL1ResponseTimeAverage) {
                    setCellToCenterMiddleText(cell, timeToString(group.getElapsedL1ResponseTimeAverage()), tableStyle);
                } else if (type == StatsType.ElapsedCspResponseTimeAverage) {
                    setCellToCenterMiddleText(cell, timeToString(group.getElapsedCspResponseTimeAverage()), tableStyle);
                }
            }

            cellMergeRange = new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, 2); //merge column 1&2
            sheet.addMergedRegion(cellMergeRange);
            setBorderForMergedCell(tableBorder, cellMergeRange, sheet);

        }

        /////////////////////////////////////////////////////////////////
        // 기술도메인별(Tribe) 티켓 분석
        rowNumber++;
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        cell.setCellValue("o 기술도메인별(Tribe) 티켓 분석");
        cell.setCellStyle(boldStyle);
        int techDomainTableRow = rowNumber;

        //Table 2 - 기술도메인별(Tribe) 티켓 분석
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        setCellToCenterMiddleText(cell, "기술 도메인(Tribe)", tableStyle);

        columnStart = 3;
        for (int i = 0; i < yearGrouping.size(); i++) {
            int index = yearGrouping.get(i);
            cell = row.createCell(columnStart + index);
            setCellToCenterMiddleText(cell, sampleGroups.get(index).yearLabel(), tableStyle);
        }
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        columnStart = 3;
        for (SampleGroup group : sampleGroups) {
            String label = group.periodLabel();
            cell = row.createCell(columnStart++);
            setCellToCenterMiddleText(cell, label, tableStyle);
        }

        cellMergeRange = new CellRangeAddress(techDomainTableRow, techDomainTableRow + 1, 1, 2); //merge 2 rows * 2 columns
        sheet.addMergedRegion(cellMergeRange);
        setBorderForMergedCell(tableBorder, cellMergeRange, sheet);

        columnStart = 3;
        for (int i = 0; i < yearGrouping.size(); i++) {
            int index = yearGrouping.get(i);
            int nextIndex;
            if (i < yearGrouping.size() - 1) {
                nextIndex = yearGrouping.get(i + 1);
            } else {
                nextIndex = (sampleGroups.size() - index) - 1;
            }
            if (nextIndex - index > 1) {
                cellMergeRange = new CellRangeAddress(techDomainTableRow, techDomainTableRow, columnStart + index, columnStart + nextIndex);
                sheet.addMergedRegion(cellMergeRange);
                setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
            }
        }

        Tribe[] tribes = {Tribe.Compute, Tribe.Storage, Tribe.Network, Tribe.Security};
        StatsType[] domainStatsTypes = {StatsType.EscalationCount, StatsType.ElapsedL1ResponseTimeAverage, StatsType.ElapsedCspResponseTimeAverage};

        for (Tribe tribe : tribes) {
            row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
            int domainRowNumber = row.getRowNum(); //rowNumber;
            cell = row.createCell(1);
            setCellToCenterMiddleText(cell, tribe.getDisplayLabel(), tableStyle);

            for (StatsType type : domainStatsTypes) {
                if (row == null) {
                    row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
                }

                String label = type.getDisplayLabel();
                cell = row.createCell(2);
                setCellToCenterMiddleText(cell, label, tableStyle);

                columnStart = 3;
                for (SampleGroup group : sampleGroups) {
                    String valueText = "";
                    switch (type) {
                        case CreationCount:
                            valueText = String.format("%d", group.getTicketCount(tribe));
                            break;
                        case EscalationCount:
                            valueText = String.format("%d", group.getEscalatedTicketCount(tribe));
                            break;
                        case ElapsedL1ResponseTimeAverage:
                            valueText = timeToString(group.getElapsedL1ResponseTimeAverage(tribe));
                            break;
                        case ElapsedCspResponseTimeAverage:
                            valueText = timeToString(group.getElapsedCspResponseTimeAverage(tribe));
                            break;
                    }
                    cell = row.createCell(columnStart++);
                    setCellToCenterMiddleText(cell, valueText, tableStyle);
                }
                row = null;
            }
            cellMergeRange = new CellRangeAddress(domainRowNumber, domainRowNumber + (domainStatsTypes.length - 1), 1, 1); //merge 3 rows
            sheet.addMergedRegion(cellMergeRange);
            setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
        }

        /////////////////////////////////////////////////////////////////
        // 심각도(Severity)별 티켓 분석
        rowNumber++;
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        cell.setCellValue("o 심각도(Severity)별 티켓 분석");
        cell.setCellStyle(boldStyle);
        int severityTableRow = rowNumber;

        //Table 3 - 심각도(Severity)별 티켓 분석
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        setCellToCenterMiddleText(cell, "심각도(Severity)", tableStyle);

        columnStart = 3;
        for (int i = 0; i < yearGrouping.size(); i++) {
            int index = yearGrouping.get(i);
            cell = row.createCell(columnStart + index);
            setCellToCenterMiddleText(cell, sampleGroups.get(index).yearLabel(), tableStyle);
        }
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        columnStart = 3;
        for (SampleGroup group : sampleGroups) {
            String label = group.periodLabel();
            cell = row.createCell(columnStart++);
            setCellToCenterMiddleText(cell, label, tableStyle);
        }

        cellMergeRange = new CellRangeAddress(severityTableRow, severityTableRow + 1, 1, 2); //merge 2 rows * 2 columns
        sheet.addMergedRegion(cellMergeRange);
        setBorderForMergedCell(tableBorder, cellMergeRange, sheet);

        columnStart = 3;
        for (int i = 0; i < yearGrouping.size(); i++) {
            int index = yearGrouping.get(i);
            int nextIndex;
            if (i < yearGrouping.size() - 1) {
                nextIndex = yearGrouping.get(i + 1);
            } else {
                nextIndex = (sampleGroups.size() - index) - 1;
            }
            if (nextIndex - index > 1) {
                cellMergeRange = new CellRangeAddress(severityTableRow, severityTableRow, columnStart + index, columnStart + nextIndex);
                sheet.addMergedRegion(cellMergeRange);
                setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
            }
        }

        Severity[] severities = {Severity.SEV1, Severity.SEV2, Severity.SEV3};
        StatsType[] severityStatsTypes = {StatsType.EscalationCount, StatsType.ElapsedL1ResponseTimeAverage, StatsType.ElapsedCspResponseTimeAverage};

        for (Severity severity : severities) {
            row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
            int severityRowNumber = row.getRowNum(); //rowNumber;
            cell = row.createCell(1);
            setCellToCenterMiddleText(cell, severity.getDisplayLabel(), tableStyle);

            for (StatsType type : severityStatsTypes) {
                if (row == null) {
                    row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
                }

                String label = type.getDisplayLabel();
                cell = row.createCell(2);
                setCellToCenterMiddleText(cell, label, tableStyle);

                columnStart = 3;
                for (SampleGroup group : sampleGroups) {
                    String valueText = "";
                    switch (type) {
                        case CreationCount:
                            valueText = String.format("%d", group.getTicketCount(severity));
                            break;
                        case EscalationCount:
                            valueText = String.format("%d", group.getEscalatedTicketCount(severity));
                            break;
                        case ElapsedL1ResponseTimeAverage:
                            valueText = timeToString(group.getElapsedL1ResponseTimeAverage(severity));
                            break;
                        case ElapsedCspResponseTimeAverage:
                            valueText = timeToString(group.getElapsedCspResponseTimeAverage(severity));
                            break;
                    }
                    cell = row.createCell(columnStart++);
                    setCellToCenterMiddleText(cell, valueText, tableStyle);
                }
                row = null;
            }
            cellMergeRange = new CellRangeAddress(severityRowNumber, severityRowNumber + (severityStatsTypes.length - 1), 1, 1); //merge 3 rows
            sheet.addMergedRegion(cellMergeRange);
            setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
        }

        /////////////////////////////////////////////////////////////////
        //SLO 초과 티켓
        rowNumber++;
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        cell.setCellValue("* SLO 초과 티켓");
        cell.setCellStyle(boldStyle);
        int sloTableRow = rowNumber;

        //Table 4 - SLO 초과 티켓
        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
        cell = row.createCell(1);
        setCellToCenterMiddleText(cell, "심각도(Severity)", tableStyle);
        cell = row.createCell(2);
        setCellToCenterMiddleText(cell, "티켓번호", tableStyle);
        cell = row.createCell(3);
        setCellToCenterMiddleText(cell, "고객번호", tableStyle);
        cell = row.createCell(4);
        setCellToCenterMiddleText(cell, "고객명", tableStyle);
        cell = row.createCell(5);
        setCellToCenterMiddleText(cell, "응답소요시간", tableStyle);
        cell = row.createCell(6);
        setCellToCenterMiddleText(cell, "기술도메인", tableStyle);
        cell = row.createCell(7);
        setCellToCenterMiddleText(cell, "고객요청 요약", tableStyle);

        cellMergeRange = new CellRangeAddress(sloTableRow, sloTableRow, 7, 10); //merge 4 columns
        sheet.addMergedRegion(cellMergeRange);
        setBorderForMergedCell(tableBorder, cellMergeRange, sheet);

        Severity[] sloSeverities = {Severity.SEV1, Severity.SEV2, Severity.SEV3};
        int sloCount = 0;
        for (Severity severity : sloSeverities) {
            List<Sample> sloTickets = getSloTickets(severity);
            sloCount += sloTickets.size();
            if (sloTickets.size() > 0) {
                row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
                int severityRowNumber = row.getRowNum();
                cell = row.createCell(1);
                setCellToCenterMiddleText(cell, severity.getDisplayLabel(), tableStyle);
                for (Sample ticket : sloTickets) {
                    if (row == null) {
                        row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
                    }
                    int ticketRowNumber = row.getRowNum();
                    cell = row.createCell(2);
                    setCellToCenterMiddleText(cell, "" + ticket.getFdTicketId(), tableStyle);
                    cell = row.createCell(3);
                    setCellToCenterMiddleText(cell, "" + ticket.getCustomerId(), tableStyle);
                    cell = row.createCell(4);
                    setCellToCenterMiddleText(cell, ticket.getCustomerName(), tableStyle);
                    cell = row.createCell(5);
                    setCellToCenterMiddleText(cell, timeToString(ticket.getElapsedCspResponseTime()), tableStyle);
                    cell = row.createCell(6);
                    setCellToCenterMiddleText(cell, ticket.getTribe().getDisplayLabel(), tableStyle);
                    cell = row.createCell(7);
                    setCellToCenterMiddleText(cell, ticket.getTitle(), tableStyle); //ticket.getTicketDescription() //getCustomerRequestSummary()
                    cellMergeRange = new CellRangeAddress(ticketRowNumber, ticketRowNumber, 7, 10); //merge 4 columns
                    sheet.addMergedRegion(cellMergeRange);
                    setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
                    row = null;
                }
                if (sloTickets.size() > 1) {
                    cellMergeRange = new CellRangeAddress(severityRowNumber, severityRowNumber + (sloTickets.size() - 1), 1, 1); //merge 3 rows
                    sheet.addMergedRegion(cellMergeRange);
                    setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
                }
            }
        }
        if (sloCount == 0) {
            row = createExcelRow(sheet, rowNumber++, DefaultRowHeight);
            for (int i = 1; i < 8; i++) {
                cell = row.createCell(i);
                setCellToCenterMiddleText(cell, "N/A", tableStyle);
            }
            cellMergeRange = new CellRangeAddress(row.getRowNum(), row.getRowNum(), 7, 10); //merge 4 columns
            sheet.addMergedRegion(cellMergeRange);
            setBorderForMergedCell(tableBorder, cellMergeRange, sheet);
        }
        return excelWorkbook;
    }

    private void setBorderForMergedCell(BorderStyle border, CellRangeAddress region, Sheet sheet) {
        // Sets the borders to the merged cell
        RegionUtil.setBorderTop(border, region, sheet);
        RegionUtil.setBorderLeft(border, region, sheet);
        RegionUtil.setBorderRight(border, region, sheet);
        RegionUtil.setBorderBottom(border, region, sheet);
    }

    private void setBorderForTableCell(Cell cell, CellPosition position) {
        BorderStyle left = BorderStyle.NONE;
        BorderStyle right = BorderStyle.NONE;
        BorderStyle top = BorderStyle.NONE;
        BorderStyle bottom = BorderStyle.NONE;
        cell.getCellStyle();
        switch (position) {
            case TopLeft:
                left = top = BorderStyle.MEDIUM;
                right = bottom = BorderStyle.THIN;
                break;
            case TopCenter:
                top = BorderStyle.MEDIUM;
                left = right = bottom = BorderStyle.THIN;
                break;
            case TopRight:
                right = top = BorderStyle.MEDIUM;
                left = bottom = BorderStyle.THIN;
                break;
            case MiddleLeft:
                left = BorderStyle.MEDIUM;
                right = top = bottom = BorderStyle.THIN;
                break;
            case MiddleCenter:
                left = top = right = bottom = BorderStyle.THIN;
                break;
            case MiddleRight:
                right = BorderStyle.MEDIUM;
                left = top = bottom = BorderStyle.THIN;
                break;
            case BottomLeft:
                left = bottom = BorderStyle.MEDIUM;
                right = top = BorderStyle.THIN;
                break;
            case BottomCenter:
                left = right = top = BorderStyle.MEDIUM;
                bottom = BorderStyle.THIN;
                break;
            case BottomRight:
                right = bottom = BorderStyle.MEDIUM;
                left = top = BorderStyle.THIN;
                break;
            case All:
                left = top = right = bottom = BorderStyle.MEDIUM;
                break;
        }
    }

    private void setCellToCenterMiddleText(Cell cell, String text, CellStyle style) {
        cell.setCellStyle(style);
        cell.setCellValue(text);
        setCellToCenterMiddle(cell);
    }

    private void setCellToLeftMiddleText(Cell cell, String text, CellStyle style) {
        cell.setCellStyle(style);
        cell.setCellValue(text);
        setCellToLeftMiddle(cell);
    }

    private void setCellToCenterMiddleText(Cell cell, String text) {
        cell.setCellValue(text);
        setCellToCenterMiddle(cell);
    }

    private void setCellToLeftMiddleText(Cell cell, String text) {
        cell.setCellValue(text);
        setCellToLeftMiddle(cell);
    }

    private void setCellToCenterMiddle(Cell cell) {
        CellUtil.setCellStyleProperty(cell, CellUtil.ALIGNMENT, HorizontalAlignment.CENTER);
        CellUtil.setCellStyleProperty(cell, CellUtil.VERTICAL_ALIGNMENT, VerticalAlignment.CENTER);
    }

    private void setCellToLeftMiddle(Cell cell) {
        CellUtil.setCellStyleProperty(cell, CellUtil.ALIGNMENT, HorizontalAlignment.LEFT);
        CellUtil.setCellStyleProperty(cell, CellUtil.VERTICAL_ALIGNMENT, VerticalAlignment.CENTER);
    }

    private String countToString(int count) {
        return String.valueOf(count);
    }

    private String timeToString(long timeMillis) {
        if (timeMillis == 0) {
            return "N/A";
        }

        long timeSeconds = (timeMillis / 1000);
        long timeMinutes = timeSeconds / 60;
        long timeHours = timeMinutes / 60;
        if (timeHours > 24) {
            int days = (int) (timeHours / 24);
            int hours = (int) (timeHours % 24);
            return String.format("%dd %dhr", days, hours);
        } else if (timeMinutes > 60) {
            int hour = (int) (timeMinutes / 60);
            int min = (int) (timeMinutes % 60);
            return String.format("%dhr %dmin", hour, min);
        } else if (timeMinutes >= 1) {
            return String.format("%dmin", timeMinutes);
        } else {
            return String.format("%dsec", timeSeconds);
        }
    }
}
