package com.sk.bds.ticket.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZUser;
import com.sk.bds.ticket.api.data.model.freshdesk.*;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.sk.bds.ticket.api.data.model.ibm.IbmTicketStatus;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.util.*;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.ticket.Update;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

@Slf4j
public class TicketServiceInitializer {
    interface ActionOperator {
        void onStateChanged(InitializingState state);

        void onFoundTicketId(String freshdeskTicketId, String cspTicketId);

        void onFoundMonitoringTicket(String freshdeskTicketId, TicketMetadata ticketMetadata);

        void onCompleteFreshdeskResearching(ResearchingMetadata researchingMetadata);

        void onCompleteCspResearching(Map<String, ResearchingMetadata> researchingMetadataMap);

        ProcessResult doCreateCspTicket(JSONObject freshdeskTicketData);

        ProcessResult doCreateFreshdeskTicket(FreshdeskTicketBuilder ticketBuilder);

        ProcessResult doSynchronizeTicket(String freshdeskTicketId, OperationBreaker breaker);
    }

    public enum InitializingState {
        idle,
        freshdeskTicketResearchingStart,
        freshdeskTicketResearchingComplete,
        cspTicketResearchingStart,
        cspTicketResearchingComplete,
        freshdeskToCspTicketCreatingStart,
        freshdeskToCspTicketCreatingComplete,
        cspToFreshdeskTicketCreatingStart,
        cspToFreshdeskTicketCreatingComplete,
        conversationSynchronizingStart,
        conversationSynchronizingComplete,
        initialized,
        canceled
    }

    public static String getAllInitializingSteps() {
        InitializingState[] fullSteps = {InitializingState.idle, InitializingState.freshdeskTicketResearchingStart, InitializingState.cspTicketResearchingStart, InitializingState.freshdeskToCspTicketCreatingStart, InitializingState.cspToFreshdeskTicketCreatingStart, InitializingState.conversationSynchronizingStart, InitializingState.initialized};
        List<String> stepNames = new ArrayList<>();
        for (InitializingState step : fullSteps) {
            stepNames.add(step.name());
        }
        return String.join(" -> ", stepNames);
    }

    @Data
    public static class ResearchingMetadata {
        long periodStart;
        long periodEnd;
        int totalTicketCount;
        int newTicketCount;
        long researchingStartedTime;
        long researchingFinishedTime;
        TicketTimeRecord latestTicket;

        public ResearchingMetadata() {
            periodStart = 0;
            periodEnd = 0;
            totalTicketCount = 0;
            newTicketCount = 0;
            researchingStartedTime = 0;
            researchingFinishedTime = 0;
            latestTicket = new TicketTimeRecord();
        }

        private void onResearchingStarted(TimeSection timeSection) {
            if (researchingStartedTime == 0) {
                researchingStartedTime = System.currentTimeMillis();
            }
        }

        private void onResearchingFinished(TimeSection timeSection) {
            researchingFinishedTime = System.currentTimeMillis();
        }

        private void onCompletedSectionEndTime(long researchingSectionPeriodEndTime) {
            if (periodEnd < researchingSectionPeriodEndTime) {
                periodEnd = researchingSectionPeriodEndTime;
            }
        }

        private void onLoadedTicketCount(int ticketCount) {
            totalTicketCount += ticketCount;
        }

        private void onFoundNewTicketCount(int ticketCount) {
            newTicketCount += ticketCount;
        }

        private void onFoundLatestTicket(TicketTimeRecord timeRecord) {
            if (timeRecord != null) {
                if (timeRecord.isNewerThan(latestTicket)) {
                    latestTicket.replace(timeRecord);
                }
            }
        }

        private JSONObject export() throws JsonProcessingException {
            String jsonText = JsonUtil.marshal(this);
            return new JSONObject(jsonText);
        }

        private JSONObject exportReport() throws JsonProcessingException {
            JSONObject report = new JSONObject();
            if (periodStart > 0) {
                report.put("PeriodStart", TicketUtil.getLocalTimeString(new Date(periodStart)));
            } else {
                report.put("PeriodStart", "Invlid");
            }
            if (periodEnd > 0) {
                report.put("PeriodEnd", TicketUtil.getLocalTimeString(new Date(periodEnd)));
            } else {
                report.put("PeriodEnd", "Up to date");
            }
            if (researchingStartedTime > 0) {
                report.put("ResearchingStartedTime", TicketUtil.getLocalTimeString(new Date(researchingStartedTime)));
            } else {
                report.put("ResearchingStartedTime", "Not started yet");
            }
            if (researchingFinishedTime > 0) {
                report.put("ResearchingFinishedTime", TicketUtil.getLocalTimeString(new Date(researchingFinishedTime)));
            } else {
                report.put("ResearchingFinishedTime", "Not finished yet");
            }
            report.put("TotalTicketCount", totalTicketCount);
            report.put("NewTicketCount", newTicketCount);
            JSONObject latestTicketRecord = new JSONObject();
            if (latestTicket.getTicketId() != null) {
                latestTicketRecord.put("TicketId", latestTicket.getTicketId());
            } else {
                latestTicketRecord.put("TicketId", "No data");
            }
            if (latestTicket.getCreateTime() > 0) {
                latestTicketRecord.put("CreateTime", TicketUtil.getLocalTimeString(new Date(latestTicket.getCreateTime())));
            } else {
                latestTicketRecord.put("CreateTime", "No data");
            }
            report.put("LatestTicket", latestTicketRecord);
            return report;
        }
    }

    @Getter
    public static class TicketCounter {
        int totalTicketCount;
        int successTicketCount;
        int errorTicketCount;

        public TicketCounter() {
            this.totalTicketCount = 0;
            this.successTicketCount = 0;
            this.errorTicketCount = 0;
        }

        private void setTotalTicketCount(int count) {
            totalTicketCount = count;
        }

        private void onSuccessCount() {
            successTicketCount++;
        }

        private void onErrorCount() {
            errorTicketCount++;
        }

        private boolean isTotalCountReached() {
            return ((successTicketCount + errorTicketCount) == totalTicketCount);
        }

        private JSONObject export() throws JsonProcessingException {
            String jsonText = JsonUtil.marshal(this);
            return new JSONObject(jsonText);
        }
    }

    @Data
    public static class InitializeReportMetadata {
        ResearchingMetadata freshdeskResearchingMeta;
        Map<String, ResearchingMetadata> cspResearchingMetadataMap;
        TicketCounter freshdeskToCspTicketCreation;
        TicketCounter cspToFreshdeskTicketCreation;
        TicketCounter TicketSynchronization;
        long initializingStartedTime;
        long initializingFinishedTime;
        InitializingState state;

        public InitializeReportMetadata() {
            initializingStartedTime = 0;
            initializingFinishedTime = 0;
            state = TicketServiceInitializer.InitializingState.idle;
        }

        public boolean isCanceled() {
            return state == TicketServiceInitializer.InitializingState.canceled;
        }

        public boolean isInitializeComplete() {
            return (state == TicketServiceInitializer.InitializingState.conversationSynchronizingComplete) || (state == TicketServiceInitializer.InitializingState.initialized);
        }

        public long getResearchingPeriodStartTime() {
            if (freshdeskResearchingMeta != null) {
                return freshdeskResearchingMeta.getPeriodStart();
            }
            return 0;
        }
    }


    private static final String EMPTY_ID = "-";

    Queue<TimeSection> operatingTimeSections;
    TimeSection latestTriggeredTimeSection;
    @Getter
    InitializingState currentState;
    List<String> freshdeskNewTickets;
    Map<String, IbmBrandAccount> cspNewTickets;
    Map<String, String> ticketIdLinker;
    private final Map<String, OperationBreaker> conversationSyncBreakers;
    ResearchingMetadata freshdeskResearchingMeta;
    Map<String, ResearchingMetadata> cspResearchingMetadataMap;
    TicketCounter freshdeskToCspTicketCreatingCounter;
    TicketCounter cspToFreshdeskTicketCreatingCounter;
    TicketCounter synchronizeTicketCounter;
    long initializingStartedTime;
    long initializingFinishedTime;

