package com.sk.bds.ticket.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketResponse;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketStatus;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.PostConstruct;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class SlaService {
    private static final boolean AgentTimeWithAgentInfo = false;
    private static final boolean UpdateAgentTimeWhileCheckingViolation = false;
    public static final int TimeLengthMin = 10;

    AppConfig config;
    DateFormat freshdeskTimeFormat;
    DateFormat localTimeFormat;

    @PostConstruct
    public void init() {
        config = AppConfig.getInstance();
        freshdeskTimeFormat = TicketUtil.getFreshdeskDateFormat();
        localTimeFormat = TicketUtil.getLocalDateFormat();
        if (config.isSlaReportEnabled()) {
            getSlaReportGenerator().startSLAReportGeneration();
        }
    }

    private Agent getFreshdeskTicketAgent(String freshdeskTicketId) {
        try {
            FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
            JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
            return getFreshdeskTicketAgent(freshdeskTicketData);
        } catch (AppInternalError e) {
            log.error("error : {}", e);
        }
        return null;
    }

    private Agent getFreshdeskTicketAgent(JSONObject freshdeskTicketData) {
        String freshdeskTicketId = null;
        if (freshdeskTicketData != null) {
            freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
            if (freshdeskTicketData.has(FreshdeskTicketField.RespondereId)) {
                long responderId = freshdeskTicketData.optLong(FreshdeskTicketField.RespondereId);
                log.info("freshdeskTicketId: {}, responderId:{}", freshdeskTicketId, responderId);
                for (Agent l1 : config.getL1Agents()) {
                    if (l1.equalsId(responderId)) {
                        log.info("freshdeskTicketId: {}, found L1: {}", freshdeskTicketId, l1);
                        return l1;
                    }
                }
                for (Agent l2 : config.getL2Agents()) {
                    if (l2.equalsId(responderId)) {
                        log.info("freshdeskTicketId: {}, found L2: {}", freshdeskTicketId, l2);
                        return l2;
                    }
                }
            }
        }
        log.info("Not found Ticket Agent: {}", freshdeskTicketId);
        return null;
    }

    private boolean isAgentId(long userId) {
        return config.isAgentId(userId);
    }

    private boolean isL1Agent(long userId) {
        return config.isL1AgentId(userId);
    }

    private boolean isL2Agent(long userId) {
        return config.isL2AgentId(userId);
    }

    private Agent getAgent(long userId) {
        Agent agent = config.getL1AgentById(userId);
        if (agent == null) {
            agent = config.getL2AgentById(userId);
        }
        return agent;
    }

    private Agent getL1Agent(long userId) {
        return config.getL1AgentById(userId);
    }

    private Agent getL2Agent(long userId) {
        return config.getL2AgentById(userId);
    }

    private static final String AgentTimeDivider = "/";

    private String buildAgentTime(String agentEmail, String timeString) {
        return agentEmail + AgentTimeDivider + timeString;
    }

    private String[] parseAgentTime(String agentTimeText) {
        if (agentTimeText != null) {
            return agentTimeText.split(AgentTimeDivider);
        }
        return null;
    }

    private List<JSONObject> getAgentPublicConversations(String freshdeskTicketId) {
        List<JSONObject> conversations = new ArrayList<>();
        if (freshdeskTicketId != null) {
            FreshdeskConversationLoader conversationLoader = FreshdeskConversationLoader.by(freshdeskTicketId);
            while (conversationLoader.hasNext()) {
                JSONArray freshdeskConversations = conversationLoader.nextSafety();
                for (int i = 0; i < freshdeskConversations.length(); i++) {
                    JSONObject conversation = freshdeskConversations.getJSONObject(i);
                    if (conversation.getBoolean(FreshdeskTicketField.Private)) {
                        continue;
                    }
                    long userId = conversation.optLong(FreshdeskTicketField.ConversationUserId);
                    if (isAgentId(userId)) {
                        conversations.add(conversation);
                    }
                }
            }
        }
        return conversations;
    }

    private Date getAgentFirstResponseTime(List<JSONObject> conversations, long agentUserId) {
        if (conversations != null && conversations.size() > 0) {
            for (JSONObject conversation : conversations) {
                long userId = conversation.optLong(FreshdeskTicketField.ConversationUserId);
                if (userId == agentUserId) {
                    String createAt = conversation.optString(FreshdeskTicketField.CreatedAt);
                    return Util.parseTime(createAt, freshdeskTimeFormat);
                }
            }
        }
        return null;
    }

    private Date getAgentFirstResponseTime(String freshdeskTicketId, long agentUserId) {
        List<JSONObject> conversations = getAgentPublicConversations(freshdeskTicketId);
        return getAgentFirstResponseTime(conversations, agentUserId);
    }

    private String checkAndUpdateL1ResponseTime(JSONObject freshdeskTicketData, List<JSONObject> conversations) {
        String agentTime = "";
        if (freshdeskTicketData != null) {
            String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
            long responderId = freshdeskTicketData.optLong(FreshdeskTicketField.RespondereId);
            Agent l1 = getL1Agent(responderId);
            JSONObject freshdeskTicketCustomData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
            String l1ResponseTime = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfL1ResponseTime);
            if (l1 != null) {
                //L1ResponseTime이 비어 있는 경우에만 업데이트.
                if (l1ResponseTime == null || l1ResponseTime.trim().length() < TimeLengthMin) {
                    Date responseTime;

                    if (conversations == null) {
                        responseTime = getAgentFirstResponseTime(freshdeskTicketId, responderId);
                    } else {
                        responseTime = getAgentFirstResponseTime(conversations, responderId);
                    }

                    if (responseTime != null) {
                        l1ResponseTime = localTimeFormat.format(responseTime);
                        if (AgentTimeWithAgentInfo) {
                            agentTime = buildAgentTime(l1.getEmail(), l1ResponseTime);
                        } else {
                            agentTime = l1ResponseTime;
                        }
                        try {
                            //L1 Response Time 업데이트
                            log.info("Update ticket {} L1 Response Time: {}", freshdeskTicketId, agentTime);
                            JSONObject updateData = new JSONObject();
                            JSONObject updateCustomData = new JSONObject();
                            updateCustomData.put(FreshdeskTicketField.CfL1ResponseTime, agentTime);
                            updateData.put(FreshdeskTicketField.CustomFields, updateCustomData);
                            FreshdeskService.updateTicket(freshdeskTicketId, updateData);
                        } catch (AppInternalError e) {
                            agentTime = "";
                            log.error("L1 response time update failed. {}", e);
                        }
                    }
                } else {
                    agentTime = l1ResponseTime;
                }
            } else {
                log.error("Current agent {} is not L1.", responderId);
            }
        }
        return agentTime;
    }

    private String checkAndUpdateEscalationTime(JSONObject freshdeskTicketData) {
        String escalationTime = "";
        if (freshdeskTicketData != null) {
            String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
            JSONObject freshdeskTicketCustomData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
            if (config.isEscalationCheckEnabled()) {
                String escalation = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfEscalation);
                if (!"Y".equals(escalation)) {
                    log.error("ticket escalation field is not 'Y'. aborted.");
                    return "";
                }
            }
            escalationTime = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfEscalationTime);
            //escalationTime이 비어 있는 경우에만 업데이트.
            if (escalationTime == null || escalationTime.trim().length() < TimeLengthMin) {
                escalationTime = localTimeFormat.format(new Date());
                try {
                    //Escalation Time 업데이트.
                    log.info("Update ticket {} escalation Time: {}", freshdeskTicketId, escalationTime);
                    JSONObject updateData = new JSONObject();
                    JSONObject updateCustomData = new JSONObject();
                    updateCustomData.put(FreshdeskTicketField.CfEscalationTime, escalationTime);
                    updateData.put(FreshdeskTicketField.CustomFields, updateCustomData);
                    FreshdeskService.updateTicket(freshdeskTicketId, updateData);
                } catch (AppInternalError e) {
                    log.error("escalation time update failed. {}", e);
                }
            }
        }
        return escalationTime;
    }

    private String checkAndUpdateL2AssignTime(JSONObject freshdeskTicketData) {
        String agentTime = "";
        if (freshdeskTicketData != null) {
            String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
            long responderId = freshdeskTicketData.optLong(FreshdeskTicketField.RespondereId);
            Agent l2 = getL2Agent(responderId);
            JSONObject freshdeskTicketCustomData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
            String l2AssignTime = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfL2AssignTime);
            if (l2 != null) {
                //l2AssignTime이 비어 있는 경우에만 업데이트.
                if (l2AssignTime == null || l2AssignTime.trim().length() < TimeLengthMin) {
                    l2AssignTime = localTimeFormat.format(new Date());
                    if (AgentTimeWithAgentInfo) {
                        agentTime = buildAgentTime(l2.getEmail(), l2AssignTime);
                    } else {
                        agentTime = l2AssignTime;
                    }
                    try {
                        //L2 Assign Time 업데이트.
                        log.info("Update ticket {} L2 Assign Time: {}", freshdeskTicketId, agentTime);
                        JSONObject updateData = new JSONObject();
                        JSONObject updateCustomData = new JSONObject();
                        updateCustomData.put(FreshdeskTicketField.CfL2AssignTime, agentTime);
                        updateData.put(FreshdeskTicketField.CustomFields, updateCustomData);
                        FreshdeskService.updateTicket(freshdeskTicketId, updateData);
                    } catch (AppInternalError e) {
                        agentTime = "";
                        log.error("L2 assign time update failed. {}", e);
                    }
                } else {
                    agentTime = l2AssignTime;
                }
            } else {
                log.error("Current agent {} is not L2. {}", responderId, getAgent(responderId));
            }
        }
        return agentTime;
    }

    private String checkAndUpdateL2ResponseTime(JSONObject freshdeskTicketData, List<JSONObject> conversations) {
        String agentTime = "";
        if (freshdeskTicketData != null) {
            String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
            long responderId = freshdeskTicketData.optLong(FreshdeskTicketField.RespondereId);
            Agent l2 = getL2Agent(responderId);
            JSONObject freshdeskTicketCustomData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
            String l2ResponseTime = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfL2ResponseTime);
            if (l2 != null) {
                //L2ResponseTime이 비어 있는 경우에만 업데이트.
                if (l2ResponseTime == null || l2ResponseTime.trim().length() < TimeLengthMin) {
                    Date responseTime;

                    if (conversations == null) {
                        responseTime = getAgentFirstResponseTime(freshdeskTicketId, responderId);
                    } else {
                        responseTime = getAgentFirstResponseTime(conversations, responderId);
                    }

                    if (responseTime != null) {
                        l2ResponseTime = localTimeFormat.format(responseTime);
                        if (AgentTimeWithAgentInfo) {
                            agentTime = buildAgentTime(l2.getEmail(), l2ResponseTime);
                        } else {
                            agentTime = l2ResponseTime;
                        }
                        try {
                            //L2 Response Time 업데이트.
                            log.info("Update ticket {} L2 Response Time: {}", freshdeskTicketId, l2ResponseTime);
                            JSONObject updateData = new JSONObject();
                            JSONObject updateCustomData = new JSONObject();
                            updateCustomData.put(FreshdeskTicketField.CfL2ResponseTime, agentTime);
                            updateData.put(FreshdeskTicketField.CustomFields, updateCustomData);
                            FreshdeskService.updateTicket(freshdeskTicketId, updateData);
                        } catch (AppInternalError e) {
                            agentTime = "";
                            log.error("L2 Response time updating failed. {}", e);
                        }
                    }
                } else {
                    agentTime = l2ResponseTime;
                }
            } else {
                log.error("Current agent {} is not L2.", responderId);
            }
        }
        return agentTime;
    }

    public String escalate(String freshdeskTicketId) {
        String escalationTime = "";
        try {
            FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
            JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
            escalationTime = checkAndUpdateEscalationTime(freshdeskTicketData);
        } catch (AppInternalError e) {
            log.error("Escalation time updating failed. {} - {}", freshdeskTicketId, e);
        }
        log.info("Freshdesk ticket: {} - escalation Time: {}", freshdeskTicketId, escalationTime);
        return escalationTime;
    }

    public String l1Ack(String freshdeskTicketId) {
        String agentTime = "";
        //L1 Agent의 공개노트가 있는지 확인.
        try {
            FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
            JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
            agentTime = checkAndUpdateL1ResponseTime(freshdeskTicketData, null);
        } catch (AppInternalError e) {
            log.error("L1 Response time updating failed. {}", e);
        }
        log.info("Freshdesk ticket: {} - L1 Response Time: {}", freshdeskTicketId, agentTime);
        return agentTime;
    }

    public String l2Assign(String freshdeskTicketId) {
        String agentTime = "";
        //responder_id 가 설정된 시간 값을 지정하는 필드는 없음. Activities 조회하는 API 없음.
        //L2 Assign Time이 기록 되어 있는지 확인.
        //Responder id를 찾아서 id의 email이 L2 Agent인지 확인.
        //L2 Assign Time 업데이트. (현재 시간으로 적용)
        try {
            FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
            JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
            agentTime = checkAndUpdateL2AssignTime(freshdeskTicketData);
        } catch (AppInternalError e) {
            log.error("L2 Assign time updating failed. {}", e);
        }
        log.info("Freshdesk ticket: {} - L2 Assign Time: {}", freshdeskTicketId, agentTime);
        return agentTime;
    }

    public String l2Ack(String freshdeskTicketId) {
        String agentTime = "";
        //L2 Agent의 공개노트가 있는지 확인.
        try {
            FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
            JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
            agentTime = checkAndUpdateL2ResponseTime(freshdeskTicketData, null);
        } catch (AppInternalError e) {
            log.error("L2 Response time updating failed. {}", e);
        }
        log.info("Freshdesk ticket: {} - L2 Response Time: {}", freshdeskTicketId, agentTime);
        return agentTime;
    }

    public JSONObject listViolations(long targetStartTime, long targetEndTime, AgentLevel targetAgentLevel, TicketStatus ticketStatus) {
        log.info("targetStartTime:{}, targetEndTime:{}, targetAgentLevel:{}, ticketStatus:{}", targetStartTime, targetEndTime, targetAgentLevel, ticketStatus);
        SlaResearcher researcher = new SlaResearcher(targetStartTime, targetEndTime, targetAgentLevel, ticketStatus);
        researcher.start();
        List<SlaViolationEntity> violations = researcher.getViolations();
        String dateFrom = TicketUtil.getLocalTimeString(new Date(researcher.getTargetStartTime()));
        String dateTo = TicketUtil.getLocalTimeString(new Date(researcher.getTargetEndTime()));
        JSONObject result = new JSONObject();
        result.put("targetDateFrom", dateFrom);
        result.put("targetDateTo", dateTo);
        result.put("count", violations.size());
        result.put("l1SlaTimeLimit", config.getSlaTimeL1() + "");
        result.put("l2SlaTimeLimit", config.getSlaTimeL2());
        result.put("processingTime", Util.timeToString(researcher.getProcessingEndTime() - researcher.getProcessingStartTime()));
        try {
            String violationsText = JsonUtil.marshal(violations);
            JSONArray violationArray = new JSONArray(violationsText);
            result.put("violations", violationArray);
        } catch (JsonProcessingException e) {
            log.warn("serializing failed. {}", e);
            result.put("violations", new JSONArray(violations));
        }
        researcher.clear();
        return result;
    }

    public JSONObject listViolations(AgentLevel targetAgentLevel, TicketStatus ticketStatus) {
        //log.info("targetAgentLevel:{}, ticketStatus:{}", targetAgentLevel, ticketStatus);
        return listViolations(getSlaTargetTime(), 0, targetAgentLevel, ticketStatus);
    }

    public JSONObject listViolations(TicketStatus ticketStatus) {
        //log.info("ticketStatus:{}", ticketStatus);
        return listViolations(AgentLevel.l1l2, ticketStatus);
    }

    public JSONObject listL1Violations(TicketStatus ticketStatus) {
        //log.info("ticketStatus:{}", ticketStatus);
        return listViolations(AgentLevel.l1, ticketStatus);
    }

    public JSONObject listL2Violations(TicketStatus ticketStatus) {
        //log.info("ticketStatus:{}", ticketStatus);
        return listViolations(AgentLevel.l2, ticketStatus);
    }

    //설정된 기간 범위 내의 모든 Agent SLA Violation 체크
    public JSONObject listViolations(Date startTime, Date endTime, TicketStatus ticketStatus) {
        //log.info("startTime:{}, endTime:{}, ticketStatus:{}", startTime, endTime, ticketStatus);
        return listViolations(startTime.getTime(), endTime.getTime(), AgentLevel.l1l2, ticketStatus);
    }

    //설정된 기간 범위 내의 L1 SLA Violation 체크
    public JSONObject listL1Violations(Date startTime, Date endTime, TicketStatus ticketStatus) {
        //log.info("startTime:{}, endTime:{}, ticketStatus:{}", startTime, endTime, ticketStatus);
        return listViolations(startTime.getTime(), endTime.getTime(), AgentLevel.l1, ticketStatus);
    }

    //설정된 기간 범위 내의 L2 SLA Violation 체크
    public JSONObject listL2Violations(Date startTime, Date endTime, TicketStatus ticketStatus) {
        //log.info("startTime:{}, endTime:{}, ticketStatus:{}", startTime, endTime, ticketStatus);
        return listViolations(startTime.getTime(), endTime.getTime(), AgentLevel.l2, ticketStatus);
    }

    private long getSlaTargetTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -config.getSlaTargetDays());
        Util.resetTimeToZero(calendar);
        return calendar.getTimeInMillis();
        //return config.getTicketSyncTargetTime();
    }

    enum ResearchingStep {
        idle,
        violationResearching,
        violationResearchingDone,
        canceled
    }

    //Freshdesk에는 Agent field가 하나 뿐이다. 따라서, L2가 지원하는 시점에는 L1이 누구였는지 알수 없다.
    //Conversation에서 첫번째 응답한 Agent가 누구인지 찾아야함.
    //또는 L1 Response Time L2 ResponseTime 작성시 agent email을 같이 표시함.(ex: zcare.dev@gmail.com/20210515T125212)
    private class SlaResearcher {
        AgentLevel targetAgentLevel;
        TicketStatus ticketStatus;
        Queue<TimeSection> timeSections;
        @Getter
        ResearchingStep researchingStep;
        @Getter
        List<SlaViolationEntity> violations;
        @Getter
        long targetStartTime;
        @Getter
        long targetEndTime;
        @Getter
        long processingStartTime;
        @Getter
        long processingEndTime;
        Semaphore semaphore;
        Object stepLocker;
        boolean stopResearching;


        private SlaResearcher(AgentLevel targetAgentLevel, TicketStatus ticketStatus) {
            this.targetAgentLevel = targetAgentLevel;
            this.ticketStatus = ticketStatus;
            this.stepLocker = new Object();
            this.researchingStep = ResearchingStep.idle;
            this.timeSections = new ConcurrentLinkedQueue<>();
            this.semaphore = new Semaphore(config.getTicketSyncConcurrentMax(), true);
            this.violations = new ArrayList<>();
            this.targetStartTime = getSlaTargetTime();
            this.targetEndTime = 0;
            this.processingStartTime = 0;
            this.processingEndTime = 0;
        }

        private SlaResearcher(long targetStartTime, long targetEndTime, AgentLevel targetAgentLevel, TicketStatus ticketStatus) {
            this.targetAgentLevel = targetAgentLevel;
            this.ticketStatus = ticketStatus;
            this.stepLocker = new Object();
            this.researchingStep = ResearchingStep.idle;
            this.timeSections = new ConcurrentLinkedQueue<>();
            this.semaphore = new Semaphore(config.getTicketSyncConcurrentMax(), true);
            this.violations = new ArrayList<>();
            this.targetStartTime = targetStartTime;
            this.targetEndTime = targetEndTime;
            this.processingStartTime = 0;
            this.processingEndTime = 0;
        }

        private boolean isOperatingSection(TimeSection section) {
            return timeSections.contains(section);
        }

        public void start() {
            log.info("researchingStep:{}", researchingStep);
            if (researchingStep == ResearchingStep.idle) {
                processingStartTime = System.currentTimeMillis();
                startResearching();
            }
        }

        public void stop() {
            log.info("");
            stopResearching = true;
        }

        private void onEnterSection(TimeSection section) throws InterruptedException {
            log.info("semaphore.acquire - section:{}", section.print());
            checkRunnable();
            log.info("semaphore.acquire returned. section:{}", section.print());
            timeSections.offer(section);
        }

        private void onExitSection(TimeSection section) {
            log.info("section complete. {}", section.print());
            if ((targetStartTime == 0) || (targetStartTime > section.getStart())) {
                targetStartTime = section.getStart();
            }
            if ((targetEndTime == 0) || (targetEndTime < section.getEnd())) {
                targetEndTime = section.getEnd();
            }

            timeSections.remove(section);
            completeRunnable();
            if (isStepComplete()) {
                synchronized (stepLocker) {
                    log.info("release step complete waiting.");
                    stepLocker.notifyAll();
                }
            }
        }

        private void startResearching() {
            log.info("researchingStep:{}", researchingStep);
            if (researchingStep == ResearchingStep.idle) {
                violations.clear();
                researchingStep = ResearchingStep.violationResearching;
                //TimeSectionGroup timeSections = new TimeSectionGroup(getSlaTargetTime(), AppConstants.getLocalTimeZone(), TimeSectionGroup.SectionInterval.date1);
                TimeSectionGroup timeSections = new TimeSectionGroup(targetStartTime, targetEndTime, AppConstants.getLocalTimeZone(), TimeSectionGroup.SectionInterval.date1);
                int threadCount = 0;
                SimpleDateFormat sectionNameFormat = new SimpleDateFormat("MMdd");
                while (timeSections.hasNext()) {
                    if (stopResearching) {
                        log.info("SLA researching canceled.");
                        onCancelResearching();
                        return;
                    }

                    final TimeSection section = timeSections.next();
                    if (!isOperatingSection(section)) {
                        log.info("SLA researching::section() - {}, availablePermits:{}", section, semaphore.availablePermits());
                        try {
                            onEnterSection(section);
                            threadCount++;
                            String sectionName = sectionNameFormat.format(new Date(section.getStart()));
                            final String threadName = "SLA-Researcher-" + threadCount + "-" + sectionName;
                            log.info("SLA researching generate new initializing thread for {}.", section.print());
                            new Thread(new ViolationResearching(section, ticketStatus), threadName).start();
                            log.info("SLA researching thread started. threadCount: {}", threadCount);
                        } catch (InterruptedException e) {
                            log.error("", e);
                        }
                    }
                }

                synchronized (stepLocker) {
                    try {
                        log.info("waiting step complete");
                        stepLocker.wait();
                    } catch (InterruptedException e) {
                        log.error("waiting step complete error: {}", e);
                    }
                }
                log.info("stepLocker released");
                onCompleteResearching();
            }
        }

        private void checkRunnable() throws InterruptedException {
            semaphore.acquire();
        }

        private void completeRunnable() {
            semaphore.release();
        }

        private boolean isStepComplete() {
            log.info("operating count: {}", timeSections.size());
            return (timeSections.size() == 0);
        }

        private void onCancelResearching() {
            log.info("SLA researching canceled.");
            researchingStep = ResearchingStep.canceled;
            processingEndTime = System.currentTimeMillis();
        }

        private void onCompleteResearching() {
            log.info("SLA researching ticket count: {}", violations.size());
            if (researchingStep == ResearchingStep.violationResearching) {
                researchingStep = ResearchingStep.violationResearchingDone;
            }
            if (isDone()) {
                processingEndTime = System.currentTimeMillis();
            }
        }

        public void clear() {
            timeSections.clear();
            violations.clear();
        }

        public ResearchingStep currentStep() {
            log.info("researchingStep:{}", researchingStep);
            return researchingStep;
        }

        public boolean isDone() {
            log.info("researchingStep:{}", researchingStep);
            return (researchingStep == ResearchingStep.violationResearchingDone);
        }

        public boolean isCanceled() {
            log.info("researchingStep:{}", researchingStep);
            return (researchingStep == ResearchingStep.canceled);
        }

        public boolean isBreakable() {
            return (isDone() || isCanceled());
        }

        private SlaViolationEntity checkViolation(JSONObject freshdeskTicketData) {
            if (freshdeskTicketData != null) {
                TicketStatus ticketStatus;
                List<JSONObject> conversations = null;
                String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
                long responderId = freshdeskTicketData.optLong(FreshdeskTicketField.RespondereId);

                int status = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
                if (status == FreshdeskTicketStatus.Pending) {
                    if (config.isFreshdeskOpenTicketStatusIncludesPending()) {
                        ticketStatus = TicketStatus.opened;
                    } else {
                        ticketStatus = TicketStatus.pending;
                    }
                } else if (status == FreshdeskTicketStatus.Resolved) {
                    if (config.isFreshdeskClosedTicketStatusIncludesResolved()) {
                        ticketStatus = TicketStatus.closed;
                    } else {
                        ticketStatus = TicketStatus.resolved;
                    }
                } else if (status == FreshdeskTicketStatus.Closed) {
                    ticketStatus = TicketStatus.closed;
                } else {
                    ticketStatus = TicketStatus.opened;
                }

                SlaViolationEntity entity = new SlaViolationEntity(freshdeskTicketId, ticketStatus);
                if (responderId == 0L) {
                    entity.setAssignedLevel(AgentLevel.notAssigned);
                    return entity;
                }

                JSONObject freshdeskTicketCustomData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
                String ticketCreateTimeText = freshdeskTicketData.optString(FreshdeskTicketField.CreatedAt);
                String l1ResponseTimeText = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfL1ResponseTime);
                String l2AssignTimeText = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfL2AssignTime);
                String l2ResponseTimeText = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfL2ResponseTime);
                Date createTime = Util.parseTime(ticketCreateTimeText, freshdeskTimeFormat);
                entity.setAssignedLevel(AgentLevel.l1);
                entity.setTicketCreatedTime(createTime);

                ///// Check L1 Response Time
                if (UpdateAgentTimeWhileCheckingViolation) {
                    if (l1ResponseTimeText.trim().length() < TimeLengthMin) { //No response yet.
                        conversations = getAgentPublicConversations(freshdeskTicketId);
                        l1ResponseTimeText = checkAndUpdateL1ResponseTime(freshdeskTicketData, conversations);
                    }
                }

                ///// Calculate L1 Elapsed Time.
                if (l1ResponseTimeText.trim().length() < TimeLengthMin) { //No response yet.
                    int elapsedTimeSeconds = (int) ((System.currentTimeMillis() - createTime.getTime()) / 1000);
                    entity.setL1Responded(false);
                    entity.setL1TimeSeconds(elapsedTimeSeconds);
                    entity.setL1(getL1Agent(responderId));
                } else {
                    if (AgentTimeWithAgentInfo && l1ResponseTimeText.contains(AgentTimeDivider)) {
                        String[] agentTimeArray = parseAgentTime(l1ResponseTimeText);
                        Agent l1 = config.getL1AgentByEmail(agentTimeArray[0]);
                        Date responseTime = Util.parseTime(agentTimeArray[1], localTimeFormat);
                        int elapsedTimeSeconds = (int) ((responseTime.getTime() - createTime.getTime()) / 1000);
                        entity.setL1Responded(true);
                        entity.setL1TimeSeconds(elapsedTimeSeconds);
                        entity.setL1(l1);
                    } else {
                        Date responseTime = Util.parseTime(l1ResponseTimeText, localTimeFormat);
                        int elapsedTimeSeconds = (int) ((responseTime.getTime() - createTime.getTime()) / 1000);
                        entity.setL1Responded(true);
                        entity.setL1TimeSeconds(elapsedTimeSeconds);
                        if (l2AssignTimeText.trim().length() < TimeLengthMin) { //Not escalated to L2.
                            entity.setL1(getL1Agent(responderId));
                        }
                    }
                }

                ///// Calculate L2 Elapsed Time.
                if (l2AssignTimeText.trim().length() < TimeLengthMin) {
                    entity.setL2Responded(false);
                    entity.setL2TimeSeconds(0);
                } else {
                    Agent ticketL2 = getL2Agent(responderId);
                    Agent assignedL2 = null;
                    Date assignTime = null;
                    //현재 responderId에 해당하는 L2를 찾지 못할 경우 Assign Time 정보의 Agent를 L2로 지정.
                    if (AgentTimeWithAgentInfo && l2AssignTimeText.contains(AgentTimeDivider)) {
                        String[] assignTimeArray = parseAgentTime(l2AssignTimeText);
                        assignedL2 = config.getL2AgentByEmail(assignTimeArray[0]);
                        assignTime = Util.parseTime(assignTimeArray[1], localTimeFormat);
                    } else {
                        assignTime = Util.parseTime(l2AssignTimeText, localTimeFormat);
                    }

                    if (ticketL2 != null) {
                        entity.setL2(ticketL2);
                    } else if (assignedL2 != null) {
                        entity.setL2(assignedL2);
                    }
                    entity.setAssignedLevel(AgentLevel.l2);

                    ///// Check L2 Response Time
                    if (UpdateAgentTimeWhileCheckingViolation) {
                        if (l2ResponseTimeText.trim().length() < TimeLengthMin) { //No response yet.
                            if (conversations == null) {
                                conversations = getAgentPublicConversations(freshdeskTicketId);
                            }
                            l2ResponseTimeText = checkAndUpdateL2ResponseTime(freshdeskTicketData, conversations);
                        }
                    }

                    if (l2ResponseTimeText.trim().length() < TimeLengthMin) { //No response yet.
                        int elapsedTimeSeconds = (int) ((System.currentTimeMillis() - assignTime.getTime()) / 1000);
                        entity.setL2Responded(false);
                        entity.setL2TimeSeconds(elapsedTimeSeconds);
                    } else {
                        if (AgentTimeWithAgentInfo && l2ResponseTimeText.contains(AgentTimeDivider)) {
                            String[] agentTimeArray = parseAgentTime(l2ResponseTimeText);
                            Agent responderL2 = config.getL2AgentByEmail(agentTimeArray[0]);
                            if (responderL2 != null) {
                                entity.setL2(responderL2);
                            }
                            Date responseTime = Util.parseTime(agentTimeArray[1], localTimeFormat);
                            int elapsedTimeSeconds = (int) ((responseTime.getTime() - assignTime.getTime()) / 1000);
                            entity.setL2Responded(true);
                            entity.setL2TimeSeconds(elapsedTimeSeconds);
                        } else {
                            Date responseTime = Util.parseTime(l2ResponseTimeText, localTimeFormat);
                            int elapsedTimeSeconds = (int) ((responseTime.getTime() - assignTime.getTime()) / 1000);
                            entity.setL2Responded(true);
                            entity.setL2TimeSeconds(elapsedTimeSeconds);
                        }
                    }
                }

                if (targetAgentLevel == AgentLevel.l1) {
                    if (entity.isL1Violation(config.getSlaTimeL1())) {
                        return entity;
                    }
                } else if (targetAgentLevel == AgentLevel.l2) {
                    if (entity.isL2Violation(config.getSlaTimeL2())) {
                        return entity;
                    }
                } else {
                    if (entity.isL1Violation(config.getSlaTimeL1()) || entity.isL2Violation(config.getSlaTimeL2())) {
                        return entity;
                    }
                }
            }
            return null;
        }

        private class ViolationResearching implements Runnable {
            private TimeSection timeSection;
            private TicketStatus ticketStatus;

            private ViolationResearching(TimeSection section, TicketStatus ticketStatus) {
                timeSection = section;
                this.ticketStatus = ticketStatus;
            }

            @Override
            public void run() {
                log.info("[ViolationResearching] start. section:{}", timeSection.print());
                final FreshdeskTicketLoader loader = FreshdeskTicketLoader.byDay(AppConstants.CSP_NAME, new Date(timeSection.getStart()), ticketStatus);
                long ticketResearchingEndTime = 0;
                int totalCount = 0;
                if (timeSection.hasEndTime()) {
                    ticketResearchingEndTime = timeSection.getEnd();
                } else {
                    ticketResearchingEndTime = System.currentTimeMillis();
                    timeSection.setEnd(ticketResearchingEndTime);
                }

                while (loader.hasNext()) {
                    if (!timeSection.hasEndTime()) {
                        ticketResearchingEndTime = System.currentTimeMillis();
                    }

                    JSONArray ticketArray = loader.next();
                    totalCount += ticketArray.length();
                    log.info("[ViolationResearching] {} - totalCount:{}", timeSection.print(), totalCount);
                    for (int i = 0; i < ticketArray.length(); i++) {
                        try {
                            if (stopResearching) {
                                log.info("[ViolationResearching] Ticket Synchronization disabled.");
                                onCancelResearching();
                                return;
                            }
                            JSONObject freshdeskTicketData = ticketArray.getJSONObject(i);
                            SlaViolationEntity entity = checkViolation(freshdeskTicketData);
                            if (entity != null) {
                                violations.add(entity);
                            }
                        } catch (JSONException | IllegalArgumentException e) {
                            log.error("[ViolationResearching] Freshdesk ticket initializing failed. sectoin: {} - error:{}", timeSection.print(), e);
                        }
                    }
                }
                log.info("[ViolationResearching] end. section:{}, totalCount: {}, ticketResearchingEndTime: {}", timeSection.print(), totalCount, ticketResearchingEndTime);
                onExitSection(timeSection);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //SLA Report API
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static class SlaReportGeneratorLazyHolder {
        private static final com.sk.bds.ticket.api.util.SlaReportGenerator SlaReportGenerator = new SlaReportGenerator();
    }

    private static SlaReportGenerator getSlaReportGenerator() {
        return SlaReportGeneratorLazyHolder.SlaReportGenerator;
    }

    public void startSlaReportGenerator() throws AppError {
        if (config.isSlaReportEnabled()) {
            getSlaReportGenerator().startSLAReportGeneration();
        } else {
            throw new AppError.NotAcceptable("SLA report ability is disabled.");
        }
    }

    public void stopSlaReportGenerator() {
        getSlaReportGenerator().stopSLAReportGeneration();
    }

    public JSONArray getSlaReports() {
        return getSlaReportGenerator().getSlaReports();
    }

    public String createSlaReport(SlaRequestParam param) throws AppError {
        if (config.isSlaReportEnabled()) {
            return getSlaReportGenerator().createSlaReport(param);
        } else {
            throw new AppError.NotAcceptable("SLA report ability is disabled.");
        }
    }

    public ResponseEntity<StreamingResponseBody> getSlaReport(String reportId, SlaReport.ReportType type) throws AppError {
        return getSlaReportGenerator().getSlaReport(reportId, type);
    }

}
