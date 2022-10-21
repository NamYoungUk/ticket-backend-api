package com.sk.bds.ticket.api.service;

import com.ifountain.opsgenie.client.swagger.ApiException;
import com.ifountain.opsgenie.client.swagger.model.SuccessResponse;
import com.sk.bds.ticket.api.Application;
import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.freshdesk.*;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.exception.AppInternalErrorNote;
import com.sk.bds.ticket.api.exception.AppInternalErrorReason;
import com.sk.bds.ticket.api.response.AppResponse;
import com.sk.bds.ticket.api.util.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class TicketService implements TicketOperator {
    @Autowired
    BuildProperties buildProperties;
    CspTicketHandler ticketHandler;
    ServiceStatus serviceStatus;
    TicketRegistry ticketRegistry;
    TicketServiceInitializer ticketServiceInitializer = null;
    TicketServiceInitializer.InitializeReportMetadata ticketServiceInitializeReportMeta = null;
    JSONObject ticketServiceInitializingCompleteReport = null;
    //DateFormat freshdeskTimeFormat;
    DateFormat localTimeFormat;

    final AppConfig config = AppConfig.getInstance();

    /**
     * Ticket Service 초기화
     */
    @PostConstruct
    public void init() {
        log.info("Ticket service instance created.");
        ticketHandler = new IbmService(this);
        ticketRegistry = TicketRegistry.getInstance();
        //freshdeskTimeFormat = TicketUtil.getFreshdeskDateFormat();
        localTimeFormat = TicketUtil.getLocalDateFormat();
        checkDeployInfo();
        initServiceStatus();
        startTicketServiceInitializing();
    }

    private void initServiceStatus() {
        if (serviceStatus == null) {
            serviceStatus = new ServiceStatus();
        }
        serviceStatus.setDeployInfo(getDeployInfo());
        serviceStatus.setStage(config.getServiceStage());
    }

    private boolean isSyncEnabled() {
        return config.isSyncEnabled();
    }

    private boolean isReverseSyncEnabled() {
        return config.isReverseSyncEnabled();
    }

    private long getTicketSyncTargetTime() {
        return config.getTicketSyncTargetTime();
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

    public boolean isLocalStage() {
        return config.isLocalStage();
    }

    public boolean isStagingStage() {
        return config.isStagingStage();
    }

    public boolean isProductionStage() {
        return config.isProductionStage();
    }

    private void checkDeployInfo() {
        AppDeployInfo deployInfo;
        try {
            deployInfo = readServiceDeployInfo();
        } catch (IOException e) {
            log.warn("create deploy information firstly.");
            deployInfo = buildDeployInfo(buildProperties);
            writeServiceDeployInfo(deployInfo);
        }
        log.info("IBM ticket service is deployed {}", deployInfo);

        long deployedTime = deployInfo.getDeployTime();
        if (buildProperties.getTime().toEpochMilli() != deployInfo.getBuildTime()) {
            log.info("Different build Time. change Deploy Information");
            deployInfo = buildDeployInfo(buildProperties);
            deployInfo.setDeployTime(deployedTime);
            writeServiceDeployInfo(deployInfo);
        }
    }

    private AppDeployInfo getDeployInfo() {
        try {
            return readServiceDeployInfo();
        } catch (IOException e) {
            log.warn("cannot read deploy information. {}", e);
        }
        return buildDeployInfo(buildProperties);
    }

    /**
     * 티켓 서비스 빌드 정보를 배포 정보로 변환.
     *
     * @param properties
     */
    private AppDeployInfo buildDeployInfo(BuildProperties properties) {
        AppDeployInfo deployInfo = new AppDeployInfo();
        deployInfo.setDeployTime(System.currentTimeMillis());
        deployInfo.setAppName(properties.getName());
        deployInfo.setAppVersion(properties.getVersion());
        deployInfo.setBuildTime(properties.getTime().toEpochMilli());
        return deployInfo;
    }

    /**
     * 저장된 티켓 서비스 배포 정보 파일로 부터 배포 정보로 변환
     *
     * @throws IOException
     */
    private AppDeployInfo readServiceDeployInfo() throws IOException {
        AppDeployInfo deployInfo;
        String jsonText = Util.readFile(config.getServiceDeployInfoFile());
        deployInfo = JsonUtil.unmarshal(jsonText, AppDeployInfo.class);
        return deployInfo;
    }

    /**
     * 티켓 서비스 배포 정보를 파일로 저장.
     *
     * @param deployInfo
     */
    private void writeServiceDeployInfo(AppDeployInfo deployInfo) {
        try {
            log.info("Write IBM ticket service deploy information. {}", deployInfo);
            String jsonText = JsonUtil.marshal(deployInfo);
            //Util.deleteFile(config.getServiceDeployInfoFile());
            Util.writeFile(config.getServiceDeployInfoFile(), jsonText);
        } catch (IOException e) {
            Util.ignoreException(e);
        }
    }

    //동기화 목록에 넣기 전에만 체크. 동기화 목록에 넣은 후에는 체크하지 않음.
    //동기화 목록에 들어간 뒤에 CSP Account와 csp case id가 변경되는 현상 있어서 조치 필요.
    private TicketSyncCondition checkTicketSyncCondition(JSONObject freshdeskTicketData) {
        if (freshdeskTicketData != null && freshdeskTicketData.has(FreshdeskTicketField.CustomFields)) {
            JSONObject customData = freshdeskTicketData.optJSONObject(FreshdeskTicketField.CustomFields);
            String csp = customData.optString(FreshdeskTicketField.CfCsp);
            if (!TicketUtil.isValidCsp(csp)) {
                log.error("Ticket ticket {} is not IBM Ticket.", freshdeskTicketData.optString(FreshdeskTicketField.Id));
                return TicketSyncCondition.invalidCsp;
            }
            FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(customData.optString(FreshdeskTicketField.CfCspAccount));
            if (!accountField.isValid()) {
                log.error("This ticket has invalid account field. {}", freshdeskTicketData.optString(FreshdeskTicketField.Id));
                return TicketSyncCondition.invalidCspAccount;
            }
            if (isBetaTestEnabled()) {
                if (!isBetaTester(accountField.getEmail())) {
                    log.error("This ticket is not beta testers' ticket. skipped. {}", freshdeskTicketData.optString(FreshdeskTicketField.Id));
                    return TicketSyncCondition.notBetaTester;
                }
            }

            String createAt = freshdeskTicketData.optString(FreshdeskTicketField.CreatedAt);
            long ticketCreatedTime = TicketUtil.getTimeByFreshdeskTime(createAt);
            long limitTime = getTicketSyncTargetTime();
            if (ticketCreatedTime < limitTime) {
                log.error("This ticket was created before the sync limit time. {}", freshdeskTicketData.optString(FreshdeskTicketField.Id));
                return TicketSyncCondition.outOfSyncTimeRange;
            }
            //return (ticketCreatedTime >= limitTime);
            return TicketSyncCondition.syncable;
        }
        return TicketSyncCondition.emptyTicketData;
    }

    public boolean isServiceInitialized() {
        if (ticketServiceInitializeReportMeta != null) {
            return ticketServiceInitializeReportMeta.isInitializeComplete();
        }
        //return serviceStatus.isServiceInitialized();
        return false;
    }

    private boolean isRunningTicketServiceInitializer() {
        return (ticketServiceInitializer != null);
    }

    private void stopTicketServiceInitializing(boolean waitTerminate) {
        log.info("waitTerminate: {}", waitTerminate);
        if (ticketServiceInitializer != null) {
            log.info("@@@@@ STOP TICKET SERVICE INITIALIZING...@@@@@");
            ticketServiceInitializer.stopTicketInitializing();
            if (waitTerminate) {
                while (ticketServiceInitializer != null && !ticketServiceInitializer.isAllOperationsTerminated()) {
                    try {
                        log.info("waiting for initializer terminating.");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        log.error("error : {}", e);
                    }
                }
            }
            log.info("@@@@@ TICKET SERVICE INITIALIZING STOPPED. @@@@@");
            ticketServiceInitializer = null;
        }
    }

    private void startTicketServiceInitializing() {
        log.info("");
        if (!isSyncEnabled()) {
            log.error("Ticket synchronization configuration is disabled.");
            return;
        }
        if (isRunningTicketServiceInitializer()) {
            log.error("Ticket service initializing is running already.");
            return;
        }

        ticketRegistry.clear();
        TicketSyncLogger.clearTicketSyncTime();
        //역방향 동기화 시간 기록 유지.
        //티켓 서비스 초기화시 마지막 동기화 시간 이후의 티켓만 조회하여 초기화함.
        //TicketSyncLogger.clearReverseSyncLastTicketTime();
        JobScheduler.execute(new Runnable() {
            @Override
            public void run() {
                ticketServiceInitializer = new TicketServiceInitializer(ticketHandler, new TicketServiceInitializer.ActionOperator() {
                    @Override
                    public void onStateChanged(TicketServiceInitializer.InitializingState state) {
                        log.info("state: {}", state);
                        final long now = System.currentTimeMillis();
                        switch (state) {
                            case freshdeskTicketResearchingStart:
                                serviceStatus.setInitializedTime(0);
                                ticketServiceInitializeReportMeta = ticketServiceInitializer.buildReportMeta();
                                ticketServiceInitializingCompleteReport = ticketServiceInitializer.exportReport();
                                break;
                            case cspTicketResearchingStart:
                            case freshdeskToCspTicketCreatingStart:
                            case cspToFreshdeskTicketCreatingStart:
                            case conversationSynchronizingStart:
                                ticketServiceInitializeReportMeta = ticketServiceInitializer.buildReportMeta();
                                ticketServiceInitializingCompleteReport = ticketServiceInitializer.exportReport();
                                break;
                            case initialized:
                                log.info("@@@@@ Ticket service initializing completed. @@@@@");
                                serviceStatus.setInitializedTime(now);
                                ticketServiceInitializeReportMeta = ticketServiceInitializer.buildReportMeta();
                                ticketServiceInitializingCompleteReport = ticketServiceInitializer.exportReport();

                                //티켓 동기화 스케쥴은 기본 비활성화.
                                /*if (ticketServiceInitializeReportMeta.getInitializingStartedTime() + config.getTicketSyncInterval() > now) {
                                    long delayMillis = (ticketServiceInitializeReportMeta.getInitializingStartedTime() + config.getTicketSyncInterval()) - now;
                                    startTicketSyncSchedule(delayMillis);
                                } else {
                                    long delayMillis = 5000;
                                    startTicketSyncSchedule(delayMillis);
                                }*/
                                startTicketWorkingThread();
                                ticketServiceInitializer = null;
                                break;
                            case canceled:
                                log.info("@@@@@ Ticket service initializing canceled. @@@@@");
                                ticketServiceInitializeReportMeta = ticketServiceInitializer.buildReportMeta();
                                ticketServiceInitializingCompleteReport = ticketServiceInitializer.exportReport();
                                ticketServiceInitializer = null;
                                break;
                        }
                    }

                    @Override
                    public void onFoundTicketId(String freshdeskTicketId, String cspTicketId) {
                        log.info("freshdeskTicketId:{}, cspTicketId:{}", freshdeskTicketId, cspTicketId);
                        if (freshdeskTicketId != null && cspTicketId != null) {
                            onLinkedTicketId(freshdeskTicketId, cspTicketId);
                        }
                    }

                    @Override
                    public void onFoundMonitoringTicket(String freshdeskTicketId, TicketMetadata ticketMetadata) {
                        log.info("ticketMetadata:{}", ticketMetadata);
                        if (ticketMetadata != null) {
                            addMonitoringTicket(freshdeskTicketId, ticketMetadata);
                        }
                    }

                    @Override
                    public void onCompleteFreshdeskResearching(TicketServiceInitializer.ResearchingMetadata researchingMetadata) {
                        log.info("researchingMetadata:{}", researchingMetadata);
                    }

                    @Override
                    public void onCompleteCspResearching(Map<String, TicketServiceInitializer.ResearchingMetadata> researchingMetadataMap) {
                        log.info("researchingMetadataMap:{}", researchingMetadataMap);
                    }

                    @Override
                    public ProcessResult doCreateCspTicket(JSONObject freshdeskTicketData) {
                        log.info("freshdeskTicketData:{}", freshdeskTicketData);
                        return ticketHandler.createCspTicket(freshdeskTicketData);
                    }

                    @Override
                    public ProcessResult doCreateFreshdeskTicket(FreshdeskTicketBuilder ticketBuilder) {
                        log.info("ticketCreation:{}", ticketBuilder);
                        if (ticketBuilder != null) {
                            return createFreshdeskTicket(ticketBuilder);
                        }
                        log.error("ticketCreation is null");
                        ProcessResult result = ProcessResult.base();
                        result.addError(AppInternalError.missingParameters());
                        result.onAborted();
                        return result;
                    }

                    @Override
                    public ProcessResult doSynchronizeTicket(String freshdeskTicketId, OperationBreaker breaker) {
                        log.info("freshdeskTicketId:{}", freshdeskTicketId);
                        return synchronizeTicket(freshdeskTicketId, breaker);
                    }
                });
                log.info("@@@@@ START TICKET SERVICE INITIALIZING...@@@@@");
                ticketServiceInitializer.startTicketInitializing();
            }
        });
    }

    private void updateCspAccountCache() {
        CloudZService.updateCspAccountCache();
        CloudZService.updateIbmCustomerCache();
    }

    public void addMonitoringTicketList(final List<String> freshdeskTicketIdList, final boolean force) {
        log.info("freshdeskTicketIdList:{}, force:{}", freshdeskTicketIdList, force);
        if (freshdeskTicketIdList != null && freshdeskTicketIdList.size() > 0) {
            if (!FreshdeskService.canApiCall()) {
                log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
                return;
            }

            JobScheduler.execute(new Runnable() {
                @Override
                public void run() {
                    for (String freshdeskTicketId : freshdeskTicketIdList) {
                        JSONObject freshdeskTicketData = null;
                        try {
                            FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
                            freshdeskTicketData = ticketResponse.getResponseBody();
                            TicketSyncCondition syncCondition = checkTicketSyncCondition(freshdeskTicketData);
                            if (force || syncCondition.isSyncable()) {
                                JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
                                String escalation = customData.optString(FreshdeskTicketField.CfEscalation);
                                int fdTicketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
                                String cspTicketId = customData.optString(FreshdeskTicketField.CfCspCaseId);
                                String csp = customData.optString(FreshdeskTicketField.CfCsp);
                                if (!TicketUtil.isValidCsp(csp)) {
                                    log.error("Ticket ticket {} is not IBM Ticket.", freshdeskTicketData.optString(FreshdeskTicketField.Id));
                                    return;
                                }

                                if (TicketUtil.isValidEscalationField(escalation)) {
                                    log.info("already exists. freshdeskTicketId:{}, cspTicketId:{}, fdTicketStatus:{}", freshdeskTicketId, cspTicketId, fdTicketStatus);
                                    if (cspTicketId == null || "".equals(cspTicketId)) {
                                        ProcessResult result = ticketHandler.createCspTicket(freshdeskTicketData);
                                        if (result.isSuccess()) {
                                            onSuccessCspTicketCreation(freshdeskTicketId);
                                        } else {
                                            onFailedCspTicketCreation(freshdeskTicketId, result.getErrorCauseForErrorNote());
                                        }
                                    } else {
                                        log.info("already created on IBM. freshdeskTicketId:{}, cspTicketId:{}, fdTicketStatus:{}", freshdeskTicketId, cspTicketId, fdTicketStatus);
                                        if (FreshdeskTicketStatus.isOpen(fdTicketStatus)) {
                                            log.info("add to monitoring. {}", freshdeskTicketId);
                                            TicketUtil.checkAndReplaceBrandEmail(freshdeskTicketData);
                                            TicketMetadata ticketMetadata = TicketMetadata.build(freshdeskTicketData, true);
                                            addMonitoringTicket(freshdeskTicketId, ticketMetadata);
                                        } else {
                                            log.error("freshdeskTicketId {} is not opened.", freshdeskTicketId);
                                        }
                                    }
                                } else {
                                    log.error("freshdeskTicketId {} is not escalated yet.", freshdeskTicketId);
                                }
                            } else {
                                log.error("This ticket {} is not syncable ticket. {}", freshdeskTicketId, syncCondition.getErrorMessage());
                            }
                        } catch (AppInternalError e) {
                            log.error("Failed to adding to monitoring. freshdesk ticket: {}. {}", freshdeskTicketId, e);
                            if (e.getErrorReason() == AppInternalErrorReason.FreshdeskApiCallRateLimitExceed) {
                                if (force) {
                                    addMonitoringTicketList(freshdeskTicketIdList);
                                }
                                return;
                            } else {
                                TicketSyncCondition syncCondition = checkTicketSyncCondition(freshdeskTicketData);
                                if (force || syncCondition.isSyncable()) {
                                    addMonitoringTicket(freshdeskTicketId);
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    public void removeMonitoringTicketList(final List<String> freshdeskTicketIdList) {
        log.info("freshdeskTicketIdList:{}", freshdeskTicketIdList);
        if (freshdeskTicketIdList != null && freshdeskTicketIdList.size() > 0) {
            JobScheduler.execute(new Runnable() {
                @Override
                public void run() {
                    for (String freshdeskTicketId : freshdeskTicketIdList) {
                        removeMonitoringTicket(freshdeskTicketId);
                        cancelTicketSynchronization(freshdeskTicketId);
                    }
                }
            });
        }
    }

    /**
     * 주기적으로 동기화할 티켓 목록에 추가.
     *
     * @param freshdeskTicketData
     */
    private void addMonitoringTicket(JSONObject freshdeskTicketData) {
        if (freshdeskTicketData != null) {
            TicketUtil.checkAndReplaceBrandEmail(freshdeskTicketData);
            TicketMetadata ticketMetadata = TicketMetadata.build(freshdeskTicketData, true);
            if (ticketMetadata != null) {
                addMonitoringTicket(ticketMetadata.getFreshdeskTicketId(), ticketMetadata);
            } else {
                addMonitoringTicket(freshdeskTicketData.optString(FreshdeskTicketField.Id));
            }
        }
    }

    /**
     * 주기적으로 동기화할 티켓 목록에 추가.
     *
     * @param freshdeskTicketId
     * @param ticketMetadata
     */
    @Override
    public void addMonitoringTicket(String freshdeskTicketId, TicketMetadata ticketMetadata) {
        if (ticketMetadata != null) {
            if (isBetaTestEnabled()) {
                if (isBetaTester(ticketMetadata.getCspAccountEmail())) {
                    ticketRegistry.addMonitoring(freshdeskTicketId, ticketMetadata);
                } else {
                    log.error("This ticket is not beta testers' ticket. abort monitoring. {}", ticketMetadata.getFreshdeskTicketId());
                    return;
                }
            } else {
                ticketRegistry.addMonitoring(freshdeskTicketId, ticketMetadata);
            }
            log.info("ticket added to polling {} <-> {}", ticketMetadata.getFreshdeskTicketId(), ticketMetadata.getCspTicketId());
        } else {
            log.error("invalid ticket information.");
        }
    }

    public void addMonitoringTicket(String freshdeskTicketId) {
        ticketRegistry.addMonitoring(freshdeskTicketId);
    }

    public void addMonitoringTicketList(List<String> freshdeskTicketIds) {
        ticketRegistry.addMonitoringList(freshdeskTicketIds);
    }

    @Override
    public void updateTicketMetadata(TicketMetadata ticketMetadata) {
        ticketRegistry.updateTicketMetadata(ticketMetadata);
    }

    @Override
    public void onLinkedTicketId(String freshdeskTicketId, String cspTicketId) {
        log.info("freshdeskTicketId: {}, cspTicketId: {}", freshdeskTicketId, cspTicketId);
        ticketRegistry.onLinkedTicketId(freshdeskTicketId, cspTicketId);
    }

    @Override
    public boolean isLinkedCspTicket(String cspTicketId) {
        return ticketRegistry.isLinkedCspTicket(cspTicketId);
    }

    /**
     * 주기적 동기화 목록에서 특정 티켓의 동기화 아이템 조회.
     *
     * @param freshdeskTicketId
     * @return
     */
    @Override
    public TicketMetadata getTicketMetadata(String freshdeskTicketId) {
        return ticketRegistry.getTicketMetadata(freshdeskTicketId);
    }

    @Override
    public boolean isMonitoringTicket(String freshdeskTicketId) {
        return ticketRegistry.isMonitoringTicket(freshdeskTicketId);
    }

    @Override
    public void removeMonitoringTicket(String freshdeskTicketId) {
        log.info("freshdeskTicketId:{}", freshdeskTicketId);
        ticketRegistry.removeMonitoring(freshdeskTicketId);
        eraseTicketPublicUrl(freshdeskTicketId);
    }

    /**
     * 주기적으로 동기화 하는 티켓 개수.
     */
    private int getMonitoringTicketCount() {
        return ticketRegistry.getMonitoringTicketCount();
    }

    public void setTicketPublicUrl(String freshdeskTicketId, String publicUrl) {
        log.info("{} - {}", freshdeskTicketId, publicUrl);
        ticketRegistry.setTicketPublicUrl(freshdeskTicketId, publicUrl);
    }

    public void eraseTicketPublicUrl(String freshdeskTicketId) {
        //Erase ticket url once ticket was closed.
        ticketRegistry.eraseTicketPublicUrl(freshdeskTicketId);
    }

    @Override
    public String getTicketPublicUrl(String freshdeskTicketId) {
        return ticketRegistry.getTicketPublicUrl(freshdeskTicketId);
    }

    /**
     * Freshdesk의 특정 티켓을 CSP에 티켓 생성.
     *
     * @param freshdeskTicketId
     * @throws IOException
     * @throws URISyntaxException
     */
    public AppResponse createCspTicket(String freshdeskTicketId, RequestBodyParam bodyParam) {
        AppResponse appResponse = AppResponse.from();
        log.info("freshdeskTicketId:{}, bodyParam: {}", freshdeskTicketId, bodyParam);
        if (bodyParam != null && bodyParam.getPublicUrl() != null) {
            setTicketPublicUrl(freshdeskTicketId, bodyParam.getPublicUrl());
        }

        if (!FreshdeskService.canApiCall()) {
            log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
            return appResponse;
        }
        if (freshdeskTicketId != null) {
            JSONObject freshdeskTicketData = null;
            try {
                FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
                freshdeskTicketData = ticketResponse.getResponseBody();
                if (freshdeskTicketData != null && freshdeskTicketData.has(FreshdeskTicketField.CustomFields)) {
                    JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
                    String csp = customData.optString(FreshdeskTicketField.CfCsp);

                    if (!TicketUtil.isValidCsp(csp)) {
                        log.error("This ticket {} is not IBM Ticket.", freshdeskTicketId);
                        return appResponse;
                    }
                    String cspAccount = customData.optString(FreshdeskTicketField.CfCspAccount);
                    FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(cspAccount);
                    if (!accountField.isValid()) {
                        log.error("This ticket {} has invalid csp account field {}. Aborted.", freshdeskTicketId, cspAccount);
                        onFailedCspTicketCreation(freshdeskTicketId, "This ticket has invalid csp account field.<br>Csp Account: " + cspAccount);
                        return appResponse;
                    }
                    if (isBetaTestEnabled()) {
                        if (!isBetaTester(accountField.getEmail())) {
                            log.error("[Beta test mode]: This ticket {} creator is not a beta tester {}. Aborted.", freshdeskTicketId, cspAccount);
                            onFailedCspTicketCreation(freshdeskTicketId, "[Beta test mode]<br>This ticket creator is not a beta tester.<br>Csp Account: " + cspAccount);
                            return appResponse;
                        }
                    }

                    String createAt = freshdeskTicketData.optString(FreshdeskTicketField.CreatedAt);
                    long ticketCreatedTime = TicketUtil.getTimeByFreshdeskTime(createAt);
                    long limitTime = getTicketSyncTargetTime();
                    if (ticketCreatedTime < limitTime) {
                        log.error("This ticket {} was created before the sync limit time. Aborted.", freshdeskTicketId);
                        onFailedCspTicketCreation(freshdeskTicketId, "This ticket was created before the sync limit time.<br>Limit time: " + TicketUtil.getLocalTimeString(new Date(limitTime)));
                        return appResponse;
                    }
                    String cspTicketId = customData.optString(FreshdeskTicketField.CfCspCaseId);
                    log.info("freshdeskTicketId:{}, cspTicketId:{}", freshdeskTicketId, cspTicketId);
                    if (cspTicketId == null || "".equals(cspTicketId)) {
                        ProcessResult result = ticketHandler.createCspTicket(freshdeskTicketData);
                        if (result.isSuccess()) {
                            onSuccessCspTicketCreation(freshdeskTicketId);
                        } else {
                            onFailedCspTicketCreation(freshdeskTicketId, result.getErrorCauseForErrorNote());
                        }
                    } else {
                        log.info("already exists. freshdeskTicketId:{}, cspTicketId:{}", freshdeskTicketId, cspTicketId);
                    }
                } else {
                    log.warn("This ticket is not syncable condition. freshdeskTicketId:{}", freshdeskTicketId);
                }
            } catch (AppInternalError e) {
                log.error(TicketUtil.freshdeskErrorText("Failed to read freshdesk ticket. error: {}"), e);
            }
        } else {
            log.error("freshdeskTicketId is null");
        }
        return appResponse;
    }

    /**
     * Freshdesk 티켓이 CSP에 정상적으로 생성된 경우 해당 티켓의 이전 티켓 생성 실패 에러 삭제.
     *
     * @param freshdeskTicketId
     */
    private void onSuccessCspTicketCreation(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            TicketErrorNoteMetadata metadata = TicketErrorNoteMetadata.loadErrorNoteMetadata(freshdeskTicketId);
            if (metadata != null) {
                metadata.onSuccessTicketCreation();
            }
        }
    }

    /**
     * Freshdesk 티켓의 CSP 티켓 생성이 실패한 경우 해당 티켓의 에러를 기록. 동일한 에러가 환경설정의 requiredErrorCountForReporting 값에 도달하면 Freshdesk의 해당 티켓에 에러 메모를 등록.
     *
     * @param freshdeskTicketId
     * @param errorCause
     */
    private void onFailedCspTicketCreation(String freshdeskTicketId, String errorCause) {
        if (freshdeskTicketId == null || errorCause == null) {
            return;
        }

        if (errorCause.startsWith("Please provide a subject when creating a standard support ticket")) {
            errorCause = "Please provide 'Support type' and 'Offering' field.";
        }

        TicketErrorNoteMetadata metadata = TicketErrorNoteMetadata.loadErrorNoteMetadata(freshdeskTicketId);
        if (metadata != null && metadata.isDuplicatedTicketCreationFailCause(errorCause)) {
            log.info("Already registered error note. aborted. {} - {}", freshdeskTicketId, errorCause);
            return;
        }

        AppInternalErrorNote errorNote = new AppInternalErrorNote(AppInternalErrorNote.ErrorType.TicketCreationFailure, errorCause);
        log.info("ticket:{}, errorCause:{}", freshdeskTicketId, errorCause);
        boolean registered = createFreshdeskErrorNote(freshdeskTicketId, errorNote.formattedText());
        if (registered) {
            if (metadata == null) {
                metadata = new TicketErrorNoteMetadata(freshdeskTicketId);
            }
            metadata.onFailedTicketCreation(errorCause);
        }
    }

    /**
     * Freshdesk의 티켓에 해당하는 CSP 티켓이 등록되면 동기화를 위해 CSP에 생성된 티켓 Id를 Freshdesk 티켓의 CSP Case Id 필드에 업데이트 한다.
     *
     * @param freshdeskTicketData
     * @param cspTicketId
     * @throws IOException
     * @throws URISyntaxException
     */
    @Override
    public void updateEscalationInfo(JSONObject freshdeskTicketData, String cspTicketId, String cspTicketDisplayId) throws AppInternalError {
        if (freshdeskTicketData != null) {
            final int TimeLengthMin = 10;
            String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
            log.info("updateRelatedCspCaseId freshdeskTicketId:{}, cspTicketId:{}, cspTicketDisplayId:{}", freshdeskTicketId, cspTicketId, cspTicketDisplayId);
            if (freshdeskTicketId != null) {
                JSONObject freshdeskTicketCustomData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
                String escalationTime = freshdeskTicketCustomData.optString(FreshdeskTicketField.CfEscalationTime);
                boolean updateReady = false;
                JSONObject updateData = new JSONObject();
                JSONObject updateCustomData = new JSONObject();
                //escalationTime이 비어 있는 경우에만 업데이트.
                if (escalationTime == null || escalationTime.trim().length() < TimeLengthMin) {
                    escalationTime = localTimeFormat.format(new Date());
                    updateCustomData.put(FreshdeskTicketField.CfEscalationTime, escalationTime);
                    updateReady = true;
                }

                if (cspTicketId != null) {
                    updateCustomData.put(FreshdeskTicketField.CfCspCaseId, cspTicketId);
                    updateReady = true;
                }

                if (updateReady) {
                    try {
                        log.info("ticket:{} - updateCustomData: {}", freshdeskTicketId, updateCustomData);
                        updateData.put(FreshdeskTicketField.CustomFields, updateCustomData);
                        FreshdeskService.updateTicket(freshdeskTicketId, updateData);
                    } catch (AppInternalError e) {
                        log.error(TicketUtil.freshdeskErrorText("Failed to update ticket {} escalation information. {}"), freshdeskTicketId, e);
                        throw AppInternalError.freshdeskApiError("Failed to update ticket escalation information.", e);
                    }
                } else {
                    log.info("No update information.");
                }

                if (cspTicketId != null && cspTicketDisplayId != null && cspTicketDisplayId.length() > 1) {
                    TicketUtil.createRelatedCspTicketMappingNote(freshdeskTicketId, cspTicketId, cspTicketDisplayId);
                }
            }
        }
    }

    /**
     * Freshdesk의 티켓 상태가 변경된 경우 CSP의 티켓에도 상태를 동기화 한다.
     *
     * @param freshdeskTicketId
     * @throws IOException
     * @throws URISyntaxException
     */
    public AppResponse changeTicketStatus(String freshdeskTicketId, RequestBodyParam bodyParam) {
        AppResponse appResponse = AppResponse.from();
        log.info("freshdeskTicketId:{}, bodyParam: {}", freshdeskTicketId, bodyParam);
        if (!FreshdeskService.canApiCall()) {
            log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
            return appResponse;
        }

        if (freshdeskTicketId != null) {
            JSONObject freshdeskTicketData = null;
            FreshdeskTicketResponse ticketResponse = null;
            try {
                ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
                freshdeskTicketData = ticketResponse.getResponseBody();
                TicketSyncCondition syncCondition = checkTicketSyncCondition(freshdeskTicketData);
                if (syncCondition.isSyncable()) {
                    JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
                    String escalation = customData.optString(FreshdeskTicketField.CfEscalation);
                    int fdTicketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
                    if (bodyParam != null && bodyParam.getPublicUrl() != null) {
                        if (!FreshdeskTicketStatus.isClosed(fdTicketStatus)) { //종료 티켓이 아닌 경우에만 업데이트. 종료시 url 삭제됨.
                            setTicketPublicUrl(freshdeskTicketId, bodyParam.getPublicUrl());
                        }
                    }

                    if (TicketUtil.isValidEscalationField(escalation)) {
                        switch (fdTicketStatus) {
                            case FreshdeskTicketStatus.Open:
                                addMonitoringTicket(freshdeskTicketData);
                                break;
                            case FreshdeskTicketStatus.Resolved:
                            case FreshdeskTicketStatus.Closed:
                                TicketUtil.checkAndReplaceBrandEmail(freshdeskTicketData);
                                TicketMetadata ticketMetadata = TicketMetadata.build(freshdeskTicketData, true);
                                if (ticketMetadata != null) {
                                    try {
                                        CloudZCspApiInfo cspApiInfo = CloudZService.getCspApiInfo(ticketMetadata.getCspAccountEmail(), ticketMetadata.getCspAccountId());
                                        if (cspApiInfo == null) {
                                            log.error("Not found available csp account {} of ticket {}.", ticketMetadata.getCspAccountId(), freshdeskTicketId);
                                            throw AppInternalError.invalidCspAccount("Not found available csp account. " + ticketMetadata.getCspAccountId()).note(true);
                                        } else {
                                            ProcessResult result = ticketHandler.closeCspTicket(cspApiInfo, freshdeskTicketData, ticketMetadata.getCspTicketId());
                                            if (result.isSuccess()) {
                                                onSuccessCspTicketClose(freshdeskTicketId);
                                                removeMonitoringTicket(freshdeskTicketId);
                                            } else {
                                                onFailedCspTicketClose(freshdeskTicketId, result.getErrorCauseForErrorNote());
                                            }
                                        }
                                    } catch (AppInternalError e) {
                                        log.error(TicketUtil.cloudzErrorText("Cant not get csp api info. {}"), e);
                                        onFailedCspTicketClose(freshdeskTicketId, e.getErrorReason().output());
                                    }
                                }
                                break;
                            case FreshdeskTicketStatus.Pending:
                                break;
                        }
                    }
                } else {
                    log.error("This ticket {} is not syncable ticket. {}", freshdeskTicketId, syncCondition.getErrorMessage());
                    onFailedCspTicketClose(freshdeskTicketId, syncCondition.getErrorMessage());
                }
            } catch (AppInternalError e) {
                log.error(TicketUtil.freshdeskErrorText("Failed to read freshdesk ticket. {}"), e);
                // 응답헤더의 내용을 티켓에 추가되는 에러 노트와 OG Alert에 추가할 필요는 없고 로그에만 찍히도록 하면 될 것 같습니다.
                // 에러 노트는 Support Portal 운영자, 에이전트 들이 보는 내용이라 굳이 표시하지 않아도 될 듯 합니다. by 김민정 @2022-07-22
                // String xRequestId = FreshdeskService.getXRequestId(ticketResponse);
                onFailedCspTicketClose(freshdeskTicketId, AppInternalError.cannotReadTicket("Failed to read freshdesk ticket.", e).getErrorReason().output());
            }
        }
        return appResponse;
    }

    /**
     * CSP 티켓의 종료 요청이 성공하면 이전에 기록된 티켓 실패 이력을 삭제한다.
     *
     * @param freshdeskTicketId
     */
    private void onSuccessCspTicketClose(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            TicketErrorNoteMetadata metadata = TicketErrorNoteMetadata.loadErrorNoteMetadata(freshdeskTicketId);
            if (metadata != null) {
                //metadata.onSuccessTicketStatusChange();
                metadata.deleteFile();
            }
        }
    }

    /**
     * CSP 티켓의 종료 요청이 실패하면 종료 실패 에러를 이력에 저장한다. 동일한 에러가 환경설정의 requiredErrorCountForReporting 값에 도달하면 Freshdesk의 해당 티켓에 에러 메모를 등록.
     *
     * @param freshdeskTicketId
     * @param errorCause
     */
    private void onFailedCspTicketClose(String freshdeskTicketId, String errorCause) {
        if (freshdeskTicketId == null || errorCause == null) {
            log.info("invalid parameter. freshdeskTicketId: {}, errorCause:{}", freshdeskTicketId, errorCause);
            return;
        }
        TicketErrorNoteMetadata metadata = TicketErrorNoteMetadata.loadErrorNoteMetadata(freshdeskTicketId);
        if (metadata != null && metadata.isDuplicatedTicketStatusChangeFailCause(errorCause)) {
            log.info("Already registered error note. aborted. {} - {}", freshdeskTicketId, errorCause);
            return;
        }

        AppInternalErrorNote errorNote = new AppInternalErrorNote(AppInternalErrorNote.ErrorType.TicketStatusChangeFailure, errorCause);
        boolean registered = createFreshdeskErrorNote(freshdeskTicketId, errorNote.formattedText());
        if (registered) {
            if (metadata == null) {
                metadata = new TicketErrorNoteMetadata(freshdeskTicketId);
            }
            metadata.onFailedTicketStatusChange(errorCause);
        }
    }

    /**
     * 티켓 대화를 업데이트하기 위해 외부 동기화 큐에 추가.
     *
     * @param freshdeskTicketId
     * @param trigger
     */
    public void synchronizeTicketConversation(String freshdeskTicketId, String trigger, RequestBodyParam bodyParam) {
        log.info("freshdeskTicketId:{}, trigger:{}, bodyParam: {}", freshdeskTicketId, trigger, bodyParam);
        if (bodyParam != null && bodyParam.getPublicUrl() != null) {
            setTicketPublicUrl(freshdeskTicketId, bodyParam.getPublicUrl());
        }

        //자동화 규칙에 의해 티켓대화 동기화 요청임.
        //자동화에 의해 호출되는 티켓 대화 동기화 요청은 요청이 왔을때 바로 체크.
        if (isMonitoringTicket(freshdeskTicketId)) {
            addExternalSyncRequest(freshdeskTicketId, trigger);
        } else {
            try {
                FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
                JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
                if (isClosedTicket(freshdeskTicketData)) {
                    log.warn("Ignore synchronization request for ticketId:({}) that have already been closed", freshdeskTicketId);
                    return; // 이미 종료된 티켓에 대해서는 동기화 요청을 무시하도록 수정. @2022.05.23
                }
                TicketSyncCondition syncCondition = checkTicketSyncCondition(freshdeskTicketData);
                if (syncCondition.isSyncable()) {
                    if (!isMonitoringTicket(freshdeskTicketId)) {
                        addMonitoringTicket(freshdeskTicketId);
                    }
                    addExternalSyncRequest(freshdeskTicketId, trigger);
                } else {
                    log.error("This ticket {} is not syncable ticket. {}", freshdeskTicketId, syncCondition.getErrorMessage());
                    SyncTriggerType triggerType = SyncTriggerType.from(trigger);
                    // 응답헤더의 내용을 티켓에 추가되는 에러 노트와 OG Alert에 추가할 필요는 없고 로그에만 찍히도록 하면 될 것 같습니다.
                    // 에러 노트는 Support Portal 운영자, 에이전트 들이 보는 내용이라 굳이 표시하지 않아도 될 듯 합니다. by 김민정 @2022-07-22
                    // String xRequestId = FreshdeskService.getXRequestId(ticketResponse);
                    String errorCause = syncCondition.buildInternalError().getErrorReason().output();
                    onFailedSynchronizeTicket(freshdeskTicketId, errorCause, triggerType);
                }
            } catch (AppInternalError e) {
                log.error("Failed to get freshdesk ticket {}. error: {}", freshdeskTicketId, e);
            }
        }
    }

    private boolean isClosedTicket(JSONObject freshdeskTicketData) {
        int fdTicketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
        if (FreshdeskTicketStatus.isClosed(fdTicketStatus)) return true;
        return false;
    }

    /**
     * 여러 티켓의 대화를 업데이트하기 위해 외부 동기화 큐에 추가.
     *
     * @param freshdeskTicketIdList
     * @param trigger
     */
    public void synchronizeTicketConversations(List<String> freshdeskTicketIdList, String trigger) {
        log.info("freshdeskTicketIdList:{}, trigger:{}", freshdeskTicketIdList, trigger);
        if (freshdeskTicketIdList != null && freshdeskTicketIdList.size() > 0) {
            JobScheduler.execute(new Runnable() {
                @Override
                public void run() {
                    //티켓 목록으로 대화 동기화 요청하는 자동화 규칙은 없음.
                    //운영자에 의해 요청된 티켓은 checkTicketSyncCondition 체크 없이 바로 동기화???.
                    for (String freshdeskTicketId : freshdeskTicketIdList) {
                        if (isMonitoringTicket(freshdeskTicketId)) {
                            addExternalSyncRequest(freshdeskTicketId, trigger);
                        } else {
                            try {
                                FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
                                JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
                                TicketSyncCondition syncCondition = checkTicketSyncCondition(freshdeskTicketData);
                                if (syncCondition.isSyncable()) {
                                    addExternalSyncRequest(freshdeskTicketId, trigger);
                                } else {
                                    log.error("This ticket {} is not syncable ticket. {}", freshdeskTicketId, syncCondition.getErrorMessage());
                                    SyncTriggerType triggerType = SyncTriggerType.from(trigger);
                                    // 응답헤더의 내용을 티켓에 추가되는 에러 노트와 OG Alert에 추가할 필요는 없고 로그에만 찍히도록 하면 될 것 같습니다.
                                    // 에러 노트는 Support Portal 운영자, 에이전트 들이 보는 내용이라 굳이 표시하지 않아도 될 듯 합니다. by 김민정 @2022-07-22
                                    // String xRequestId = FreshdeskService.getXRequestId(ticketResponse);
                                    String errorCause = syncCondition.buildInternalError().getErrorReason().output();
                                    onFailedSynchronizeTicket(freshdeskTicketId, errorCause, triggerType);
                                }
                            } catch (AppInternalError e) {
                                log.error("Failed to get freshdesk ticket {}. error: {}", freshdeskTicketId, e);
                            }
                        }
                    }
                }
            });
        }
    }

    public void startTicketWorkingThread() {
        log.info("Ticket Working Thread Starting...");
        startConversationSyncThread();
        startCspNewTicketCheckingThread();
        startCspNewConversationCheckingThread();
    }

    /**
     * 주기적으로 티켓 동기화를 호출하는 스케쥴러.
     */
    private ScheduledFuture ticketSyncSchedule;
    private long ticketSyncScheduledInterval;
    /**
     * 티켓 동기화 스케쥴러에 의해 시작되는 Runnable.
     */
    private final Runnable ticketSyncRunner = new Runnable() {
        @Override
        public void run() {
            log.info("\n@@@@@\nTicket Synchronization Runner\n@@@@@");
            prepareScheduledTicketSynchronization();
        }
    };

    /**
     * 티켓 동기화 스케쥴러 설정 여부 조회.
     */
    private boolean isTicketSyncScheduled() {
        return (ticketSyncSchedule != null);
    }

    /**
     * 티켓 동기화를 동작시키는 쓰레드를 시작.
     *
     * @param initialDelay
     */
    public void startTicketSyncSchedule(long initialDelay) {
        log.info("@@@@@ START TICKET SYNC SCHEDULE @@@@@ - initialDelay: {}", initialDelay);
        if (isSyncEnabled()) {
            if (!isTicketSyncScheduled()) {
                ticketSyncScheduledInterval = config.getTicketSyncInterval();
                ticketSyncSchedule = JobScheduler.schedule(ticketSyncRunner, ticketSyncScheduledInterval, initialDelay);
            }
            startTicketWorkingThread();
        }
    }

    /**
     * 티켓 동기화 스케쥴러 중지.
     */
    public void stopTicketSyncSchedule() {
        log.info("@@@@@ STOP TICKET SYNC SCHEDULE @@@@@");
        if (ticketSyncSchedule != null) {
            ticketSyncSchedule.cancel(true);
            log.info("Ticket sync schedule is canceled.");
            ticketSyncSchedule = null;
        } else {
            log.info("Ticket sync schedule is not running.");
        }
    }

    private void reinitializeTicketService() {
        log.info("@@@@@ REINITIALIZE TICKET SERVICE @@@@@");
        if (isSyncEnabled()) {
            JobScheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    log.info("isRunningTicketServiceInitializer: {}", isRunningTicketServiceInitializer());
                    if (isRunningTicketServiceInitializer()) {
                        stopTicketServiceInitializing(true);
                    }
                    startTicketServiceInitializing();
                }
            }, 500);
        } else {
            log.error("Ticket synchronization configuration is disabled.");
        }
    }

    /**
     * 즉각적인 티켓 동기화 실행.
     *
     * @param freshdeskTicketData
     */
    @Override
    public void startInstantTicketSync(final JSONObject freshdeskTicketData) {
        if (freshdeskTicketData != null) {
            JobScheduler.execute(new Runnable() {
                @Override
                public void run() {
                    String freshdeskTicketId = freshdeskTicketData.optString(FreshdeskTicketField.Id);
                    log.info("===> INSTANT SYNC TICKET ticketId:{}", freshdeskTicketId);
                    if (syncRequestHandler.tryInstantSync(freshdeskTicketId)) {
                        OperationBreaker breaker = new OperationBreaker();
                        ProcessResult result = ticketHandler.synchronizeTicket(freshdeskTicketData, breaker);
                        log.info("===> INSTANT SYNC TICKET ticketId:{}. result: {}", freshdeskTicketId, result);
                        if (result.isSuccess()) {
                            log.info("{} ticket synchronized successfully.", freshdeskTicketId);
                            onSuccessSynchronizeTicket(freshdeskTicketId);
                        } else if (result.isCanceled()) {
                            log.error("{} ticket synchronization canceled.", freshdeskTicketId);
                        } else {
                            if (result.hasErrorReason(AppInternalErrorReason.ExceedConversationLimit)) {
                                log.error("{} ticket synchronization failed because exceed conversation limit number.", freshdeskTicketId);
                                try {
                                    boolean exists = TicketUtil.isExistsTaggedConversation(freshdeskTicketId, AppConstants.MAX_THREAD_LIMIT);
                                    log.debug("exceed error note exists: {}", exists);
                                    if (!exists) {
                                        String message = String.format("Ticket conversation has reached a limited number(%d).", config.getTicketSyncConversationMax());
                                        String localTimeString = TicketUtil.getLocalTimeString(new Date());
                                        String body = String.format("<div>%s%s[%s:%s]</div>", message, AppConstants.FRESHDESK_LINEFEED_TWO_LINE, AppConstants.MAX_THREAD_LIMIT, localTimeString);
                                        TicketUtil.createPublicNote(freshdeskTicketId, body);
                                    }
                                } catch (AppInternalError e) {
                                    log.error("Failed to check the tag 'MAX_THREAD_LIMIT' from conversttions. {}", e);
                                }
                                log.info("Removing ticket from monitoring list. {}", freshdeskTicketId);
                                removeMonitoringTicket(freshdeskTicketId);
                            } else if (result.hasErrorReason(AppInternalErrorReason.FreshdeskApiCallRateLimitExceed)) {
                                log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
                                //TODO. Cancel reserved sync schedule.
                                //cancelAllTicketSynchronization();
                            } else {
                                log.error("Other error: {}", freshdeskTicketId);
                                if (result.hasNoteEnabledError()) {
                                    onFailedSynchronizeTicket(freshdeskTicketId, result.getErrorCauseForErrorNote(), SyncTriggerType.Instant);
                                }
                            }
                        }
                        syncRequestHandler.onCompleted(freshdeskTicketId);
                    } else {
                        log.info("InstantSync aborted. {}", freshdeskTicketId);
                    }
                }
            });
        } else {
            log.error("ticket object is null.");
        }
    }

    /**
     * 동기화 대상이 되는 열린 티켓 전부를 주기적 동기화 큐에 등록.
     */

    private void prepareScheduledTicketSynchronization() {
        int addedCount = 0;
        log.info("\n@@@@@\nPREPARE TICKET SYNC STARTED. time:{}\n@@@@@", TicketUtil.getLocalTimeString(new Date()));
        if (!isSyncEnabled()) {
            log.info("Ticket Synchronization was disabled.");
            return;
        }

        updateCspAccountCache();
        //TicketSyncLogger.setTicketSyncTime(System.currentTimeMillis()); //disabled. this is scheduled time log.

        for (String ticketId : ticketRegistry.getMonitoringTicketIdList()) {
            if (syncRequestHandler.addScheduled(ticketId)) {
                addedCount++;
            }
        }

        if (addedCount > 0) {
            synchronized (syncThreadLock) {
                log.info("syncThreadLock release");
                syncThreadLock.notifyAll();
            }
        }
        log.info("\n@@@@@\nPREPARE TICKET SYNC ENDED. time:{}\n@@@@@", TicketUtil.getLocalTimeString(new Date()));
    }

    /**
     * 역방향 신규 대화 모니터링에 의한 동기화 요청.
     *
     * @param freshdeskTicketId
     */
    @Override
    public void synchronizeConversationByCspMonitoring(String freshdeskTicketId) {
        log.info("freshdeskTicketId: {}", freshdeskTicketId);
        if (freshdeskTicketId == null) {
            log.error("invalid sync request. {}", freshdeskTicketId);
        }
        boolean added = syncRequestHandler.addExternalAuto(freshdeskTicketId);
        if (added) {
            synchronized (syncThreadLock) {
                log.info("syncThreadLock release");
                syncThreadLock.notifyAll();
            }
        } else {
            log.info("sync request failed. This ticket is being synced. {}", freshdeskTicketId);
        }
    }

    /**
     * 외부 요청에 의한 티켓 동기화 큐에 티켓 등록
     *
     * @param freshdeskTicketId
     * @param trigger
     */
    private void addExternalSyncRequest(String freshdeskTicketId, String trigger) {
        boolean added = false;
        SyncTriggerType triggerType = SyncTriggerType.from(trigger);
        if (freshdeskTicketId == null || triggerType.isInvalid()) {
            log.error("invalid sync request. {}/{}", freshdeskTicketId, trigger);
        }
        if (triggerType.isAuto()) {
            added = syncRequestHandler.addExternalAuto(freshdeskTicketId);
        } else if (triggerType.isManual()) {
            added = syncRequestHandler.addExternalManual(freshdeskTicketId);
        }
        if (added) {
            synchronized (syncThreadLock) {
                log.info("syncThreadLock release");
                syncThreadLock.notifyAll();
            }
        } else {
            log.info("sync request failed. The same ticket to synchronize already exists. {}", freshdeskTicketId);
        }
    }

    /**
     * 동기화 쓰레드 동작 여부.
     */
    private boolean isConversationSyncThreadRunning() {
        return conversationSyncThreadRunning;
    }

    final TicketSyncRequestHandler syncRequestHandler = new TicketSyncRequestHandler();
    final TicketSynchronizer externalSynchronizer = new TicketSynchronizer("EXTERNAL SYNC", config.getTicketSyncConcurrentMax());
    final TicketSynchronizer scheduledSynchronizer = new TicketSynchronizer("SCHEDULED SYNC", config.getTicketSyncConcurrentMax());
    final Object syncThreadLock = new Object();
    boolean conversationSyncThreadRunning = false;

    private void cancelAllTicketSynchronization() {
        log.warn("@@@@@@@@ STOP ALL SYNCHRONIZATION PROCESSING... @@@@@@@@");
        if (cspNewTicketCheckingBreaker != null) {
            cspNewTicketCheckingBreaker.cancel();
        }

        if (cspNewConversationCheckingBreaker != null) {
            cspNewConversationCheckingBreaker.cancel();
        }

        //동기화 대기 중인 티켓 목록 취소.
        log.info("Clear the ticket list waiting for the synchronization process.");
        syncRequestHandler.clear(); //scheduledSync, externalSync
        log.info("Stop the synchronization process of the periodic synchronization process.");
        scheduledSynchronizer.stopAllSync();
        log.info("Stop the synchronization process of the external request synchronization process.");
        externalSynchronizer.stopAllSync();
        log.info("Cancel operating finished.");
    }

    private void cancelTicketSynchronization(String freshdeskTicketId) {
        log.warn("@@@@@@@@ STOP SYNCHRONIZATION PROCESSING {} @@@@@@@@", freshdeskTicketId);
        scheduledSynchronizer.stopSync(freshdeskTicketId);
        externalSynchronizer.stopSync(freshdeskTicketId);
    }

    /**
     * 티켓 동기화 쓰레드 시작.
     */
    private void startConversationSyncThread() {
        log.info("isThreadRunning:{}", isConversationSyncThreadRunning());
        if (!isConversationSyncThreadRunning() && isSyncEnabled()) {
            conversationSyncThreadRunning = true;
            Thread syncThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isSyncEnabled()) {
                        String freshdeskTicketId;
                        SyncTriggerType triggerType;
                        if (syncRequestHandler.hasExternalRequest()) {
                            TicketSyncRequestHandler.SyncRequest syncRequest = syncRequestHandler.nextExternalRequest();
                            if (syncRequest != null) {
                                freshdeskTicketId = syncRequest.getFdTicketId();
                                triggerType = syncRequest.getTriggerType();
                                log.info("Synchronization by external request. {}/{}", freshdeskTicketId, triggerType);
                                externalSynchronizer.startSync(freshdeskTicketId, triggerType);
                            }
                        }

                        if (syncRequestHandler.hasScheduledRequest()) {
                            TicketSyncRequestHandler.SyncRequest syncRequest = syncRequestHandler.nextScheduledRequest();
                            if (syncRequest != null) {
                                freshdeskTicketId = syncRequest.getFdTicketId();
                                triggerType = syncRequest.getTriggerType();
                                log.info("Synchronization by schedule. {}", freshdeskTicketId);
                                scheduledSynchronizer.startSync(freshdeskTicketId, triggerType);
                            }
                        }

                        if (!syncRequestHandler.hasExternalRequest() && !syncRequestHandler.hasScheduledRequest()) {
                            synchronized (syncThreadLock) {
                                try {
                                    log.info("syncThread waiting...");
                                    syncThreadLock.wait();
                                } catch (InterruptedException e) {
                                    log.error("syncThread waiting error.{}", e.getMessage());
                                }
                            }
                        }
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Util.ignoreException(e);
                        }
                    }
                    syncRequestHandler.clear();
                    conversationSyncThreadRunning = false;
                }
            });
            log.info("\n@@@@@\nSYNC THREAD START\n@@@@@");
            syncThread.start();
        }
    }

    boolean cspNewTicketCheckingThreadRunning = false;
    private OperationBreaker cspNewTicketCheckingBreaker = null;

    private boolean isCspNewTicketCheckingThreadRunning() {
        return cspNewTicketCheckingThreadRunning;
    }

    private void startCspNewTicketCheckingThread() {
        log.info("isCspNewTicketCheckingThreadRunning:{}", isCspNewTicketCheckingThreadRunning());
        if (!isCspNewTicketCheckingThreadRunning() && isSyncEnabled() && isReverseSyncEnabled()) {
            cspNewTicketCheckingThreadRunning = true;
            Thread newTicketCheckingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    log.info("@@@@@ Enter Reverse Ticket Checking Thread. @@@@@");
                    while (isSyncEnabled() && isReverseSyncEnabled()) {
                        log.info("Attempting to reverse ticket checking.");
                        cspNewTicketCheckingBreaker = new OperationBreaker();
                        ticketHandler.checkCspNewTicket(cspNewTicketCheckingBreaker);

                        try {
                            log.info("Reverse ticket checking completed. enter sleep {} milli seconds.", config.getReverseSyncCheckingSleepTime());
                            Thread.sleep(config.getReverseSyncCheckingSleepTime());
                            log.info("Reverse ticket checking is waked up.");
                        } catch (InterruptedException e) {
                            Util.ignoreException(e);
                        }
                    }
                    log.info("@@@@@ Exit Reverse Ticket Checking Thread. @@@@@");
                    cspNewTicketCheckingThreadRunning = false;
                }
            });
            log.info("\n@@@@@\nREVERSE TICKET CHECKING THREAD START\n@@@@@");
            newTicketCheckingThread.start();
        }
    }

    boolean cspNewConversationCheckingThreadRunning = false;
    private OperationBreaker cspNewConversationCheckingBreaker = null;

    private boolean isCspNewConversationCheckingThreadRunning() {
        return cspNewConversationCheckingThreadRunning;
    }

    private void startCspNewConversationCheckingThread() {
        log.info("isCspNewConversationCheckingThreadRunning:{}", isCspNewConversationCheckingThreadRunning());
        if (!isCspNewConversationCheckingThreadRunning() && isSyncEnabled()) {
            cspNewConversationCheckingThreadRunning = true;
            Thread newConversationCheckingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    log.info("@@@@@ Enter Reverse Conversation Checking Thread. @@@@@");
                    while (isSyncEnabled()) {
                        log.info("Attempting to reverse conversation checking.");
                        cspNewConversationCheckingBreaker = new OperationBreaker();
                        ticketHandler.checkCspNewConversation(ticketRegistry.getMonitoringTicketMetadataList(), cspNewConversationCheckingBreaker);
                        try {
                            log.info("Reverse conversation checking completed. enter sleep {} milli seconds.", config.getReverseSyncCheckingSleepTime());
                            Thread.sleep(config.getReverseSyncCheckingSleepTime());
                            log.info("Reverse conversation checking is waked up.");
                        } catch (InterruptedException e) {
                            log.error("Failed to sleep reverse conversation checking. {}", e);
                        }
                    }
                    log.info("@@@@@ Exit Reverse Conversation Checking Thread. @@@@@");
                    cspNewConversationCheckingThreadRunning = false;
                }
            });
            log.info("\n@@@@@\nREVERSE CONVERSATION CHECKING THREAD START\n@@@@@");
            newConversationCheckingThread.start();
        }
    }

    private class TicketSynchronizer {
        private final String name;
        private final Queue<String> operatingTickets;
        private final Semaphore semaphore;
        private final Map<String, OperationBreaker> operationBreakers;

        private TicketSynchronizer(String name, int concurrentMax) {
            this.name = name;
            operatingTickets = new ConcurrentLinkedQueue<>();
            semaphore = new Semaphore(concurrentMax, true);
            operationBreakers = new ConcurrentHashMap<>();
        }

        /**
         * 이미 동기화 중인 티켓인지 여부 확인.
         *
         * @param freshdeskTicketId
         */
        private boolean isOperating(String freshdeskTicketId) {
            return operatingTickets.contains(freshdeskTicketId);
        }

        private void stopAllSync() {
            log.info(name + " Stop all synchronizing tickets.");
            for (String freshdeskTicketId : operationBreakers.keySet()) {
                OperationBreaker breaker = operationBreakers.get(freshdeskTicketId);
                if (breaker != null) {
                    log.info(name + " Cancel synchronizing by breaker. {}", freshdeskTicketId);
                    breaker.cancel();
                }
            }

            boolean allOperationTerminated;
            do {
                allOperationTerminated = true;
                for (String freshdeskTicketId : operationBreakers.keySet()) {
                    OperationBreaker breaker = operationBreakers.get(freshdeskTicketId);
                    if (breaker != null) {
                        if (!breaker.isOperationTerminated()) {
                            log.info(name + " Not terminated synchronizating ticket yet. {}", freshdeskTicketId);
                            allOperationTerminated = false;
                            break;
                        }
                    }
                }
                if (!allOperationTerminated) {
                    try {
                        log.info(name + " wait for terminating synchronization process.");
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        log.error(name + " Sleep failed. {}", e);
                    }
                }
            } while (!allOperationTerminated);
            operationBreakers.clear();
            log.info(name + " All synchronization is terminated.");
        }

        private void stopSync(final String freshdeskTicketId) {
            log.info(name + " Stop synchronizing ticket {}.", freshdeskTicketId);
            if (isOperating(freshdeskTicketId)) {
                log.info(name + " ticket {} is synchronizating here.");
                OperationBreaker breaker = operationBreakers.get(freshdeskTicketId);
                if (breaker != null) {
                    log.info(name + " ticket {} cancel synchronizating by breaker.", freshdeskTicketId);
                    breaker.cancel();
                } else {
                    log.warn(name + "ticket {} is no breaker.", freshdeskTicketId);
                }
            } else {
                log.info(name + " ticket {} is not operating here.", freshdeskTicketId);
            }
        }

        /**
         * 티켓 대화 동기화 시작
         *
         * @param freshdeskTicketId
         * @param triggerType
         */
        private void startSync(final String freshdeskTicketId, final SyncTriggerType triggerType) {
            if (freshdeskTicketId == null || triggerType.isInvalid()) {
                log.error(name + " - Invalid ticket id or trigger type. ticket:{}, trigger:{}", freshdeskTicketId, triggerType);
                return;
            }
            if (!FreshdeskService.canApiCall()) {
                log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
                return;
            }
            if (isOperating(freshdeskTicketId)) {
                log.info(name + " - {}, Already operating now. aborted.", freshdeskTicketId);
                return;
            }
            log.info(name + " - {}, availablePermits:{}", freshdeskTicketId, semaphore.availablePermits());
            try {
                semaphore.acquire();
                operatingTickets.offer(freshdeskTicketId);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        log.info(name + " ticket: {}", freshdeskTicketId);
                        try {
                            OperationBreaker breaker = new OperationBreaker();
                            operationBreakers.put(freshdeskTicketId, breaker);
                            ProcessResult result = synchronizeTicket(freshdeskTicketId, breaker);
                            operationBreakers.remove(freshdeskTicketId);

                            log.info(name + " ticket: {}, synchronizeTicket result: {}", freshdeskTicketId, result);
                            if (result.isSuccess()) {
                                onSuccessSynchronizeTicket(freshdeskTicketId);
                            } else if (result.isCanceled()) {
                                log.info(name + " Ticket {} sync is canceled.", freshdeskTicketId);
                            } else {
                                if (result.hasErrorReason(AppInternalErrorReason.ExceedConversationLimit)) {
                                    log.info(name + " Ticket {} conversations are reached maximum number.", freshdeskTicketId);
                                    try {
                                        boolean exists = TicketUtil.isExistsTaggedConversation(freshdeskTicketId, AppConstants.MAX_THREAD_LIMIT);
                                        log.info("error note exists: {}", exists);
                                        if (!exists) {
                                            String message = String.format("Ticket conversation has reached a limited number(%d).", config.getTicketSyncConversationMax());
                                            String localTimeString = TicketUtil.getLocalTimeString(new Date());
                                            String body = String.format("<div>%s%s[%s:%s]</div>", message, AppConstants.FRESHDESK_LINEFEED_TWO_LINE, AppConstants.MAX_THREAD_LIMIT, localTimeString);
                                            TicketUtil.createPublicNote(freshdeskTicketId, body);
                                        }
                                    } catch (AppInternalError e) {
                                        log.error(name + " Failed to check the tag 'MAX_THREAD_LIMIT' from conversttions. {}", e);
                                    }
                                    log.info(name + " Remove ticket from monitoring list. {}", freshdeskTicketId);
                                    removeMonitoringTicket(freshdeskTicketId);
                                } else if (result.hasErrorReason(AppInternalErrorReason.FreshdeskApiCallRateLimitExceed)) {
                                    log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
                                    //TODO. Cancel reserved sync schedule.
                                    //cancelAllTicketSynchronization();
                                } else {
                                    log.error(name + " Other error: {}", freshdeskTicketId);
                                    if (result.hasNoteEnabledError()) {
                                        onFailedSynchronizeTicket(freshdeskTicketId, result.getErrorCauseForErrorNote(), triggerType);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error(name + " Ticket sync error occurred. {} - error:{}", freshdeskTicketId, e);
                        } finally {
                            log.info(name + " sync finished. {}", freshdeskTicketId);
                            syncRequestHandler.onCompleted(freshdeskTicketId);
                            operatingTickets.remove(freshdeskTicketId);
                            semaphore.release();
                        }
                    }
                }).start();
            } catch (InterruptedException e) {
                Util.ignoreException(e);
            }
        }
    }

    private ProcessResult synchronizeTicket(String freshdeskTicketId, OperationBreaker breaker) {
        log.info("===> SYNC TICKET ticketId:{}", freshdeskTicketId);
        ProcessResult processResult = ProcessResult.base();
        if (freshdeskTicketId == null) {
            processResult.addError(AppInternalError.missingParameters("empty ticket id."));
            processResult.onAborted();
            return processResult;
        }
        JSONObject freshdeskTicketData = null;
        FreshdeskTicketResponse ticketResponse = null;
        try {
            ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
            freshdeskTicketData = ticketResponse.getResponseBody();
            if (freshdeskTicketData != null) {
                TicketSyncCondition syncCondition = checkTicketSyncCondition(freshdeskTicketData);
                if (syncCondition.isSyncable()) {
                    processResult = ticketHandler.synchronizeTicket(freshdeskTicketData, breaker);
                } else {
                    log.error("This ticket {} is not syncable ticket. {}", freshdeskTicketId, syncCondition.getErrorMessage());
                    processResult.addError(syncCondition.buildInternalError().note(true));
                }
            } else {
                // 응답헤더의 내용을 티켓에 추가되는 에러 노트와 OG Alert에 추가할 필요는 없고 로그에만 찍히도록 하면 될 것 같습니다.
                // 에러 노트는 Support Portal 운영자, 에이전트 들이 보는 내용이라 굳이 표시하지 않아도 될 듯 합니다. by 김민정 @2022-07-22
                // String xRequestId = FreshdeskService.getXRequestId(ticketResponse);
                processResult.addError(AppInternalError.cannotReadTicket("Failed to get freshdesk ticket with error response. " + freshdeskTicketId).note(true));
            }
        } catch (AppInternalError e) {
            // 응답헤더의 내용을 티켓에 추가되는 에러 노트와 OG Alert에 추가할 필요는 없고 로그에만 찍히도록 하면 될 것 같습니다.
            // 에러 노트는 Support Portal 운영자, 에이전트 들이 보는 내용이라 굳이 표시하지 않아도 될 듯 합니다. by 김민정 @2022-07-22
            // String xRequestId = FreshdeskService.getXRequestId(ticketResponse);
            processResult.addError(AppInternalError.cannotReadTicket("Failed to get freshdesk ticket with exception.", e).note(true));
        }
        log.info("===> SYNC TICKET ticketId:{}. result: {}", freshdeskTicketId, processResult);
        return processResult;
    }

    public void createFreshdeskTicket(final List<String> cspTicketIdList) {
        log.info("cspTicketIdList: {}", cspTicketIdList);
        if (!FreshdeskService.canApiCall()) {
            log.error("Not available Freshdesk api call. Freshdesk API calls have reached maximum of plan.");
            return;
        }
        if (cspTicketIdList != null) {
            JobScheduler.execute(new Runnable() {
                @Override
                public void run() {
                    for (String cspTicketId : cspTicketIdList) {
                        for (IbmBrandAccount brandAccount : config.getReverseSyncAccounts()) {
                            if (!isSyncEnabled() || !isReverseSyncEnabled()) {
                                log.error("Ticket sync configuration is disabled.");
                                return;
                            }
                            try {
                                log.info("Creating IBM ticket to Freshdesk. ibm ticket: {}, brand: {}", cspTicketId, brandAccount.getBrandId());
                                FreshdeskTicketBuilder ticketBuilder = ticketHandler.buildFreshdeskTicketBuilder(brandAccount, cspTicketId);
                                if (ticketBuilder != null) {
                                    if (!isSyncEnabled() || !isReverseSyncEnabled()) {
                                        log.error("Ticket sync configuration is disabled.");
                                        return;
                                    }
                                    ProcessResult creationResult = createFreshdeskTicket(ticketBuilder);
                                    if (creationResult.isSuccess()) {
                                        //현재 brand account로 티켓이 정상 등록되었으므로 다른 brand account로 티켓 생성 시도할 필요 없이 다음 티켓 생성 시도.
                                        break;
                                    } else {
                                        log.error("Failed to create freshdesk ticket for CSP ticket: {} using brand: {}. error:{}", cspTicketId, brandAccount.getBrandId(), creationResult.getErrorCauseForErrorNote());
                                    }
                                } else {
                                    log.error("Failed to build ticket creation for CSP ticket: {} using brand: {}.", cspTicketId, brandAccount.getBrandId());
                                }
                            } catch (AppInternalError e) {
                                log.error("Failed to create freshdesk ticket for CSP ticket: {} using brand: {}. error:{}", cspTicketId, brandAccount.getBrandId(), e);
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public ProcessResult createFreshdeskTicket(FreshdeskTicketBuilder ticketBuilder) {
        ProcessResult processResult = ProcessResult.base();
        if (!isReverseSyncEnabled()) {
            log.error("Reverse synchronization is disabled.");
            processResult.addError(AppInternalError.notSupported("Reverse synchronization is disabled."));
            processResult.onAborted();
            return processResult;
        }
        if (ticketBuilder != null) {
            TicketMetadata ticketMetadata = null;
            if (isBetaTestEnabled()) {
                if (!isBetaTester(ticketBuilder.getEmail())) {
                    log.error("This ticket is not beta tester's ticket. Rejected. csp ticket id: {}, email:{}", ticketBuilder.getCspTicketId(), ticketBuilder.getEmail());
                    //processResult.addError(AppInternalError.notBetaTester(ticketCreation.getEmail() + "is not beta tester."));
                    processResult.onRejected(ProcessResult.RejectCause.notBetaTester);
                    return processResult;
                }
            }

            if (!FreshdeskService.canApiCall()) {
                log.error("Not available Freshdesk API call.");
                processResult.addError(AppInternalError.freshdeskApiCallRateLimitExceed());
                return processResult;
            }

            try {
                log.info("creating Freshdesk ticket for ibm ticket {}.", ticketBuilder.getCspTicketId());
                JSONObject ticketResult = FreshdeskService.createTicket(ticketBuilder.buildTicketCreationParameter(), ticketBuilder.getAttachments());
                if (ticketResult != null) {
                    log.info("Freshdesk ticket created successfully. IBM {} - {} ==> Freshdesk {}", ticketBuilder.getCspTicketId(), ticketBuilder.getIbmServiceProviderResourceId(), ticketResult.optString(FreshdeskTicketField.Id));
                    TicketUtil.checkAndReplaceBrandEmail(ticketResult);
                    ticketMetadata = TicketMetadata.build(ticketResult, true);
                    if (ticketMetadata != null) {
                        TicketUtil.createRelatedCspTicketMappingNote(ticketMetadata.getFreshdeskTicketId(), ticketMetadata.getCspTicketId(), ticketBuilder.getIbmServiceProviderResourceId());
                        onLinkedTicketId(ticketMetadata.getFreshdeskTicketId(), ticketMetadata.getCspTicketId());

                        addMonitoringTicket(ticketMetadata.getFreshdeskTicketId(), ticketMetadata);
                        startInstantTicketSync(ticketResult);
                    } else {
                        log.error("Ticket mapping object creation failed.");
                    }
                } else {
                    log.error("Ticket creation failed. IBM {} - {} ==> Freshdesk", ticketBuilder.getCspTicketId(), ticketBuilder.getIbmServiceProviderResourceId());
                    //티켓 생성 실패에 대한 에러 티켓 작성.
                    String agentEmail = config.getIbmAgentL1Email();
                    List<String> ccEmails = null;
                    JSONObject errorTicketBody = FreshdeskTicketBuilder.buildErrorReportTicketParameter(ticketBuilder.getCspTicketId(), ticketBuilder.getIbmServiceProviderResourceId(), ticketBuilder.getSubject(), agentEmail, ccEmails);
                    JSONObject errorTicketResult = FreshdeskService.createTicket(errorTicketBody, null);
                    if (errorTicketResult != null) {
                        log.info("Error ticket {} created for csp ticket {}", errorTicketResult.optString(FreshdeskTicketField.Id), ticketBuilder.getCspTicketId());
                    } else {
                        log.error("Error ticket creation was failed for csp ticket {}", ticketBuilder.getCspTicketId());
                    }
                }
            } catch (AppInternalError e) {
                log.error("Failed to create ticket on the freshdesk. {}", e.getMessage());
                processResult.addError(AppInternalError.freshdeskApiError("Failed to create ticket on the freshdesk.", e));
                return processResult;
            }

            try {
                if (ticketBuilder.isUnplannedEvent() || TicketUtil.isUnplannedEventTest(ticketBuilder)) {
                    log.info("Creating unplannedEvent ticket IBM ticket {} ==> Freshdesk.", ticketBuilder.getCspTicketId());
                    List<String> relatedTicketIds = null;
                    if (ticketMetadata != null) {
                        relatedTicketIds = new ArrayList<>();
                        relatedTicketIds.add(ticketMetadata.getFreshdeskTicketId()); //ticketData.optString(FreshdeskTicketField.Id);
                        log.info("UnplannedTicket's related_ticket_id {}", ticketMetadata.getFreshdeskTicketId());
                    }

                    //Freshdesk createTicket api always fails if request the related_ticket_ids field and attachment field in same time.
                    JSONObject unplannedTicketResult = FreshdeskService.createTicket(ticketBuilder.buildUnplannedEventTicketParameter(relatedTicketIds), null);
                    log.info("unplannedTicketResult :{}", unplannedTicketResult);
                    if (unplannedTicketResult != null) {
                        if (ticketBuilder.getAttachments() != null) {
                            JSONObject attachmentUpdateResult = FreshdeskService.updateTicket(unplannedTicketResult.optString(FreshdeskTicketField.Id), null, ticketBuilder.getAttachments());
                            log.info("attachmentUpdateResult :{}", attachmentUpdateResult);
                        }
                        log.info("UnplannedTicket created successfully. IBM {} - {} ==> Freshdesk {}", ticketBuilder.getCspTicketId(), ticketBuilder.getIbmServiceProviderResourceId(), unplannedTicketResult.optString(FreshdeskTicketField.Id));
                    } else {
                        log.error("UnplannedTicket creation failed. IBM {} - {} ==> Freshdesk", ticketBuilder.getCspTicketId(), ticketBuilder.getIbmServiceProviderResourceId());
                    }
                }
            } catch (AppInternalError e) {
                log.error("Failed to create ticket on the freshdesk. {}", e.getMessage());
                processResult.addError(AppInternalError.freshdeskApiError("Failed to create ticket on the freshdesk.", e));
                return processResult;
            }
        } else {
            log.error("TicketCreation is empty.");
            processResult.addError(AppInternalError.missingParameters("TicketCreation is empty."));
        }
        return processResult;
    }

    /**
     * 장애 발생시 Opsgenie에 Alert 생성.
     *
     * @param freshdeskTicketId
     * @param errorNote
     * @param triggerType
     */
    private void createOpsgenieAlert(String freshdeskTicketId, String errorNote, SyncTriggerType triggerType) {
        log.info("freshdeskTicketId:{}, trigger:{}, errorNote: {}", freshdeskTicketId, triggerType, errorNote);
        String title;
        String description = "Ticket synchronization failed in CSP ticket service integrated in Support Portal.\n\n";
        TicketMetadata ticketMetadata = getTicketMetadata(freshdeskTicketId);
        String ticketLink = ticketRegistry.getTicketPublicUrl(freshdeskTicketId);
        if (ticketLink == null) {
            ticketLink = TicketUtil.buildFreshdeskTicketLink(freshdeskTicketId);
        }

        if (triggerType.isAuto()) {
            title = "[Ticket Sync Fail][Auto] CSP ticket integration in Support Portal";
            description += "Type : Auto Sync Fail";
        } else if (triggerType.isManual()) {
            title = "[Ticket Sync Fail][Manual] CSP ticket integration in Support Portal";
            description += "Type : Manual Sync Fail";
        } else {
            title = "[Ticket Sync Fail][Schedule] CSP ticket integration in Support Portal";
            description += "Type : Schedule Sync Fail";
        }
        description += "\nTicket Link : " + ticketLink;
        description += "\nTicket Note : " + errorNote;
        try {
            SuccessResponse response = OpsgenieService.createAlert(title, description);
            log.info("opsgenie createAlert result:" + response);
        } catch (ApiException e) {
            log.error("Opsgenie createAlert failed. " + e.getMessage());
        }
    }

    /**
     * Freshdesk 티켓에 에러 메모 등록.
     *
     * @param freshdeskTicketId
     * @param errorNote
     */
    private boolean createFreshdeskErrorNote(String freshdeskTicketId, String errorNote) {
        log.debug("freshdeskTicketId:{}, errorNote:{}", freshdeskTicketId, errorNote);
        try {
            JSONObject result = FreshdeskService.createErrorNote(freshdeskTicketId, errorNote);
            return (result != null);
        } catch (AppInternalError e) {
            log.error("Failed to register error note. {}", e);
        }
        return false;
    }

    /**
     * 마지막에 등록된 에러 메모와 동일한 에러인지 확인.
     *
     * @param freshdeskTicketId
     * @param errorCause
     */
    private boolean isExistsErrorAtLastConversationOfFreshdeskTicket(String freshdeskTicketId, AppInternalErrorNote.ErrorType errorType, String errorCause) {
        if (freshdeskTicketId == null || errorType == null || errorCause == null) {
            return false;
        }
        try {
            JSONObject last = FreshdeskService.getLastConversation(freshdeskTicketId);
            if (last != null) {
                String htmlBody = last.optString(FreshdeskTicketField.ConversationBodyHtml);
                AppInternalErrorNote errorNote = AppInternalErrorNote.from(htmlBody);
                if (errorNote != null) {
                    return errorNote.isSameError(errorType, errorCause);
                } else {
                    /*boolean monitoringTagged = TicketUtil.isTaggedMonitoring(htmlBody);
                    String errorText = errorCause.replaceAll(AppConstants.CSP_LINEFEED, AppConstants.FRESHDESK_LINEFEED);
                    if (monitoringTagged && htmlBody.contains(errorText)) {
                        return true;
                    }*/
                }
            }
        } catch (AppInternalError e) {
            log.error("error: {}", e);
        }
        return false;
    }

    /**
     * 티켓 대화 동기화 성공적으로 처리된 경우 해당 티켓의 에러 초기화.
     *
     * @param freshdeskTicketId
     */
    private void onSuccessSynchronizeTicket(String freshdeskTicketId) {
        if (freshdeskTicketId == null) {
            return;
        }
        TicketErrorNoteMetadata metadata = TicketErrorNoteMetadata.loadErrorNoteMetadata(freshdeskTicketId);
        if (metadata != null) {
            metadata.onSuccessConversationSync();
        }
    }

    /**
     * 티켓 대화 동기화가 실패한 경우 해당 티켓의 에러를 기록. 동일한 에러가 환경설정의 requiredErrorCountForReporting 값에 도달하면 Freshdesk의 해당 티켓에 에러 메모를 등록.
     *
     * @param freshdeskTicketId
     * @param errorCause
     * @param triggerType
     */
    private void onFailedSynchronizeTicket(String freshdeskTicketId, String errorCause, SyncTriggerType triggerType) {
        final String UnableToEditTicketMessage = "Unable to edit ticket";
        final String AdditionalGuideMessage = "<br>---<br>" +
                "직전 답변이 IBM 지원센터에 등록되지 않았습니다.<br>" +
                "IBM 지원센터에 직접 로그인하여 해당 티켓에 답변 등록 부탁드립니다.<br>" +
                "이용에 불편을 드려 죄송합니다.";
        if (freshdeskTicketId == null || errorCause == null) {
            return;
        }
        TicketErrorNoteMetadata metadata = TicketErrorNoteMetadata.loadErrorNoteMetadata(freshdeskTicketId);
        if (metadata != null && metadata.isDuplicatedConversationSyncFailCause(errorCause)) { //Use original cause.
            log.info("Already registered error note. aborted. {} - {}", freshdeskTicketId, errorCause);
            return;
        }

        String causeText = errorCause;
        if (causeText.contains(UnableToEditTicketMessage)) {
            causeText += AdditionalGuideMessage;
        }

        AppInternalErrorNote errorNote = new AppInternalErrorNote(AppInternalErrorNote.ErrorType.TicketConversationSyncFailure, causeText);
        boolean registered = createFreshdeskErrorNote(freshdeskTicketId, errorNote.formattedText());
        createOpsgenieAlert(freshdeskTicketId, errorNote.formattedText(), triggerType);
        if (registered) {
            if (metadata == null) {
                metadata = new TicketErrorNoteMetadata(freshdeskTicketId);
            }
            metadata.onFailedConversationSync(errorCause); //Use original cause.
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Administration API
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    private ScheduledFuture restartSchedule;

    public JSONObject restartService(int delaySeconds) {
        log.warn("restartService was called. restarting Ticket Service. reserved restartSchedule:{}", (restartSchedule != null));
        JSONObject result = new JSONObject();
        if (restartSchedule == null) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    log.warn("Restarts the ticket service.");
                    restartSchedule = null;
                    stopTicketSyncSchedule();
                    Application.restart();
                }
            };
            restartSchedule = JobScheduler.schedule(runnable, delaySeconds * 1000);
            result.put("result", "reserved");
            result.put("delaySeconds", delaySeconds);
            result.put("message", String.format("Ticket service restarting was reserved. It restarts after %d seconds.", delaySeconds));
        } else {
            result.put("result", "aborted");
            result.put("message", "Ticket service restart is already scheduled.");
        }
        return result;
    }

    public JSONObject cancelRestartService() {
        log.info("Aborts restart Ticket Service. reserved restartSchedule:{}", (restartSchedule != null));
        JSONObject result = new JSONObject();
        if (restartSchedule != null) {
            restartSchedule.cancel(true);
            restartSchedule = null;
            result.put("result", "canceled");
            result.put("message", "Ticket service restarting was canceled.");
        } else {
            result.put("result", "aborted");
            result.put("message", "Ticket service restarting was not reserved.");
        }
        return result;
    }

    /**
     * 티켓 서비스의 현재 동작 정보 조회.
     */
    public String printServiceStatus() {
        JSONObject result = new JSONObject();
        JSONObject service = new JSONObject();
        //JSONObject agents = new JSONObject();

        ///// Service Common
        service.put("Name", serviceStatus.getName());
        service.put("Version", serviceStatus.getVersion());
        service.put("BuildTime", TicketUtil.getLocalTimeString(new Date(serviceStatus.getBuildTime())));
        service.put("DeployTime", TicketUtil.getLocalTimeString(new Date(serviceStatus.getDeployTime())));
        service.put("StartTime", TicketUtil.getLocalTimeString(new Date(serviceStatus.getStartTime())));
        service.put("ConfiguredTime", TicketUtil.getLocalTimeString(config.getConfiguredTime()));
        service.put("DeployStage", serviceStatus.getStage());
        if (isServiceInitialized()) {
            service.put("InitializedTime", TicketUtil.getLocalTimeString(new Date(serviceStatus.getInitializedTime())));
        }
        result.put("Service", service);

        //현재 초기화 진행중인 정보를 포함한다.
        if (ticketServiceInitializer != null) {
            log.debug("ticketServiceInitializer is living.");
            result.put("InitializingStatus", ticketServiceInitializer.exportReport());
        } else if (ticketServiceInitializingCompleteReport != null) {
            log.debug("ticketServiceInitializingCompleteReport is using.");
            result.put("InitializingStatus", ticketServiceInitializingCompleteReport);
        } else {
            result.put("InitializingStatus", "Not Running");
        }

        result.put("EscalationCheckEnabled", isEscalationCheckEnabled());
        result.put("TicketSyncScheduled", isTicketSyncScheduled());

        ///// Beta Tester
        result.put("BetaTestEnabled", isBetaTestEnabled());
        if (isBetaTestEnabled()) {
            JSONArray betaTesters = new JSONArray();
            for (String tester : config.getBetaTesters()) {
                betaTesters.put(tester);
            }
            result.put("BetaTester", betaTesters);
        }

        ///// Agents
        /*
        List<Agent> l1Agents = config.getL1Agents();
        if (l1Agents != null) {
            try {
                String jsonText = JsonUtil.marshal(l1Agents);
                agents.put("L1", new JSONArray(jsonText));
            } catch (JsonProcessingException e) {
                log.error("error:{}", e);
                agents.put("L1", new JSONArray());
            }
        } else {
            agents.put("L1", new JSONArray());
        }

        List<Agent> l2Agents = config.getL2Agents();
        if (l2Agents != null) {
            try {
                String jsonText = JsonUtil.marshal(l2Agents);
                agents.put("L2", new JSONArray(jsonText));
            } catch (JsonProcessingException e) {
                log.error("error:{}", e);
                agents.put("L2", new JSONArray());
            }
        } else {
            agents.put("L2", new JSONArray());
        }
        result.put("Agents", agents);
        */

        ///// Synchronization
        JSONObject synchronization = new JSONObject();
        synchronization.put("SynchronizationEnabled", isSyncEnabled());
        synchronization.put("ReverseSynchronizationEnabled", isReverseSyncEnabled());
        synchronization.put("SynchronizationRunning", isTicketSyncScheduled());
        synchronization.put("SynchronizationInterval", config.getTicketSyncInterval());
        synchronization.put("SynchronizationTargetTime", TicketUtil.getLocalTimeString(new Date(getTicketSyncTargetTime())));
        synchronization.put("LastSynchronizedTime", TicketUtil.getLocalTimeString(new Date(TicketSyncLogger.getTicketSyncTime())));

        if (isReverseSyncEnabled()) {
            JSONObject revSyncRecords = TicketSyncLogger.exportReverseSyncRecord();
            JSONObject revSyncTicketRecord = new JSONObject();
            for (String brandId : revSyncRecords.keySet()) {
                JSONObject recordJson = revSyncRecords.getJSONObject(brandId);
                TicketTimeRecord timeRecord = TicketTimeRecord.from(recordJson.toString());
                if (timeRecord != null) {
                    JSONObject formatted = new JSONObject();
                    String createTime = TicketUtil.getLocalTimeString(new Date(timeRecord.getCreateTime()));
                    formatted.put("TicketId", timeRecord.getTicketId());
                    formatted.put("CreateTime", createTime);
                    revSyncTicketRecord.put(brandId, formatted);
                }
            }
            synchronization.put("ReverseSynchronizationTicketRecord", revSyncTicketRecord);
        }
        synchronization.put("CurrentSynchronizationTicketCount", getMonitoringTicketCount());
        JSONArray tickets = new JSONArray();
        for (String freshdeskTicketId : ticketRegistry.getMonitoringTicketIdList()) {
            tickets.put(freshdeskTicketId);
        }
        synchronization.put("CurrentSynchronizationTickets", tickets);
        result.put("Synchronization", synchronization);

        /*if (isAvailableAccountCache()) {
            result.put("AccountCache", accountCache.export());
        } else {
            result.put("AccountCache", new JSONObject());
        }*/
        //return result.toString();

        //Freshdesk API Call Count Limitation
        result.put("FreshdeskRateLimit", FreshdeskService.getCurrentRateLimit());
        return JsonUtil.prettyPrint(result);
    }

    /**
     * 티켓 서비스의 환경 설정 정보를 업데이트하고 저장.
     *
     * @param configJsonText
     */
    public String setAppConfig(String configJsonText, boolean silentChange) {
        log.info("silentChange:{}, configJsonText: {}", silentChange, configJsonText);
        if (configJsonText != null) {
            try {
                JSONObject configObject = new JSONObject(configJsonText);
                AppConfig replaced = AppConfig.getOverlapped(configObject);
                log.info("replaced: {}", replaced.textOutput());
                if (silentChange) {
                    //환경 설정 변경만 조용히 변경.
                    AppConfig.store(replaced);
                    config.apply(replaced);
                    log.info("Configuration changed silently.");
                } else {
                    log.info("ticketServiceInitializeResultMeta: {}", ticketServiceInitializeReportMeta);
                    log.info("isServiceInitialized: {}", isServiceInitialized());
                    log.info("isTicketSyncScheduled: {}", isTicketSyncScheduled());
                    log.info("isRunningTicketServiceInitializer: {}", isRunningTicketServiceInitializer());

                    //현재 실제 서비스의 상태에 따라 변경 여부 확인.(silentChange 옵션에 의해 동작과 상관없이 환경 설정 값만 바뀔 수 있으므로.)
                    long currentSyncTargetTime = config.getTicketSyncTargetTime();
                    long currentSyncInterval = config.getTicketSyncInterval();
                    boolean currentSyncEnabled = isRunningTicketServiceInitializer() || isTicketSyncScheduled();
                    if (isRunningTicketServiceInitializer()) {
                        currentSyncTargetTime = ticketServiceInitializer.getResearchingPeriodStartTime();
                    } else if (ticketServiceInitializeReportMeta != null && ticketServiceInitializeReportMeta.isInitializeComplete()) {
                        //currentSyncTargetTime = ticketServiceInitializeReportMeta.getFreshdeskResearchingMeta().getPeriodStart();
                        long periodStart = ticketServiceInitializeReportMeta.getResearchingPeriodStartTime();
                        if (periodStart > 0) {
                            currentSyncTargetTime = periodStart;
                        }
                    }
                    if (isTicketSyncScheduled()) {
                        currentSyncInterval = ticketSyncScheduledInterval;
                    }

                    //조치가 필요한 상황 확인.
                    boolean syncTargetTimeChanged = currentSyncTargetTime != replaced.getTicketSyncTargetTime();
                    boolean syncIntervalChanged = currentSyncInterval != replaced.getTicketSyncInterval();
                    boolean serviceNotInitialized = false;

                    //환경 설정 변경 전 - 중지가 필요한 동작 처리.
                    if (isRunningTicketServiceInitializer()) { //초기화가 동작 중인 상태.
                        //초기화를 중지해야하는 경우: 대상 기간 변경된 경우, 동기화 설정이 disabled로 바뀐 경우
                        if (syncTargetTimeChanged || !replaced.isSyncEnabled()) {
                            stopTicketServiceInitializing(true);
                            log.info("Ticket service initializing canceled. Ticket service not initialized yet.");
                            serviceNotInitialized = true;
                        }
                    } else {
                        if (isServiceInitialized()) { //초기화가 완료된 상태.
                            if (syncTargetTimeChanged || !replaced.isSyncEnabled()) { //동작중인 동기화 쓰레드를 중지해야 하는 경우: 대상 기간 변경된 경우, 동기화 설정이 disabled로 바뀐 경우
                                stopTicketSyncSchedule(); //현재 동기화 스케쥴 취소.
                                cancelAllTicketSynchronization(); //현재 동기화 중인 티켓 동기화 쓰레드 모두 중지.
                            } else if (syncIntervalChanged) { //동기화 주기가 바뀐 경우.
                                if (isTicketSyncScheduled()) { //동기화 스케쥴이 동작 중인 상태.
                                    //이미 동작중인 동기화 쓰레드는 유지. - 동기화 스케쥴 취소 안함.
                                    //다음 동기화 스케쥴부터 주기 변경.
                                }
                            }
                        } else { //초기화가 진행되지 않은 상태.
                            log.info("Ticket service not initialized yet.");
                            serviceNotInitialized = true;
                        }
                    }

                    //환경 설정 변경
                    AppConfig.store(replaced);
                    config.apply(replaced);
                    log.info("@@@@@ SERVICE CONFIGURATION CHANGED. @@@@@");

                    //환경 설정 변경 후 - 새로 시작해야 하는 동작 처리.
                    log.info("SyncEnabled: {}", isSyncEnabled());
                    log.info("ReverseSyncEnabled: {}", isReverseSyncEnabled());
                    log.info("syncTargetTimeChanged: {}", syncTargetTimeChanged);
                    log.info("serviceNotInitialized: {}", serviceNotInitialized);
                    log.info("syncIntervalChanged: {}", syncIntervalChanged);
                    log.info("ServiceInitialized: {}", isServiceInitialized());
                    log.info("TicketSyncScheduled: {}", isTicketSyncScheduled());
                    log.info("isRunningTicketServiceInitializer: {}", isRunningTicketServiceInitializer());
                    if (isSyncEnabled()) {
                        final long MIN_DELAY = 3000;
                        if (syncTargetTimeChanged || serviceNotInitialized) { //초기화가 완료되지 않은 상태이거나, 초기화 재시작이 필요한 경우
                            reinitializeTicketService();
                        } else if (syncIntervalChanged) { //초기화가 완료된 상태. 동기화 주기가 바뀐 경우.
                            if (isTicketSyncScheduled()) { //스케쥴이 동작중인 경우.
                                log.info("stop previous sync schedule.");
                                stopTicketSyncSchedule(); //예약된 스케쥴 취소.

                                //이미 동작중인 동기화 쓰레드는 유지. 동기화 취소 안함.
                                //다음 동기화 스케쥴부터 주기 변경.
                                long nextTriggerTime = TicketSyncLogger.getTicketSyncTime() + replaced.getTicketSyncInterval();
                                long now = System.currentTimeMillis();
                                long newScheduleDelay = MIN_DELAY;
                                if (nextTriggerTime > now) {
                                    newScheduleDelay = nextTriggerTime - now; //다음 동기화 스케쥴까지 남은 시간.
                                }
                                if (newScheduleDelay < MIN_DELAY) {
                                    newScheduleDelay = MIN_DELAY;
                                }
                                log.info("reset sync schedule. schedule will be triggrer after {} seconds.", (newScheduleDelay / 1000));
                                startTicketSyncSchedule(newScheduleDelay); //변경된 동기화 주기가 환경 설정 값에 적용된 이후에 호출해야함.
                            }
                        }

                        if (!isConversationSyncThreadRunning()) {
                            startConversationSyncThread();
                        }
                        if (isReverseSyncEnabled()) {
                            if (!isCspNewTicketCheckingThreadRunning()) {
                                startCspNewTicketCheckingThread();
                            }
                            if (!isCspNewConversationCheckingThreadRunning()) {
                                startCspNewConversationCheckingThread();
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                log.error("Invalid configureation. {}", configJsonText);
            }
        }
        String configureText = config.textOutput();
        log.info("current configuration: {}", configureText);
        return configureText;
    }

    /**
     * 티켓 서비스의 환경 설정 정보를 조회.
     *
     * @param builtin
     */
    public String getAppConfig(boolean builtin) {
        AppConfig targetConfig;
        if (builtin) {
            targetConfig = AppConfig.getDefaultConfig();
        } else {
            targetConfig = config;
        }
        //String configureText = JsonUtil.marshal(targetConfig);
        //JSONObject configJson = targetConfig.jsonOutput();
        String configureText = targetConfig.textOutput();
        return configureText;
    }
}