    boolean freshdeskTicketResearchingComplete = false;
    boolean cspTicketResearchingComplete = false;
    boolean freshdeskToCspTicketCreatingComplete = false;
    boolean cspToFreshdeskTicketCreatingComplete = false;

    Semaphore semaphore;
    Object stepLockerForMultiProcessing;
    boolean stopInitializing;
    Map<String, TicketMetadata> monitoringTickets;

    final CspTicketHandler ticketHandler;
    final ActionOperator actionOperator;
    final AppConfig config = AppConfig.getInstance();

    public TicketServiceInitializer(CspTicketHandler ticketHandler, ActionOperator actionOperator) {
        this.ticketHandler = ticketHandler;
        this.actionOperator = actionOperator;

        monitoringTickets = new ConcurrentHashMap<>();
        ticketIdLinker = new ConcurrentHashMap();
        stepLockerForMultiProcessing = new Object();
        currentState = InitializingState.idle;
        operatingTimeSections = new ConcurrentLinkedQueue<>();
        semaphore = new Semaphore(config.getTicketSyncConcurrentMax(), true);
        freshdeskNewTickets = new ArrayList<>();
        cspNewTickets = new ConcurrentHashMap<>();
        conversationSyncBreakers = new ConcurrentHashMap<>();
        freshdeskToCspTicketCreatingCounter = new TicketCounter();
        cspToFreshdeskTicketCreatingCounter = new TicketCounter();
        synchronizeTicketCounter = new TicketCounter();
        initializingStartedTime = 0;
        initializingFinishedTime = 0;
        initializeResearchingMeta();
    }

    private void initializeResearchingMeta() {
        freshdeskResearchingMeta = new ResearchingMetadata();
        cspResearchingMetadataMap = new ConcurrentHashMap<>();

        freshdeskResearchingMeta.setPeriodStart(config.getTicketSyncTargetTime());
        if (config.getReverseSyncAccounts() != null && config.getReverseSyncAccounts().size() > 0) {
            //final long SoftLayerTimeZoneMissedTime = config.getIbmMissedTimeOffset(); //CST time zone & Central Daylight Time(CDT). IbmTicketLoader에 CST Timezone을 설정하여 Timezone에 의한 오차 수정됨.
            final long SoftLayerApiTimeMargin = config.getIbmSoftLayerApiDelayTime(); ////IBM console에서 새티켓 등록 직후 SoftLayer API로는 티켓이 바로 조회 되지않고, 2~3분 이후에 API로 새로 등록한 티켓이 조회됨. //API Time Margin 적용 필요.
            final long RevisionTime = SoftLayerApiTimeMargin; //SoftLayerTimeZoneMissedTime + SoftLayerApiTimeMargin;
            final long ConfiguredTicketSyncTargetTime = config.getTicketSyncTargetTime();
            final long LimitTimeMax = System.currentTimeMillis() - RevisionTime;
            for (IbmBrandAccount brandAccount : config.getReverseSyncAccounts()) {
                String brandId = brandAccount.getBrandId();
                TicketTimeRecord recordedLastTicket = TicketSyncLogger.getReverseSyncLastTicketTimeRecord(brandId);
                log.info("brand: {}, recordedLastTicket: {}", brandId, recordedLastTicket);
                long filterTimeMillis = Math.max((recordedLastTicket.getCreateTime() - RevisionTime), (ConfiguredTicketSyncTargetTime - RevisionTime));
                filterTimeMillis = Math.min(filterTimeMillis, LimitTimeMax);
                log.info("brand: {}, filterTimeMillis: {}, recordedLastTicket: {}", brandId, filterTimeMillis, recordedLastTicket);
                ResearchingMetadata meta = new ResearchingMetadata();
                meta.setPeriodStart(filterTimeMillis);
                cspResearchingMetadataMap.put(brandId, meta);
            }
        }
    }

    public long getResearchingPeriodStartTime() {
        return freshdeskResearchingMeta.getPeriodStart();
    }

    private void onStateChanged(InitializingState state) {
        currentState = state;
        switch (state) {
            case freshdeskTicketResearchingComplete:
                freshdeskTicketResearchingComplete = true;
                break;
            case cspTicketResearchingComplete:
                cspTicketResearchingComplete = true;
                break;
            case freshdeskToCspTicketCreatingComplete:
                freshdeskToCspTicketCreatingComplete = true;
                break;
            case cspToFreshdeskTicketCreatingComplete:
                cspToFreshdeskTicketCreatingComplete = true;
                break;
        }
        if (actionOperator != null) {
            actionOperator.onStateChanged(state);
        }
    }

    private void onFoundTicketId(String freshdeskTicketId, String cspTicketId) {
        if (freshdeskTicketId != null) {
            if (cspTicketId == null) {
                cspTicketId = EMPTY_ID;
            }
            ticketIdLinker.put(freshdeskTicketId, cspTicketId);
        }
        if (actionOperator != null) {
            actionOperator.onFoundTicketId(freshdeskTicketId, cspTicketId);
        }
    }

    private boolean isLinkedCspTicketId(String cspTicketId) {
        if (cspTicketId != null && !EMPTY_ID.equalsIgnoreCase(cspTicketId)) {
            boolean linked = ticketIdLinker.containsValue(cspTicketId);
            log.info("cspTicketId: {}, linked on freshdesk: {}", cspTicketId, linked);
            return linked;
        }
        return false;
    }

    private void onFoundMonitoringTicket(TicketMetadata ticketMetadata) {
        if (ticketMetadata != null) {
            monitoringTickets.put(ticketMetadata.getFreshdeskTicketId(), ticketMetadata);
            if (actionOperator != null) {
                actionOperator.onFoundMonitoringTicket(ticketMetadata.getFreshdeskTicketId(), ticketMetadata);
            }
        }
    }

    private void onCompleteFreshdeskResearching(ResearchingMetadata metadata) {
        if (actionOperator != null) {
            actionOperator.onCompleteFreshdeskResearching(metadata);
        }
    }

    private void onCompleteCspResearching(Map<String, ResearchingMetadata> metadataMap) {
        if (actionOperator != null) {
            actionOperator.onCompleteCspResearching(metadataMap);
        }
    }

    private boolean canStart(InitializingState action) {
        if (!isSyncEnabled()) {
            return false;
        }

        switch (action) {
            case freshdeskTicketResearchingStart:
                if (currentState == InitializingState.idle) {
                    return true;
                }
            case cspTicketResearchingStart:
                if (isReverseSyncEnabled()) {
                    if (freshdeskTicketResearchingComplete) {
                        return true;
                    }
                }
                break;
            case freshdeskToCspTicketCreatingStart:
                if (freshdeskTicketResearchingComplete && (cspTicketResearchingComplete || !isReverseSyncEnabled())) {
                    return true;
                }
                break;
            case cspToFreshdeskTicketCreatingStart:
                if (isReverseSyncEnabled()) {
                    if (cspTicketResearchingComplete && freshdeskToCspTicketCreatingComplete) {
                        return true;
                    }
                }
                break;
            case conversationSynchronizingStart:
                if (isReverseSyncEnabled()) {
                    return (cspTicketResearchingComplete && cspToFreshdeskTicketCreatingComplete);
                } else {
                    return (freshdeskTicketResearchingComplete && freshdeskToCspTicketCreatingComplete);
                }
        }
        return false;
    }

    private void checkAndReplaceBrandEmail(JSONObject fdTicketData) {
        if (fdTicketData != null) {
            JSONObject customData = fdTicketData.optJSONObject(FreshdeskTicketField.CustomFields);
            if (customData != null) {
                FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(customData.optString(FreshdeskTicketField.CfCspAccount));
                if (accountField.isValid()) {
                    if (TicketUtil.isBrandEmail(accountField.getEmail())) {
                        CloudZUser masterUser = CloudZService.getCloudZMasterUserByAccountId(accountField.getAccountId());
                        if (masterUser != null && masterUser.getUserEmail() != null) {
                            String cspAccountFieldValue = masterUser.getUserEmail() + "/" + accountField.getAccountId();
                            customData.put(FreshdeskTicketField.CfCspAccount, cspAccountFieldValue);
                        } else {
                            log.error("Can not found master email for IBM brand account : {}", accountField.getAccountId());
                        }
                    }
                }
            }
        }
    }

