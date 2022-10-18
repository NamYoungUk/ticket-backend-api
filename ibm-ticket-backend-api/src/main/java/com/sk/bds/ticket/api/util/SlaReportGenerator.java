package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketBuilder;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketPriority;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketType;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.service.FreshdeskService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class SlaReportGenerator {
    final AppConfig config = AppConfig.getInstance();

    public SlaReportGenerator() {
    }

    private boolean isSlaReportEnabled() {
        return config.isSlaReportEnabled();
    }

    public JSONArray getSlaReports() {
        //ResponseEntity<StreamingResponseBody> streamResponse;
        log.info("");
        JSONArray slaReports = new JSONArray();
        List<SlaReportMeta> reportMetas = SlaReport.getAllSLAReports();
        Collections.sort(reportMetas, new Comparator<SlaReportMeta>() {
            @Override
            public int compare(SlaReportMeta o1, SlaReportMeta o2) {
                if (o1.getTicketTimeFrom() != null && o2.getTicketTimeFrom() != null) {
                    long diff = o1.getTicketTimeFrom().getTime() - o2.getTicketTimeFrom().getTime();
                    if (diff > 0) {
                        return 1;
                    } else if (diff < 0) {
                        return -1;
                    } else {
                        if (o1.getTicketTimeTo() != null && o2.getTicketTimeTo() != null) {
                            diff = o1.getTicketTimeTo().getTime() - o2.getTicketTimeTo().getTime();
                            if (diff > 0) {
                                return 1;
                            } else if (diff < 0) {
                                return -1;
                            }
                        }
                    }
                }
                return 0;
            }
        });
        for (SlaReportMeta meta : reportMetas) {
            log.debug("meta:{}", meta);
            slaReports.put(meta.export());
        }
        return slaReports;
    }

    /**
     * 사용자로 부터 SLA Report 생성 요청을 받아 리포트 생성을 예약한다.
     *
     * @param param
     * @throws AppError
     */
    public String createSlaReport(SlaRequestParam param) throws AppError {
        log.info("request:{}", param);
        if (param == null || param.getTargetPeriodStart() == null || param.getTargetPeriodEnd() == null) {
            throw AppError.badRequest("The date parameter is not specified.");
        }
        /*if ((request.getSlackChannels() == null) && (request.getSlackUsers() == null)) {
            throw AppError.badRequest("There is no information available to receive the report. Please specify to slackChannels or slackUsers.");
        }*/
        String title = "[SLA Report] - " + AppConstants.CSP_NAME + " (대상 티켓 기간: " + param.periodText() + ")";
        String description = param.descriptionForTicket();
        String email = param.getRequesterEmail();
        JSONObject ticketBody = FreshdeskTicketBuilder.FreshdeskTicketParameterBuilder.buildParameter(title, description, email, FreshdeskTicketType.Etc, FreshdeskTicketPriority.Low);
        log.info("ticketBody: {}", ticketBody);
        try {
            JSONObject freshdeskTicketData = FreshdeskService.createTicket(ticketBody, null);
            String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);

            SlaReportMeta meta = SlaReport.writeReportMeta(param, freshdeskTicketId);
            meta.setFreshdeskTicketId(freshdeskTicketId);
            addSLAReportGeneration(meta);
            String metaResponse = meta.exportText();
            log.info("metaResponse:{}", metaResponse);
            return metaResponse;
        } catch (AppInternalError e) {
            log.error("Cannot create Freshdesk ticket for SLA report. {}", e);
            throw AppError.internalError("Cannot create Freshdesk ticket for SLA report. Error: " + e.getMessage());
        }
    }

    public ResponseEntity<StreamingResponseBody> getSlaReport(String reportId, SlaReport.ReportType type) throws AppError {
        //ResponseEntity<StreamingResponseBody> streamResponse;
        log.info("{}/{}", reportId, type);

        if (reportId != null && type != null) {
            String reportPath = SlaReport.getReportPath(reportId);
            File reportTargetDir = new File(reportPath);
            if (reportTargetDir.exists() && reportTargetDir.isDirectory()) {
                SlaReportMeta meta = null;
                final File reportFile;
                try {
                    meta = SlaReport.getSlaReportMeta(reportId);
                } catch (IOException e) {
                    throw new AppError.InternalServerError("Failed to open the report meta. " + e.getMessage());
                }
                //Requested to meta information. Or SLAReport is not completed.
                if (type.isMeta() || !meta.isDone()) {
                    final String metaString = meta.exportText();
                    StreamingResponseBody streamResponseBody = new StreamingResponseBody() {
                        @Override
                        public void writeTo(OutputStream outStream) throws IOException {
                            final InputStream inStream = new ByteArrayInputStream(metaString.getBytes());
                            try {
                                Util.readAndWrite(inStream, outStream);
                            } catch (IOException e) {
                                Util.ignoreException(e);
                            } finally {
                                inStream.close();
                            }
                        }
                    };

                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(streamResponseBody);
                }

                if (type.isJson()) {
                    reportFile = new File(reportPath, meta.getJSONReportName());
                } else if (type.isExcel()) {
                    reportFile = new File(reportPath, meta.getExcelReportName());
                } else if (type.isSample() && SlaReport.REPORT_SAMPLE_SAVE) {
                    reportFile = new File(SlaReport.getSampleFilePath(reportId));
                } else {
                    throw AppError.badRequest(TicketUtil.internalErrorText(" Specified type is not valid. " + type));
                }

                if (reportFile.exists() && reportFile.isFile()) {
                    StreamingResponseBody streamResponseBody = new StreamingResponseBody() {
                        @Override
                        public void writeTo(OutputStream outStream) throws IOException {
                            final InputStream inStream = new FileInputStream(reportFile);
                            try {
                                Util.readAndWrite(inStream, outStream);
                            } catch (IOException e) {
                                Util.ignoreException(e);
                            } finally {
                                inStream.close();
                            }
                        }
                    };

                    //https://en.wikipedia.org/wiki/Media_type
                    if (type.isJson()) {
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Content-Disposition", "attachment;filename=" + reportFile.getName())
                                .body(streamResponseBody);
                    } else {
                        final String ExcelMediaType = "application/vnd.ms-excel";
                        //final String ExcelxMediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                        //final String ExcelMediaType = "ms-vnd/excel";
                        MediaType mediaType = MediaType.parseMediaType(ExcelMediaType);
                        return ResponseEntity.ok()
                                .contentType(mediaType)
                                .header("Content-Disposition", "attachment;filename=" + reportFile.getName())
                                .body(streamResponseBody);
                    }
                }
            }
        }
        throw AppError.badRequest(TicketUtil.internalErrorText(" Report is not exists. " + reportId));
    }

    /**
     * 요청받은 SLA Report 생성 요청 중 미완료된 SLAReport가 있으면 계속 생성을 진행한다.
     */
    public void checkInCompleteSlaReport() {
        log.info("");
        if (isSlaReportEnabled()) {
            List<SlaReportMeta> incompleteList = SlaReport.getInCompletedSlaReport();
            if (incompleteList.size() > 0) {
                incompleteSlaReports.clear();
                for (SlaReportMeta meta : incompleteList) {
                    log.info("incompleted SLA Report. {}", meta);
                    incompleteSlaReports.offer(meta);
                }
            }
        }
    }

    public void addSLAReportGeneration(SlaReportMeta meta) {
        log.info("meta:{}", meta);
        if (meta != null && !meta.isDone()) {
            incompleteSlaReports.offer(meta);
            if (!isSLAReportGeneratorRunning()) {
                startGenerationThread();
            } else {
                synchronized (slaReportGeneratorLock) {
                    log.info("slaReportGeneratorLock release");
                    slaReportGeneratorLock.notifyAll();
                }
            }
        }
    }

    private Thread slaReportGenerator = null;
    private Object slaReportGeneratorLock = new Object();
    private boolean slaReportGeneratorStop = false;
    final Queue<SlaReportMeta> incompleteSlaReports = new ConcurrentLinkedQueue<>();

    private void startGenerationThread() {
        log.info("");
        if (isSlaReportEnabled() && slaReportGenerator == null) {
            log.info("create new thread.");
            checkInCompleteSlaReport();
            slaReportGeneratorStop = false;
            slaReportGenerator = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!slaReportGeneratorStop && isSlaReportEnabled()) {
                        if (isReportingJobRunnableTime()) {
                            if (isFreshdeskApiAvailableForReportJob()) {
                                SlaReportMeta meta = incompleteSlaReports.poll();
                                if (!meta.isDone()) {
                                    SlaReport report = new SlaReport(meta);
                                    report.generateSlaReport();
                                }
                                if (incompleteSlaReports.isEmpty()) {
                                    synchronized (slaReportGeneratorLock) {
                                        try {
                                            log.info("slaReportGenerator waiting...");
                                            slaReportGeneratorLock.wait();
                                        } catch (InterruptedException e) {
                                            log.error("slaReportGenerator waiting error. {}", e);
                                        }
                                    }
                                }
                            } else {
                                try {
                                    long sleepTime = 600000; //10 minutes
                                    log.info("Freshdesk API Call Rate Limit is not available. Sleep {} milli seconds", sleepTime);
                                    Thread.sleep(sleepTime);
                                    log.info("Waked up from sleep.");
                                } catch (InterruptedException e) {
                                    log.error("Thread sleep failed. {}", e);
                                }
                            }
                        } else {
                            try {
                                long sleepTime = getRemainsTimeForReportJobRunning();
                                log.info("Enter sleep for {} milli seconds", sleepTime);
                                Thread.sleep(sleepTime);
                                log.info("Waked up from sleep.");
                            } catch (InterruptedException e) {
                                log.error("Thread sleep failed. {}", e);
                            }
                        }
                    }
                    slaReportGenerator = null;
                }
            });
            slaReportGenerator.start();
        }
    }

    private boolean isReportingJobRunnableTime() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(AppConstants.getLocalTimeZone());
        int nowHour = cal.get(Calendar.HOUR_OF_DAY);
        return (nowHour > 22) || (nowHour < 5); //LocalTime 23~05
    }

    private long getRemainsTimeForReportJobRunning() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(AppConstants.getLocalTimeZone());
        int nowHour = cal.get(Calendar.HOUR_OF_DAY);
        if ((nowHour > 22) || (nowHour < 5)) { //Do now.
            return 1000;
        } else { //6~22
            Util.resetTimeToZero(cal);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            return cal.getTimeInMillis() - System.currentTimeMillis();
        }
    }

    private boolean isFreshdeskApiAvailableForReportJob() {
        //남아 있는 API Call Rate Limit Count
        //동기화 중인 티켓 수
        //리셋까지 남아 있는 시간
        //리셋까지 남아 있는 시간 대비 잔여 API Call Rate Limit Count의 여유 count.
        //	- 한 번 동기화에 필요한 API Call Count 계산 필요: 기본 - 티켓 조회 1, 대화 조회 1, 대화 추가 ?? => 티켓 당 4개 필요.
        //	- 주기적 동기화가 아닌 실제 변경에 따른 동기화를 할 경우 티켓 당 1 시간에 몇 개의 대화가 추가될 수 있는지 예상 카운터를 선정해야함.
        //int total = FreshdeskService.getRateLimitTotalCount();
        int remains = FreshdeskService.getRateLimitRemainingCount();
        int ticketCount = TicketRegistry.getInstance().getMonitoringTicketCount();
        /*
        //long rateLimitResetTime = FreshdeskService.getRateLimitResetTime();
        int requiredCountPerTicketPerHour = 3 * 10;//count per sync time * sync number per hour(6분 주기로 동기화시 10회)
        final int countMargin = 500; //최소 500개 이상의 API Call이 가능한 경우 리포트 작업 실행.

        //long remainsTimeUntilReset = (rateLimitResetTime + 3600000L) - System.currentTimeMillis();//다음 Rate Limit Reset까지 남은 시간(ms)
        //int remainsMinutesUntilReset = (int) ((remainsTimeUntilReset % 3600000L) / 60000); //Rate Limit Reset까지 남은 시간(분)
        //int syncNumber = (remainsMinutesUntilReset / (int) (config.getTicketSyncInterval() / 1000)) + 1; //마지막으로 동기화한 시간까지 체크해야함...
        //requiredCountPerTicketPerHour = 3 * syncNumber;
        return (remains - (requiredCountPerTicketPerHour * ticketCount)) > countMargin;
        */
        //TODO. Temporary.. 티켓 150개 미만, 남은 count가 2000 이상일 때
        return (ticketCount < 150) && (remains > 2000);
    }

    public void startSLAReportGeneration() {
        startGenerationThread();
    }

    public void stopSLAReportGeneration() {
        if (isSLAReportGeneratorRunning()) {
            slaReportGeneratorStop = true;
            synchronized (slaReportGeneratorLock) {
                log.info("slaReportGeneratorLock release");
                slaReportGeneratorLock.notifyAll();
            }
        }
    }

    public boolean isSLAReportGeneratorRunning() {
        return (slaReportGenerator != null);
    }
}