    private boolean canCreateFreshdeskTicket(IbmBrandAccount brandAccount, Ticket ticket) {
        if (brandAccount == null || ticket == null) {
            log.error("invalid parameter.");
            return false;
        }

        long cspTicketId = ticket.getId();
        Long ibmUserCustomerId = ticket.getAssignedUserId();
        String ibmAccountId = TicketUtil.attachIbmAccountPrefix(ticket.getAccountId());
        String ibmTicketCreatorEmail = CloudZService.getIbmCustomerEmail(brandAccount, ibmUserCustomerId);
        if (getCspApiInfo(ibmTicketCreatorEmail, ibmAccountId) != null) {
            log.info("Exists Api Key email:{}, accountId:{} for ticket:{}", ibmTicketCreatorEmail, ibmAccountId, cspTicketId);
            return true;
        }
        log.error("Can not found Api Key for ticket: {}", cspTicketId);
        return false;
    }

    public List<String> getMonitoringTicketIdList() {
        return new ArrayList<>(monitoringTickets.keySet());
    }

    private void updateCspAccountCache() {
        log.info("Refreshing csp account cache");
        CloudZService.updateCspAccountCache();
        CloudZService.updateIbmCustomerCache();
    }

    private CloudZCspApiInfo getCspApiInfo(String email, String apiId) {
        return CloudZService.getCspApiInfo(email, apiId);
    }

    private boolean isSyncEnabled() {
        return config.isSyncEnabled();
    }

    private boolean isReverseSyncEnabled() {
        return config.isReverseSyncEnabled();
    }

    private boolean isSlaReportEnabled() {
        return config.isSlaReportEnabled();
    }

    private boolean isEscalationCheckEnabled() {
        return config.isEscalationCheckEnabled();
    }

    private boolean isBetaTestEnabled() {
        return config.isBetaTestEnabled();
    }

    private boolean isBetaTester(String email) {
        return config.isBetaTester(email);
    }

    private boolean isOperatingSection(TimeSection section) {
        return operatingTimeSections.contains(section);
    }

    public void startTicketInitializing() {
        if (currentState == InitializingState.idle) {
            initializingStartedTime = System.currentTimeMillis();

            if (isSyncEnabled()) {
                updateCspAccountCache();
                startFreshdeskTicketResearching();
            } else {
                log.error("Ticket synchronization is disabled. Initializing aborted.");
                onInitializingCanceled();
            }
        }
    }

    public void stopTicketInitializing() {
        log.info("@@@@@ STOP TICKET SERVICE INITIALIZING... @@@@@");
        stopInitializing = true;
        log.info("currentState: {}", currentState);
        if (currentState == InitializingState.conversationSynchronizingStart) {
            stopTicketConversationSynchronizationProcessings();
        }
    }

    private void onEnterSection(TimeSection section) throws InterruptedException {
        if (section != null) {
            log.debug("semaphore.acquire - section:{}", section.print());
            checkRunnable();
            log.debug("semaphore.acquire returned. section:{}", section.print());
            latestTriggeredTimeSection = section;
            operatingTimeSections.offer(section);
        }
    }

    private void onExitSection(TimeSection section) {
        if (section != null) {
            log.debug("section complete. {}", section.print());
            operatingTimeSections.remove(section);
            completeRunnable();
            if (isStepComplete()) {
                synchronized (stepLockerForMultiProcessing) {
                    log.info("release step complete waiting.");
                    stepLockerForMultiProcessing.notifyAll();
                }
            }
        }
    }

    public JSONObject getOperatingTimeSections() {
        JSONObject output = new JSONObject();
        output.put("OperatingTimeSectionCount", operatingTimeSections.size());
        JSONArray timeArray = new JSONArray();
        List<TimeSection> timeSectionList = new ArrayList<>(operatingTimeSections);
        for (TimeSection timeSection : timeSectionList) {
            timeArray.put(timeSection.print());
        }
        output.put("OperatingTimeSections", timeArray);
        return output;
    }

    private void startFreshdeskTicketResearching() {
        log.info("currentState:{}", currentState);
        if (canStart(InitializingState.freshdeskTicketResearchingStart)) {
            if (stopInitializing || !isSyncEnabled()) {
                log.warn("FreshdeskTicketResearching: Ticket service initializing canceled.");
                onInitializingCanceled();
                return;
            }
            freshdeskNewTickets.clear();
            onStateChanged(InitializingState.freshdeskTicketResearchingStart);

            log.info("FreshdeskTicketResearching: freshdeskResearchingMeta: {}", freshdeskResearchingMeta);

            TimeSectionGroup timeSections = new TimeSectionGroup(freshdeskResearchingMeta.getPeriodStart(), AppConstants.getLocalTimeZone());
            int threadCount = 0;
            SimpleDateFormat sectionNameFormat = new SimpleDateFormat("MMdd");
            while (timeSections.hasNext()) {
                if (stopInitializing || !isSyncEnabled()) {
                    log.warn("FreshdeskTicketResearching: Ticket service initializing canceled.");
                    break;
                }

                final TimeSection section = timeSections.next();
                if (!isOperatingSection(section)) {
                    log.debug("FreshdeskTicketResearching: section: {}, availablePermits: {}", section, semaphore.availablePermits());
                    try {
                        onEnterSection(section);
                        if (stopInitializing || !isSyncEnabled()) {
                            log.warn("FreshdeskTicketResearching: Ticket service initializing canceled.");
                            onExitSection(section);
                            break;
                        }

                        threadCount++;
                        String sectionName = sectionNameFormat.format(new Date(section.getStart()));
                        final String threadName = "INIT-Freshdesk-" + threadCount + "-" + sectionName;
                        log.debug("FreshdeskTicketResearching: Generate new initializing thread for {}.", section.print());
                        new Thread(new FreshdeskTicketResearching(section), threadName).start();
                        log.debug("FreshdeskTicketResearching: ticket initializing thread started. threadCount: {}", threadCount);
                    } catch (InterruptedException e) {
                        log.error("", e);
                    }
                }
            }

            if (!isStepComplete()) {
                synchronized (stepLockerForMultiProcessing) {
                    try {
                        log.debug("FreshdeskTicketResearching: waiting step complete");
                        stepLockerForMultiProcessing.wait();
                    } catch (InterruptedException e) {
                        log.error("FreshdeskTicketResearching: waiting step complete error: {}", e);
                    }
                }
            }

            log.info("FreshdeskTicketResearching: processing complete. stopInitializing: {}", stopInitializing);
            if (stopInitializing) {
                onInitializingCanceled();
            } else {
                log.info("FreshdeskTicketResearching: Freshdesk ticket initialized");
                onCompleteFreshdeskResearching();
            }
        }
    }

    private void startCspTicketResearching() {
        log.info("currentState:{}", currentState);
        if (canStart(InitializingState.cspTicketResearchingStart)) {
            if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                log.warn("CspTicketResearching: Ticket service initializing canceled.");
                onInitializingCanceled();
                return;
            }
            cspNewTickets.clear();
            onStateChanged(InitializingState.cspTicketResearchingStart);

            if (isReverseSyncEnabled()) {
                if (config.getReverseSyncAccounts() != null && config.getReverseSyncAccounts().size() > 0) {
                    for (IbmBrandAccount brandAccount : config.getReverseSyncAccounts()) {
                        String brandId = brandAccount.getBrandId();
                        ResearchingMetadata researchingMetadata = cspResearchingMetadataMap.get(brandId);
                        log.info("CspTicketResearching: brand: {}, researchingMetadata: {}", brandId, researchingMetadata);

                        TimeSectionGroup timeSections = new TimeSectionGroup(researchingMetadata.getPeriodStart(), AppConstants.getLocalTimeZone(), TimeSectionGroup.SectionInterval.hour1);
                        int threadCount = 0;
                        SimpleDateFormat sectionNameFormat = new SimpleDateFormat("MMdd-HHmm");
                        while (timeSections.hasNext()) {
                            if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                                log.warn("CspTicketResearching: Ticket service initializing canceled.");
                                break;
                            }

                            final TimeSection section = timeSections.next();
                            if (!isOperatingSection(section)) {
                                log.debug("CspTicketResearching:section: {}, availablePermits: {}", section, semaphore.availablePermits());
                                try {
                                    onEnterSection(section);
                                    if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                                        log.warn("CspTicketResearching: Ticket service initializing canceled.");
                                        onExitSection(section);
                                        break;
                                    }

                                    threadCount++;
                                    String sectionName = sectionNameFormat.format(new Date(section.getStart()));
                                    final String threadName = "INIT-CSP-" + threadCount + "-" + brandId + "-" + sectionName;
                                    log.debug("CspTicketResearching: Generate new initializing thread for {}.", section.print());
                                    new Thread(new CspTicketResearching(brandAccount, section), threadName).start();
                                    log.debug("CspTicketResearching: thread started. threadCount: {}", threadCount);
                                } catch (InterruptedException e) {
                                    log.error("", e);
                                }
                            }
                        }
                    }

                    if (!isStepComplete()) {
                        synchronized (stepLockerForMultiProcessing) {
                            try {
                                log.info("CspTicketResearching: waiting step complete");
                                stepLockerForMultiProcessing.wait();
                            } catch (InterruptedException e) {
                                log.error("CspTicketResearching: waiting step complete error: {}", e);
                            }
                        }
                    }
                }
            }

            log.info("CspTicketResearching: processing complete. stopInitializing: {}", stopInitializing);

            if (stopInitializing) {
                onInitializingCanceled();
            } else {
                log.info("CspTicketResearching: CSP ticket initialized");
                onCompleteCspResearching();
            }
        }
    }

    private void startFreshdeskToCspTicketCreating() {
        log.info("[FD > CSP] Start ticket creating... currentState:{}", currentState);
        if (canStart(InitializingState.freshdeskToCspTicketCreatingStart)) {
            onStateChanged(InitializingState.freshdeskToCspTicketCreatingStart);
            freshdeskToCspTicketCreatingCounter.setTotalTicketCount(freshdeskNewTickets.size());
            log.info("[FD > CSP] Freshdesk new ticket count: {}", freshdeskNewTickets.size());
            if (freshdeskNewTickets.size() > 0) {
                //TODO. 티켓 생성은 단일쓰레드로 등록된 순서대로 진행.
                Collections.sort(freshdeskNewTickets, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        return o1.compareTo(o2);
                    }
                });
                log.info("[FD > CSP] Not linked Freshdesk tickets. {}", StringUtils.join(freshdeskNewTickets, ","));

                if (actionOperator != null) {
                    for (String freshdeskTicketId : freshdeskNewTickets) {
                        try {
                            if (stopInitializing || !isSyncEnabled()) {
                                log.warn("[FD > CSP] Ticket service initializing canceled.");
                                onInitializingCanceled();
                                return;
                            }
                            FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId); //more ticket information. attachments element is not included ticketData result. from (freshdeskService.getTickets(CSP_NAME_IBM, date, page++).optJSONArray(FreshdeskService.KeyResults)
                            JSONObject fdTicketData = ticketResponse.getResponseBody();
                            if (stopInitializing || !isSyncEnabled()) {
                                log.warn("[FD > CSP] Ticket service initializing canceled.");
                                onInitializingCanceled();
                                return;
                            }
                            ProcessResult result = actionOperator.doCreateCspTicket(fdTicketData); //CSP 티켓 생성 후 모니터링 목록에 자동 추가됨.
                            if (result.isSuccess()) {
                                freshdeskToCspTicketCreatingCounter.onSuccessCount();
                                log.info("[FD > CSP] Freshdesk Ticket {} is created successfully on IBM side.", freshdeskTicketId);
                            } else {
                                freshdeskToCspTicketCreatingCounter.onErrorCount();
                                log.error("[FD > CSP] Failed to create freshdesk ticket {} on IBM side. {}", freshdeskTicketId, result.getErrorCauseForErrorNote());
                            }
                        } catch (Exception e) {
                            log.error("[FD > CSP] CSP ticket registering is failed. {}", e);
                        }
                    }
                }
            }
            onCompleteFreshdeskToCspTicketCreating();
        }
    }

    private void startCspToFreshdeskTicketCreating() {
        log.info("[CSP > FD] Start ticket creating... currentState:{}", currentState);
        if (canStart(InitializingState.cspToFreshdeskTicketCreatingStart)) {
            onStateChanged(InitializingState.cspToFreshdeskTicketCreatingStart);
            cspToFreshdeskTicketCreatingCounter.setTotalTicketCount(cspNewTickets.size());
            log.info("[CSP > FD] CSP new ticket count: {}", cspNewTickets.size());
            if (cspNewTickets.size() > 0) {
                //TODO. 티켓 생성은 단일쓰레드로 등록된 순서대로 진행.
                List<String> cspTicketIdList = new ArrayList<>(cspNewTickets.keySet());
                Collections.sort(cspTicketIdList, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        return o1.compareTo(o2);
                    }
                });
                log.info("[CSP > FD] Not linked CSP tickets. {}", StringUtils.join(cspTicketIdList, ","));

                if (actionOperator != null) {
                    for (String cspTicketId : cspTicketIdList) {
                        try {
                            if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                                log.warn("[CSP > FD] Ticket service initializing canceled.");
                                onInitializingCanceled();
                                return;
                            }
                            log.info("building freshdeskticket creation.");
                            FreshdeskTicketBuilder ticketBuilder = ticketHandler.buildFreshdeskTicketBuilder(cspNewTickets.get(cspTicketId), cspTicketId);
                            if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                                log.warn("[CSP > FD] Ticket service initializing canceled.");
                                onInitializingCanceled();
                                return;
                            }
                            ProcessResult result = actionOperator.doCreateFreshdeskTicket(ticketBuilder);
                            if (result.isSuccess()) {
                                cspToFreshdeskTicketCreatingCounter.onSuccessCount();
                                log.info("[CSP > FD] IBM Ticket {} is created successfully on Fresdhesk side.", ticketBuilder.getCspTicketId());
                            } else {
                                cspToFreshdeskTicketCreatingCounter.onErrorCount();
                                log.error("[CSP > FD] Failed to create IBM ticket {} on Freshdesk ticket. {}", ticketBuilder.getCspTicketId(), result.getErrorCauseForErrorNote());
                            }
                        } catch (AppInternalError e) {
                            cspToFreshdeskTicketCreatingCounter.onErrorCount();
                            log.error("[CSP > FD] Failed to build freshdesk ticket creation. {}", e);
                        }
                    }
                }
            }
            onCompleteCspToFreshdeskTicketCreating();
        }
    }

    private void stopTicketConversationSynchronizationProcessings() {
        log.info("syncOperationBreakers size: {}", conversationSyncBreakers.size());
        for (String ticketId : conversationSyncBreakers.keySet()) {
            OperationBreaker breaker = conversationSyncBreakers.get(ticketId);
            if (breaker != null) {
                log.info("ticket {} call breaker.cancel. ", ticketId);
                breaker.cancel();
            } else {
                log.info("ticket {} is no breaker", ticketId);
            }
        }
        boolean allSynchronizationsTerminated = true;
        do {
            allSynchronizationsTerminated = true;
            for (String freshdeskTicketId : conversationSyncBreakers.keySet()) {
                OperationBreaker breaker = conversationSyncBreakers.get(freshdeskTicketId);
                if (breaker != null) {
                    if (!breaker.isOperationTerminated()) {
                        log.info("Not terminated synchronizating ticket yet. {}", freshdeskTicketId);
                        allSynchronizationsTerminated = false;
                        break;
                    }
                }
            }
            if (!allSynchronizationsTerminated) {
                try {
                    log.info("wait for terminating synchronization process.");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log.error("sleep failed. {}", e);
                }
            }
        } while (!allSynchronizationsTerminated);
        log.info("All synchronization terminated.");
        conversationSyncBreakers.clear();

        if (stopInitializing || !isSyncEnabled()) {
            onInitializingCanceled();
        }
    }

    private void startTicketConversationSynchronization() {
        log.info("[FD <-> CSP] Start ticket Synchronization... currentState:{}", currentState);
        if (canStart(InitializingState.conversationSynchronizingStart)) {
            onStateChanged(InitializingState.conversationSynchronizingStart);
            List<String> ticketIdList = getMonitoringTicketIdList();
            int synchronizationTicketTotalCount = ticketIdList.size();
            log.info("[FD <-> CSP] start Conversation Synchronization: {} tickets", ticketIdList.size());
            synchronizeTicketCounter.setTotalTicketCount(synchronizationTicketTotalCount);
            if (synchronizationTicketTotalCount > 0) {
                for (int i = 0; i < synchronizationTicketTotalCount; i++) {
                    if (stopInitializing || !isSyncEnabled()) {
                        log.warn("[FD <-> CSP] Ticket Synchronization disabled.");
                        stopTicketConversationSynchronizationProcessings();
                        return;
                    }

                    try {
                        final String ticketId = ticketIdList.get(i);
                        log.info("[FD <-> CSP] synchronizing index: {}/{} - ticket: {}", i, synchronizationTicketTotalCount, ticketId);
                        checkRunnable();
                        JobScheduler.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    boolean success = false;
                                    if (actionOperator != null) {
                                        OperationBreaker breaker = new OperationBreaker();
                                        conversationSyncBreakers.put(ticketId, breaker);
                                        log.info("[FD <-> CSP] Enter Synchronizing ticketId: {}, breaker size: {}", ticketId, conversationSyncBreakers.size());
                                        ProcessResult result = actionOperator.doSynchronizeTicket(ticketId, breaker);
                                        log.info("[FD <-> CSP] Exit Synchronizing ticketId: {}, result: {}", ticketId, result);
                                        conversationSyncBreakers.remove(ticketId);
                                        success = result.isSuccess();

                                    }
                                    if (success) {
                                        synchronizeTicketCounter.onSuccessCount();
                                    } else {
                                        synchronizeTicketCounter.onErrorCount();
                                    }
                                } catch (Exception e) {
                                    synchronizeTicketCounter.onErrorCount();
                                    log.error("[FD <-> CSP] error: {}", e);
                                } finally {
                                    completeRunnable();
                                    if (stopInitializing || !isSyncEnabled()) {
                                        log.warn("[FD <-> CSP] Ticket Synchronization canceled.");
                                    } else {
                                        if (synchronizeTicketCounter.isTotalCountReached()) {
                                            onCompleteTicketConversationSynchronization();
                                        }
                                    }
                                }
                            }
                        });
                    } catch (Exception e) {
                        log.error("[FD <-> CSP] error: {}", e);
                    }
                }
            } else {
                onCompleteTicketConversationSynchronization();
            }
            log.info("[FD <-> CSP] Conversation Synchronization triggered: {} tickets", ticketIdList.size());
        }
    }

    private void checkRunnable() throws InterruptedException {
        semaphore.acquire();
    }

    private void completeRunnable() {
        semaphore.release();
    }

    private boolean isStepComplete() {
        log.debug("operating count: {}", operatingTimeSections.size());
        return (operatingTimeSections.size() == 0);
    }

    private void onInitializingCanceled() {
        log.warn("Ticket service initializing canceled.");
        //초기화 중인 모든 쓰레드가 중지되었는지 확인되어야 함.
        initializingFinishedTime = System.currentTimeMillis();

        operatingTimeSections.clear();
        freshdeskNewTickets.clear();
        cspNewTickets.clear();
        monitoringTickets.clear();
        /*
        completeRunnable();
        log.info("enter synchronized stepLockerForMultiProcessing");
        synchronized (stepLockerForMultiProcessing) {
            log.info("release waiting step.");
            stepLockerForMultiProcessing.notifyAll();
        }
        log.info("exit synchronized stepLockerForMultiProcessing");
        */
        onStateChanged(InitializingState.canceled);
    }

    private void onCompleteFreshdeskResearching() {
        log.info("Freshdesk researching totalTicketCount: {}, newTicketCount: {}", freshdeskResearchingMeta.getTotalTicketCount(), freshdeskResearchingMeta.getNewTicketCount());
        if (currentState == InitializingState.freshdeskTicketResearchingStart) {
            onStateChanged(InitializingState.freshdeskTicketResearchingComplete);
            log.debug("freshdeskNewTickets.size: {}", freshdeskNewTickets.size());
            onCompleteFreshdeskResearching(freshdeskResearchingMeta);
            if (isReverseSyncEnabled()) {
                startCspTicketResearching();
            } else {
                startFreshdeskToCspTicketCreating();
            }
        }
    }

    private void onCompleteCspResearching() {
        int totalTicketCount = 0;
        int newTicketCount = 0;
        for (String brandId : cspResearchingMetadataMap.keySet()) {
            totalTicketCount += cspResearchingMetadataMap.get(brandId).getTotalTicketCount();
            newTicketCount += cspResearchingMetadataMap.get(brandId).getNewTicketCount();
        }

        log.info("CSP researching totalTicketCount: {}, newTicketCount: {}", totalTicketCount, newTicketCount);
        if (currentState == InitializingState.cspTicketResearchingStart) {
            onCompleteCspResearching(cspResearchingMetadataMap);
            onStateChanged(InitializingState.cspTicketResearchingComplete);
            log.debug("cspNewTickets.size: {}", cspNewTickets.size());
            startFreshdeskToCspTicketCreating();
        }
    }

    private void onCompleteFreshdeskToCspTicketCreating() {
        log.info("Freshdesk ==> CSP ticket creation completed.");
        if (currentState == InitializingState.freshdeskToCspTicketCreatingStart) {
            onStateChanged(InitializingState.freshdeskToCspTicketCreatingComplete);
            if (isReverseSyncEnabled()) {
                startCspToFreshdeskTicketCreating();
            } else {
                startTicketConversationSynchronization();
            }
        }
    }

    private void onCompleteCspToFreshdeskTicketCreating() {
        log.info("CSP ==> Freshdesk ticket creation completed.");
        if (currentState == InitializingState.cspToFreshdeskTicketCreatingStart) {
            onStateChanged(InitializingState.cspToFreshdeskTicketCreatingComplete);
            startTicketConversationSynchronization();
            if (cspResearchingMetadataMap != null) {
                Map<String, TicketTimeRecord> timeRecords = new ConcurrentHashMap<>();
                for (String brandId : cspResearchingMetadataMap.keySet()) {
                    timeRecords.put(brandId, cspResearchingMetadataMap.get(brandId).getLatestTicket());
                }
                TicketSyncLogger.setReverseSyncLatestTicketTimes(timeRecords);
            }
        }
    }

    private void onCompleteTicketConversationSynchronization() {
        log.info("ticket conversation synchronization completed.");
        if (currentState == InitializingState.conversationSynchronizingStart) {
            //TicketSyncLogger.setTicketSyncTime(System.currentTimeMillis()); //disabled. setTicketSyncTime is called after each ticket is synchronized(synchronizeTicket metohd in CspTicketHandler).
            onStateChanged(InitializingState.conversationSynchronizingComplete);
            onCompleteInitializing();
        }
    }

    private void onCompleteInitializing() {
        log.info("currentState:{}", currentState);
        if (currentState == InitializingState.conversationSynchronizingComplete) {
            log.info("\n\n\n=============================\nTicket Service Initializing Completed.\n=============================\n\n\n");
            initializingFinishedTime = System.currentTimeMillis();
            operatingTimeSections.clear();
            freshdeskNewTickets.clear();
            cspNewTickets.clear();
            monitoringTickets.clear();
            onStateChanged(InitializingState.initialized);
        }
    }

    public InitializingState getState() {
        log.debug("currentState:{}", currentState);
        return currentState;
    }

    public boolean isComplete() {
        log.debug("currentState:{}", currentState);
        return (currentState == InitializingState.conversationSynchronizingComplete) || (currentState == InitializingState.initialized);
    }

    public boolean isCanceled() {
        log.debug("currentState:{}", currentState);
        return (currentState == InitializingState.canceled);
    }

    public boolean isAllOperationsTerminated() {
        return (isComplete() || isCanceled());
    }

    private class FreshdeskTicketResearching implements Runnable {
        private TimeSection timeSection;

        private FreshdeskTicketResearching(TimeSection section) {
            timeSection = section;
        }

        @Override
        public void run() {
            log.info("[FD Researching] start. section:{}", timeSection.print());
            //Freshdesk 티켓 조회(동기화 대상 기간에 포함되는 티켓만)
            //	escalation된 티켓이지만 아직 정방향 티켓 생성이 안된 경우 티켓 생성 후 동기화 목록에 추가.
            //	escalation된 티켓이고, 이미 정방향 티켓이 생성되었고 오픈되어 있는 경우 동기화 목록에 추가.
            //	escalation된 티켓이고, 이미 정방향 티켓이 생성되었고 종료된 경우 CSP 티켓의 변경사항을 체크만 함.
            //		(AWS에 티켓이 재오픈되어 있는 경우 다시 오픈하고 동기화 목록에 추가하고, 종료된 상태이면 추가된 대화가 있는지만 확인/동기화하고 동기화 목록에는 포함 안함.)
            final FreshdeskTicketLoader loader = FreshdeskTicketLoader.byDay(AppConstants.CSP_NAME, new Date(timeSection.getStart()), TicketStatus.all);
            int loadedTicketCount = 0;
            int newTicketCount = 0;
            long ticketResearchingPeriodEndTime;
            TicketTimeRecord latestTicket = new TicketTimeRecord();

            if (timeSection.hasEndTime()) {
                ticketResearchingPeriodEndTime = timeSection.getEnd();
            } else {
                ticketResearchingPeriodEndTime = System.currentTimeMillis();
            }

            freshdeskResearchingMeta.onResearchingStarted(timeSection);

            while (loader.hasNext()) {
                JSONArray ticketArray = loader.next();
                loadedTicketCount += ticketArray.length();
                log.debug("[FD Researching] {} - loadedTicketCount:{}", timeSection.print(), loadedTicketCount);
                for (int i = 0; i < ticketArray.length(); i++) {
                    try {
                        if (stopInitializing || !isSyncEnabled()) {
                            log.warn("[FD Researching] stop researching. timeSection: {}", timeSection.print());
                            onExitSection(timeSection);
                            return;
                        }
                        JSONObject fdTicketData = ticketArray.getJSONObject(i);
                        int fdTicketStatus = fdTicketData.optInt(FreshdeskTicketField.Status);
                        log.debug("[FD Researching] Freshdesk ticket : {} - createdTime:{}", fdTicketData.optString(FreshdeskTicketField.Id), fdTicketData.optString(FreshdeskTicketField.CreatedAt));
                        checkAndReplaceBrandEmail(fdTicketData);
                        TicketMetadata ticketMetadata = TicketMetadata.build(fdTicketData, false);
                        //If ticketObject is not null, ticket is escalated.
                        if (ticketMetadata != null) {
                            String freshdeskTicketId = ticketMetadata.getFreshdeskTicketId();
                            String cspTicketId = ticketMetadata.getCspTicketId();
                            onFoundTicketId(freshdeskTicketId, cspTicketId);

                            if (!latestTicket.isNewerThan(ticketMetadata.getFreshdeskCreatedTime())) {
                                latestTicket.setTicketId(freshdeskTicketId);
                                latestTicket.setCreateTime(ticketMetadata.getFreshdeskCreatedTime());
                            }

                            if (isBetaTestEnabled()) {
                                if (!isBetaTester(ticketMetadata.getCspAccountEmail())) {
                                    log.debug("[FD Researching] This ticket is not beta testers' ticket. skipped. {}", fdTicketData.optString(FreshdeskTicketField.Id));
                                    continue;
                                }
                            }
                            log.debug("[FD Researching] fdTicketId: {} - cspTicketId: {} - status: {}", freshdeskTicketId, cspTicketId, fdTicketStatus);
                            //cspTicketId가 있는 경우 모두 동기화 목록에 넣어서 동기화 시도. (종료된 티켓도 한번은 동기화 체크하도록)
                            //Freshdesk에 종료된 티켓이 CSP에서 재오픈 된 경우에만 Freshdesk 티켓을 오픈 상태로 변경하는 것만 여기에서 처리.
                            if (FreshdeskTicketStatus.isClosed(fdTicketStatus)) { //SP에 종료된 티켓.
                                if (cspTicketId != null) {
                                    //종료된 티켓이지만 CSP에 티켓이 생성된 경우 티켓 내용 변경사항 체크를 위해 동기화 목록에 추가.
                                    //동기화 후 티켓 종료 상태에 의해 동기화 목록에서 삭제됨.
                                    //	escalation된 티켓이고, 이미 정방향 티켓이 생성되었고 종료된 경우 CSP 티켓의 변경사항을 체크만 함. (한번만 동기화. 종료된 티켓이므로 동기화 후 목록에 삭제됨.)
                                    //		(CSP에 티켓이 재오픈되어 있는 경우 다시 오픈하고 동기화 목록에 추가하고, 종료된 상태이면 추가된 대화가 있는지만 확인/동기화하고 동기화 목록에는 포함 안함.)
                                    CloudZCspApiInfo cspApiInfo = getCspApiInfo(ticketMetadata.getCspAccountEmail(), ticketMetadata.getCspAccountId());
                                    if (cspApiInfo != null && cspApiInfo.isAvailable()) {
                                        Ticket ibmTicket = IbmService.getTicket(cspApiInfo, Long.valueOf(cspTicketId));
                                        if (ibmTicket != null) {
                                            if (ibmTicket.getStatus() != null) {
                                                IbmTicketStatus ibmTicketStatus = IbmTicketStatus.valueOf(ibmTicket.getStatus().getName());
                                                log.info("[FD Researching] CSP ticket status. {}", ibmTicketStatus);
                                                if (ibmTicketStatus.isOpen() || ibmTicketStatus.isAssigned()) {
                                                    try {
                                                        log.info("[FD Researching] CSP Ticket is not closed. Reopen Freshdesk ticket {}.", freshdeskTicketId);
                                                        FreshdeskService.changeTicketStatus(freshdeskTicketId, FreshdeskTicketStatus.Open);
                                                    } catch (AppInternalError e) {
                                                        log.error(TicketUtil.freshdeskErrorText("[FD Researching] {} cannot change ticket status to open. {}"), freshdeskTicketId, e);
                                                    }
                                                }
                                            }
                                            //종료된 티켓도 동기화가 필요하다는 요청이 있으면 활성화할 예정.
                                            //onFoundMonitoringTicket(ticketObject);
                                        }
                                    } else {
                                        log.error("Not found available csp account {} of ticket {}.", ticketMetadata.getCspAccountId(), freshdeskTicketId);
                                    }
                                } else {
                                    //정방향 티켓(CSP)이 생성 안된 상태에서 Freshdesk에서 이미 종료된 티켓이라면 CSP에 티켓 생성 안함.
                                    log.debug("[FD Researching] Skips synchronization because Freshdesk ticket {} were closed before the tickets were registered into CSP side.", freshdeskTicketId);
                                }
                            } else if (FreshdeskTicketStatus.isOpen(fdTicketStatus)) { //SP에 Open 상태로 존재하는 티켓 (모니터링 대상 티켓)
                                if (cspTicketId == null) {
                                    log.info("[FD Researching] Not linked ticket. Freshdesk {} ==> CSP.", freshdeskTicketId);
                                    //CSP에 새로 생성해야할 티켓 목록에 추가.
                                    if (!config.isIgnoreTicketCreationByFreshdeskTicketId(freshdeskTicketId)) {
                                        if (!freshdeskNewTickets.contains(freshdeskTicketId)) {
                                            freshdeskNewTickets.add(freshdeskTicketId);
                                            newTicketCount++;
                                        }
                                    } else {
                                        log.info("[FD Researching] Freshdesk ticket {} ==> IBM ticket creation is ignored. This ticket exists ignore ticket list.", freshdeskTicketId);
                                    }
                                } else {
                                    onFoundMonitoringTicket(ticketMetadata);
                                }
                            }
                        } else {
                            log.error("[FD Researching] TicketMetadata building failed. ticket : {}", fdTicketData.optString(FreshdeskTicketField.Id));
                        }
                    } catch (JSONException | IllegalArgumentException e) {
                        log.error("[FD Researching] Freshdesk ticket initializing failed. sectoin: {} - error:{}", timeSection.print(), e);
                    }
                }
            }

            log.info("loader.getTotalTicketCount(): {}", loader.getTotalTicketCount());
            if (loader.getTotalTicketCount() > FreshdeskService.TICKET_SEARCH_ITEMS_TOTAL_MAX) {
                log.info("{} has total {} tickets. Cannot read more than {} tickets. Attempt to read opened ticket for monitoring.", timeSection.print(), loader.getTotalTicketCount(), FreshdeskService.TICKET_SEARCH_ITEMS_TOTAL_MAX);
                final FreshdeskTicketLoader openTicketLoader = FreshdeskTicketLoader.byDay(AppConstants.CSP_NAME, new Date(timeSection.getStart()), TicketStatus.opened);
                while (openTicketLoader.hasNext()) {
                    JSONArray ticketArray = openTicketLoader.next();
                    log.debug("[FD Researching] loading opened ticket. {}", timeSection.print());
                    for (int i = 0; i < ticketArray.length(); i++) {
                        try {
                            if (stopInitializing || !isSyncEnabled()) {
                                log.warn("[FD Researching] stop researching. timeSection: {}", timeSection.print());
                                onExitSection(timeSection);
                                return;
                            }
                            JSONObject fdTicketData = ticketArray.getJSONObject(i);
                            int fdTicketStatus = fdTicketData.optInt(FreshdeskTicketField.Status);
                            log.debug("[FD Researching] Freshdesk ticket : {} - createdTime:{}", fdTicketData.optString(FreshdeskTicketField.Id), fdTicketData.optString(FreshdeskTicketField.CreatedAt));
                            checkAndReplaceBrandEmail(fdTicketData);
                            TicketMetadata ticketMetadata = TicketMetadata.build(fdTicketData, false);
                            //If ticketObject is not null, ticket is escalated.
                            if (ticketMetadata != null) {
                                String freshdeskTicketId = ticketMetadata.getFreshdeskTicketId();
                                String cspTicketId = ticketMetadata.getCspTicketId();
                                onFoundTicketId(freshdeskTicketId, cspTicketId);

                                if (!latestTicket.isNewerThan(ticketMetadata.getFreshdeskCreatedTime())) {
                                    latestTicket.setTicketId(freshdeskTicketId);
                                    latestTicket.setCreateTime(ticketMetadata.getFreshdeskCreatedTime());
                                }

                                if (isBetaTestEnabled()) {
                                    if (!isBetaTester(ticketMetadata.getCspAccountEmail())) {
                                        log.debug("[FD Researching] This ticket is not beta testers' ticket. skipped. {}", fdTicketData.optString(FreshdeskTicketField.Id));
                                        continue;
                                    }
                                }
                                log.debug("[FD Researching] fdTicketId: {} - cspTicketId: {} - status: {}", freshdeskTicketId, cspTicketId, fdTicketStatus);
                                //cspTicketId가 있는 경우 모두 동기화 목록에 넣어서 동기화 시도. (종료된 티켓도 한번은 동기화 체크하도록)
                                //Freshdesk에 종료된 티켓이 CSP에서 재오픈 된 경우에만 Freshdesk 티켓을 오픈 상태로 변경하는 것만 여기에서 처리.
                                if (FreshdeskTicketStatus.isOpen(fdTicketStatus)) { //SP에 Open 상태로 존재하는 티켓 (모니터링 대상 티켓)
                                    if (cspTicketId == null) {
                                        log.info("[FD Researching] Not linked ticket. Freshdesk {} ==> CSP.", freshdeskTicketId);
                                        //CSP에 새로 생성해야할 티켓 목록에 추가.
                                        if (!config.isIgnoreTicketCreationByFreshdeskTicketId(freshdeskTicketId)) {
                                            if (!freshdeskNewTickets.contains(freshdeskTicketId)) {
                                                freshdeskNewTickets.add(freshdeskTicketId);
                                                newTicketCount++;
                                            }
                                        } else {
                                            log.info("[FD Researching] Freshdesk ticket {} ==> IBM ticket creation is ignored. This ticket exists ignore ticket list.", freshdeskTicketId);
                                        }
                                    } else {
                                        onFoundMonitoringTicket(ticketMetadata);
                                    }
                                }
                            } else {
                                log.error("[FD Researching] TicketMetadata building failed. ticket : {}", fdTicketData.optString(FreshdeskTicketField.Id));
                            }
                        } catch (JSONException | IllegalArgumentException e) {
                            log.error("[FD Researching] Freshdesk ticket initializing failed. sectoin: {} - error:{}", timeSection.print(), e);
                        }
                    }
                }
            }

            log.info("[FD Researching] end. timeSection:{}, loadedTicketCount: {}, ticketResearchingPeriodEndTime: {}, latestTicket: {}", timeSection.print(), loadedTicketCount, ticketResearchingPeriodEndTime, latestTicket);
            freshdeskResearchingMeta.onCompletedSectionEndTime(ticketResearchingPeriodEndTime);
            freshdeskResearchingMeta.onLoadedTicketCount(loadedTicketCount);
            freshdeskResearchingMeta.onFoundNewTicketCount(newTicketCount);
            freshdeskResearchingMeta.onFoundLatestTicket(latestTicket);
            freshdeskResearchingMeta.onResearchingFinished(timeSection);
            onExitSection(timeSection);
        }
    }

    private class CspTicketResearching implements Runnable {
        private TimeSection timeSection;
        private IbmBrandAccount brandAccount;

        private CspTicketResearching(IbmBrandAccount account, TimeSection section) {
            this.timeSection = section;
            this.brandAccount = account;
        }

        @Override
        public void run() {
            if (brandAccount == null || timeSection == null) {
                log.warn("[CSP Researching] stop researching. invalid brand: {} or timesection: {}", brandAccount, timeSection);
                onExitSection(timeSection);
                return;
            }
            String brandId = brandAccount.getBrandId();
            log.info("[CSP Researching] start. brand: {}, section:{}", brandId, timeSection.print());
            if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                log.warn("[CSP Researching] stop researching. timeSection: {}", timeSection.print());
                onExitSection(timeSection);
                return;
            }
            if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                log.warn("[CSP Researching] stop researching. timeSection: {}", timeSection.print());
                onExitSection(timeSection);
                return;
            }

            Date filterTimeStart = new Date(timeSection.getStart());
            IbmTicketLoader ticketLoader;
            if (timeSection.hasEndTime()) {
                Date filterTimeEnd = new Date(timeSection.getEnd());
                ticketLoader = IbmTicketLoader.betweenTime(brandAccount, filterTimeStart, filterTimeEnd, false);
            } else {
                ticketLoader = IbmTicketLoader.afterTime(brandAccount, filterTimeStart, false);
            }

            int loadedTicketCount = 0;
            int newTicketCount = 0;
            long ticketResearchingPeriodEndTime;
            TicketTimeRecord latestTicket = new TicketTimeRecord();
            if (timeSection.hasEndTime()) {
                ticketResearchingPeriodEndTime = timeSection.getEnd();
            } else {
                ticketResearchingPeriodEndTime = System.currentTimeMillis();
            }

            ResearchingMetadata brandResearchingMeta = cspResearchingMetadataMap.get(brandId);
            brandResearchingMeta.onResearchingStarted(timeSection);

            while (ticketLoader.hasNext()) {
                List<Ticket> ibmBrandTickets = ticketLoader.next();
                loadedTicketCount += ibmBrandTickets.size();
                log.info("[CSP Researching] {} - brand {}, loadedTicketCount:{}", timeSection.print(), brandId, loadedTicketCount);
                ApiClient ibmClient = brandAccount.buildApiClient();
                for (Ticket ticket : ibmBrandTickets) {
                    long createdTimeMillis = ticket.getCreateDate().getTimeInMillis();
                    String cspTicketId = ticket.getId().toString();
                    //String timeString = Util.getLocalTimeString(ticket.getCreateDate().getTime(), AppConstants.LOCAL_TIME_FORMAT, AppConstants.LOCAL_TIME_ZONE_ID);
                    if (stopInitializing || !isSyncEnabled() || !isReverseSyncEnabled()) {
                        log.warn("[CSP Researching] stop researching. timeSection: {}", timeSection.print());
                        onExitSection(timeSection);
                        return;
                    }
                    if (!latestTicket.isNewerThan(createdTimeMillis)) {
                        latestTicket.setTicketId(cspTicketId);
                        latestTicket.setCreateTime(createdTimeMillis);
                    }
                    if (!isLinkedCspTicketId(cspTicketId)) {
                        log.info("[CSP Researching] Not linked ticket. IBM {} - {} - {} ==> Freshdesk.", cspTicketId, ticket.getServiceProviderResourceId(), ticket.getTitle());
                        if (!config.isIgnoreTicketCreationByCspTicketId(cspTicketId)) {
                            if (config.isIgnoreTicketCreationByCspTicketTitleEnabled()) {
                                try {
                                    Ticket.Service ticketService = Ticket.service(ibmClient, ticket.getId());
                                    //Ticket ticketDetails = ticketService.getObject();
                                    Update firstUpdate = ticketService.getFirstUpdate();
                                    if (firstUpdate != null) {
                                        String editorType = firstUpdate.getEditorType();
                                        String ticketTitle = TicketUtil.buildIbmTicketTitle(editorType, ticket.getTitle());
                                        log.info("cspTicketId:{}, editorType:{}, ticketTitle: {}", cspTicketId, editorType, ticketTitle);
                                        if (!config.isIgnoreTicketCreationByCspTicketTitle(ticketTitle)) {
                                            if (canCreateFreshdeskTicket(brandAccount, ticket)) {
                                                log.info("[CSP Researching] IBM ticket {} can create ticket on Freshdesk.", cspTicketId);
                                                //Freshdesk에 새로 생성해야할 티켓 목록에 추가.
                                                cspNewTickets.put(cspTicketId, brandAccount);
                                                newTicketCount++;
                                            } else {
                                                log.info("[CSP Researching] IBM ticket {} cannot create ticket on Freshdesk.", cspTicketId);
                                            }
                                        } else {
                                            log.info("[CSP Researching] IBM ticket {} ==> Freshdesk ticket creation is ignored. This ticket has not allowed title '{}'.", cspTicketId, ticketTitle);
                                        }
                                    } else {
                                        log.info("[CSP Researching] IBM ticket {} ==> Freshdesk ticket creation is ignored. This ticket has invalid ticket body.", cspTicketId);
                                    }
                                    //} catch (com.softlayer.api.ApiException e) {
                                } catch (Exception e) {
                                    log.error("[CSP Researching] Failed to check ibm ticket title. {}", e);
                                }
                            } else {
                                if (canCreateFreshdeskTicket(brandAccount, ticket)) {
                                    log.info("[CSP Researching] IBM ticket {} can create ticket on Freshdesk.", cspTicketId);
                                    //Freshdesk에 새로 생성해야할 티켓 목록에 추가.
                                    cspNewTickets.put(cspTicketId, brandAccount);
                                    newTicketCount++;
                                } else {
                                    log.info("[CSP Researching] IBM ticket {} cannot create ticket on Freshdesk.", cspTicketId);
                                }
                            }
                        } else {
                            log.info("[CSP Researching] IBM ticket {} ==> Freshdesk ticket creation is ignored. This ticket exists ignore ticket list.", cspTicketId);
                        }
                    } else {
                        //Freshdesk ticket과 매칭이 된 티켓은 Freshdesk researching 과정에서 모니터링 목록에 추가된 상태임.
                        log.info("[CSP Researching] Skip ticket creating IBM ==> Freshdesk. already exists. {} - {} ", cspTicketId, ticket.getTitle());
                    }
                }
            }
            log.info("[CSP Researching] end. brand:{}, timeSection:{}, loadedTicketCount: {}, ticketResearchingPeriodEndTime: {},lastTicket: {}", brandId, timeSection.print(), loadedTicketCount, ticketResearchingPeriodEndTime, latestTicket);

            brandResearchingMeta.onCompletedSectionEndTime(ticketResearchingPeriodEndTime);
            brandResearchingMeta.onLoadedTicketCount(loadedTicketCount);
            brandResearchingMeta.onFoundNewTicketCount(newTicketCount);
            brandResearchingMeta.onFoundLatestTicket(latestTicket);
            brandResearchingMeta.onResearchingFinished(timeSection);
            onExitSection(timeSection);
        }
    }

    private Date toDate(long timeMillis) {
        if (timeMillis > 0) {
            return new Date(timeMillis);
        }
        return null;
    }

    public InitializeReportMetadata buildReportMeta() {
        InitializeReportMetadata meta = new InitializeReportMetadata();
        meta.setState(currentState);
        meta.setFreshdeskResearchingMeta(freshdeskResearchingMeta);
        meta.setCspResearchingMetadataMap(cspResearchingMetadataMap);
        meta.setFreshdeskToCspTicketCreation(freshdeskToCspTicketCreatingCounter);
        meta.setCspToFreshdeskTicketCreation(cspToFreshdeskTicketCreatingCounter);
        meta.setTicketSynchronization(synchronizeTicketCounter);
        meta.setInitializingStartedTime(initializingStartedTime);
        meta.setInitializingFinishedTime(initializingFinishedTime);
        return meta;
    }

    public JSONObject exportReport() {
        JSONObject report = new JSONObject();
        try {
            report.put("FreshdeskResearching", freshdeskResearchingMeta.exportReport());
        } catch (JsonProcessingException e) {
            log.error("freshdeskResearchingMeta serializing error. {}", e);
        }

        if (initializingStartedTime > 0) {
            String operatingStart = TicketUtil.getLocalTimeString(new Date(initializingStartedTime));
            report.put("InitializingStartTime", operatingStart);
        } else {
            report.put("InitializingStartTime", "Not Started");
        }
        if (initializingFinishedTime > 0) {
            String operatingEnd = TicketUtil.getLocalTimeString(new Date(initializingFinishedTime));
            report.put("InitializingEndTime", operatingEnd);
        } else {
            report.put("InitializingEndTime", "Operating");
        }

        if ((getCurrentState() == TicketServiceInitializer.InitializingState.freshdeskTicketResearchingStart)
                || (getCurrentState() == TicketServiceInitializer.InitializingState.cspTicketResearchingStart)) {
            report.put("InitializingOperatingTimeSections", getOperatingTimeSections());
        }

        report.put("InitializingStepCurrent", getCurrentState().name());
        report.put("InitializingStepSequences", getAllInitializingSteps());
        report.put("InitializingThreadCountMax", config.getTicketSyncConcurrentMax());

        try {
            report.put("FreshdeskToCspCreation", freshdeskToCspTicketCreatingCounter.export());
        } catch (JsonProcessingException e) {
            log.error("freshdeskToCspTicketCreatingCounter serializing error. {}", e);
        }

        if (isReverseSyncEnabled()) {
            try {
                JSONObject cspResearching = new JSONObject();
                for (String brandId : cspResearchingMetadataMap.keySet()) {
                    ResearchingMetadata metadata = cspResearchingMetadataMap.get(brandId);
                    cspResearching.put(brandId, metadata.exportReport());
                }
                report.put("CspResearching", cspResearching);
            } catch (JsonProcessingException e) {
                log.error("cspResearchingMetadataMap serializing error. {}", e);
            }
            try {
                report.put("CspToFreshdeskCreation", cspToFreshdeskTicketCreatingCounter.export());
            } catch (JsonProcessingException e) {
                log.error("cspToFreshdeskTicketCreatingCounter serializing error. {}", e);
            }
        }

        try {
            report.put("TicketSynchronization", synchronizeTicketCounter.export());
        } catch (JsonProcessingException e) {
            log.error("synchronizeTicketCounter serializing error. {}", e);
        }

        if (latestTriggeredTimeSection != null) {
            report.put("LatestTriggeredTimeSection", latestTriggeredTimeSection.print());
        }

        if (isComplete()) {
            long operatingTime = initializingFinishedTime - initializingStartedTime;
            operatingTime = operatingTime / 1000;
            if (operatingTime > 3600) {
                String duration = String.format("%dh %dm %ds", (operatingTime / 3600), ((operatingTime % 3600) / 60), (operatingTime % 60));
                report.put("InitializingDuration", duration);
            } else {
                String duration = String.format("%dm %ds", (operatingTime / 60), (operatingTime % 60));
                report.put("InitializingDuration", duration);
            }
        }
        return report;
    }
}