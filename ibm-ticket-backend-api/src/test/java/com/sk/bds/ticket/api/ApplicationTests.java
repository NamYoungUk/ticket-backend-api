package com.sk.bds.ticket.api;

import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZUser;
import com.sk.bds.ticket.api.data.model.freshdesk.*;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.service.*;
import com.sk.bds.ticket.api.util.FreshdeskTicketLoader;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.ticket.Update;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

//@PropertySource(value = "application.properties", encoding = "UTF-8")
//@PropertySource("classpath:application.properties")
//@ActiveProfiles("local")
//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
//@SpringBootTest
@RunWith(SpringRunner.class)
//@TestPropertySource(properties = {"my.value=value"})
@TestPropertySource("classpath:application.properties")
@Slf4j
public class ApplicationTests {
    //https://stackoverflow.com/questions/55147012/value-on-a-field-in-a-service-injected-with-mockbean-results-in-null-value
    //The logback system uses the "src/test/resources/logback-test.xml" file to configure the unit testing log levels.

    @Autowired
    //@MockBean
    @SpyBean
    PropertyReader propertyReader;
    CspTicketHandler ticketHandler;

    @PostConstruct
    public void initialize() {
        log.info("Test Application initializing...");
        AppConfig.initialize(propertyReader);
        AppConfig instantConfig = AppConfig.getDefaultConfig();
        AppConfig config = AppConfig.getInstance();
        log.info("stage: {}, SupportPortal: {}", config.getServiceStage(), config.getFreshdeskApiEndpoint());
        AppConfig.store(instantConfig); //change to default configuration
        config.apply(instantConfig); //apply configuration to current instance
        log.info("stage: {}, SupportPortal: {}", config.getServiceStage(), config.getFreshdeskApiEndpoint());
        ticketHandler = new IbmService(new TicketOperator() {
            @Override
            public void startInstantTicketSync(JSONObject freshdeskTicketData) {

            }

            @Override
            public void synchronizeConversationByCspMonitoring(String freshdeskTicketId) {

            }

            @Override
            public boolean isMonitoringTicket(String freshdeskTicketId) {
                return false;
            }

            @Override
            public void addMonitoringTicket(String freshdeskTicketId, TicketMetadata ticketMetadata) {

            }

            @Override
            public void removeMonitoringTicket(String freshdeskTicketId) {

            }

            @Override
            public TicketMetadata getTicketMetadata(String freshdeskTicketId) {
                return null;
            }

            @Override
            public void updateTicketMetadata(TicketMetadata ticketMetadata) {

            }

            @Override
            public void onLinkedTicketId(String freshdeskTicketId, String cspCaseId) {

            }

            @Override
            public boolean isLinkedCspTicket(String cspTicketId) {
                return false;
            }

            @Override
            public String getTicketPublicUrl(String freshdeskTicketId) {
                return null;
            }

            @Override
            public void updateEscalationInfo(JSONObject freshdeskTicketData, String cspTicketId, String cspTicketDisplayId) throws AppInternalError {

            }

            @Override
            public ProcessResult createFreshdeskTicket(FreshdeskTicketBuilder ticketBuilder) {
                return null;
            }
        });
    }

    @Before
    public void setup() {
        log.info("setup test configuration before test.");
    }

    public static final String TICKET_TITLE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
    static final String AgentEmail = "seoingood@sk.com";
    static final String AgentCspAccountFiled = "peter.lee@twolinecode.com/IBM2054072";
    static final String TesterEmail = "peter.lee@twolinecode.com";
    static final String TesterCspAccountFiled = "peter.lee@twolinecode.com/IBM0000000";

    private static String apiId() {
        return "IBM2054072";
        //return "IBM838391";
    }

    private static String apiKey() {
        return "5edb93a4070275b1b3a8685b9cf443c7e08651b4ab23d80a4bedbf9053e5861d"; //IBM2054072
        //return "960a59134dba43b3afa801e9ab7cc587a4f35ad2bcb005257874a8c4a6134443"; //IBM838391
    }

    private static CloudZCspApiInfo buildAccount(String apiId, String apiKey) {
        return new CloudZCspApiInfo(apiId, apiKey);
    }

    private static ApiClient buildClient(String apiId, String apiKey) {
        return IbmService.buildClient(apiId, apiKey);
        //return new CloudZCspApiInfo(apiId, apiKey).buildApiClient();
    }

    private static ApiClient client() {
        return buildClient(apiId(), apiKey());
        //return new CloudZCspApiInfo(apiId, apiKey).buildApiClient();
    }

    private static Ticket.Service ticketService(long cspTicketId) {
        return IbmService.ticketService(apiId(), apiKey(), cspTicketId);
        //return Ticket.service(client(), cspTicketId);
    }

    @Test
    public void a1() {
        String freshdeskTicketId = "1627";
        ProcessResult processResult = ProcessResult.base();
        JSONObject freshdeskTicketData = null;
        FreshdeskTicketResponse ticketResponse = null;
        try {
            ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
            freshdeskTicketData = ticketResponse.getResponseBody();
            if (freshdeskTicketData != null) {
                OperationBreaker breaker = new OperationBreaker();
                processResult = ticketHandler.synchronizeTicket(freshdeskTicketData, breaker);
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
    }

    @Test
    public void a2() {
        Calendar calendar;
        calendar = Calendar.getInstance();
        calendar.setTimeZone(AppConstants.getUTCTimeZone());
        Util.setDate(calendar, 2021, 8, 26);
        Util.resetTimeToZero(calendar);
        long start = calendar.getTimeInMillis();
        Util.resetTimeToMax(calendar);
        long end = calendar.getTimeInMillis();
        TimeSection timeSection = new TimeSection(start, end);
        log.info("[FD Researching] start. section:{}", timeSection.print());
        //Freshdesk 티켓 조회(동기화 대상 기간에 포함되는 티켓만)
        //	escalation된 티켓이지만 아직 정방향 티켓 생성이 안된 경우 티켓 생성 후 동기화 목록에 추가.
        //	escalation된 티켓이고, 이미 정방향 티켓이 생성되었고 오픈되어 있는 경우 동기화 목록에 추가.
        //	escalation된 티켓이고, 이미 정방향 티켓이 생성되었고 종료된 경우 CSP 티켓의 변경사항을 체크만 함.
        //		(AWS에 티켓이 재오픈되어 있는 경우 다시 오픈하고 동기화 목록에 추가하고, 종료된 상태이면 추가된 대화가 있는지만 확인/동기화하고 동기화 목록에는 포함 안함.)
        final FreshdeskTicketLoader loader = FreshdeskTicketLoader.byDay(AppConstants.CSP_NAME, new Date(timeSection.getStart()), TicketStatus.all);
        int foundTicketCount = 0;
        int newTicketCount = 0;
        long ticketResearchingPeriodEndTime;
        TicketTimeRecord latestTicket = new TicketTimeRecord();

        if (timeSection.hasEndTime()) {
            ticketResearchingPeriodEndTime = timeSection.getEnd();
        } else {
            ticketResearchingPeriodEndTime = System.currentTimeMillis();
        }
        while (loader.hasNext()) {
            JSONArray ticketArray = loader.next();
            foundTicketCount += ticketArray.length();
            log.debug("[FD Researching] {} - foundTicketCount:{}", timeSection.print(), foundTicketCount);
            for (int i = 0; i < ticketArray.length(); i++) {
                try {
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

                        log.debug("[FD Researching] fdTicketId: {} - cspTicketId: {} - status: {}", freshdeskTicketId, cspTicketId, fdTicketStatus);
                        //cspTicketId가 있는 경우 모두 동기화 목록에 넣어서 동기화 시도. (종료된 티켓도 한번은 동기화 체크하도록)
                        //Freshdesk에 종료된 티켓이 CSP에서 재오픈 된 경우에만 Freshdesk 티켓을 오픈 상태로 변경하는 것만 여기에서 처리.
                        if (FreshdeskTicketStatus.isOpen(fdTicketStatus)) { //SP에 Open 상태로 존재하는 티켓 (모니터링 대상 티켓)
                            if (cspTicketId == null) {
                                log.info("[FD Researching] Not linked ticket. Freshdesk {} ==> CSP.", freshdeskTicketId);
                            } else {
                                log.debug("onFoundMonitoringTicket() FD: {} - CSP: {}", freshdeskTicketId, cspTicketId);
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
            log.info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ Read more opened ticket.");
            final FreshdeskTicketLoader openTicketLoader = FreshdeskTicketLoader.byDay(AppConstants.CSP_NAME, new Date(timeSection.getStart()), TicketStatus.opened);
            while (openTicketLoader.hasNext()) {
                JSONArray ticketArray = openTicketLoader.next();
                for (int i = 0; i < ticketArray.length(); i++) {
                    try {
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

                            log.debug("[FD Researching] fdTicketId: {} - cspTicketId: {} - status: {}", freshdeskTicketId, cspTicketId, fdTicketStatus);
                            //cspTicketId가 있는 경우 모두 동기화 목록에 넣어서 동기화 시도. (종료된 티켓도 한번은 동기화 체크하도록)
                            //Freshdesk에 종료된 티켓이 CSP에서 재오픈 된 경우에만 Freshdesk 티켓을 오픈 상태로 변경하는 것만 여기에서 처리.
                            if (FreshdeskTicketStatus.isOpen(fdTicketStatus)) { //SP에 Open 상태로 존재하는 티켓 (모니터링 대상 티켓)
                                if (cspTicketId == null) {
                                    log.info("[FD Researching] Not linked ticket. Freshdesk {} ==> CSP.", freshdeskTicketId);
                                    //CSP에 새로 생성해야할 티켓 목록에 추가.
                                    log.info("[FD Researching] Not linked ticket. Freshdesk {} ==> CSP.", freshdeskTicketId);
                                } else {
                                    log.debug("onFoundMonitoringTicket() FD: {} - CSP: {}", freshdeskTicketId, cspTicketId);
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

        log.info("[FD Researching] end. timeSection:{}, foundTicketCount: {}, ticketResearchingPeriodEndTime: {}, latestTicket: {}", timeSection.print(), foundTicketCount, ticketResearchingPeriodEndTime, latestTicket);
    }

    @Test
    public void a3() {
        TicketErrorNoteMetadata metadata = new TicketErrorNoteMetadata("1111");
        metadata.onFailedTicketCreation("ticket creation failed cause aaaaa");
        metadata.onFailedConversationSync("conversation sync failed cause bbbbb");
        metadata.onFailedTicketStatusChange("ticket status changing failed cause ccccc");
    }

    @Test
    public void a4() {
        TicketErrorNoteMetadata metadata = TicketErrorNoteMetadata.loadErrorNoteMetadata("1111");
        log.debug("metadata: {}", metadata);
        boolean duplicateCreationFailed = metadata.isDuplicatedTicketCreationFailCause("ticket creation failed cause aaaaa");
        boolean duplicateSyncFailed = metadata.isDuplicatedConversationSyncFailCause("conversation sync failed cause bbbbb");
        boolean duplicateStatusFailed = metadata.isDuplicatedTicketStatusChangeFailCause("ticket status changing failed cause ccccc");
        log.debug("aaaaa duplicateCreationFailed: {}", duplicateCreationFailed);
        log.debug("bbbbb duplicateSyncFailed: {}", duplicateSyncFailed);
        log.debug("ccccc duplicateStatusFailed: {}", duplicateStatusFailed);
        duplicateCreationFailed = metadata.isDuplicatedTicketCreationFailCause("ticket creation failed cause 11111");
        duplicateSyncFailed = metadata.isDuplicatedConversationSyncFailCause("conversation sync failed cause 22222");
        duplicateStatusFailed = metadata.isDuplicatedTicketStatusChangeFailCause("ticket status changing failed cause 33333");
        log.debug("11111 duplicateCreationFailed: {}", duplicateCreationFailed);
        log.debug("22222 duplicateSyncFailed: {}", duplicateSyncFailed);
        log.debug("33333 duplicateStatusFailed: {}", duplicateStatusFailed);
    }

    @Test
    public void a5() {
        AppConfig config = AppConfig.getInstance();
        IbmBrandAccount brandAccount = new IbmBrandAccount("72327", "IBM838391", "960a59134dba43b3afa801e9ab7cc587a4f35ad2bcb005257874a8c4a6134443", "zcare.test@gmail.com");
        try {
            String cspTicketId = "140994313";
            FreshdeskTicketBuilder ticketBuilder = ticketHandler.buildFreshdeskTicketBuilder(brandAccount, cspTicketId);
            if (ticketBuilder != null) {
                JSONObject param = ticketBuilder.buildUnplannedEventTicketParameter(null);
                log.info("param: {}", param);
            } else {
                log.error("Failed to build ticket creation for CSP ticket: {} using brand: {}.", cspTicketId, brandAccount.getBrandId());
            }
        } catch (AppInternalError e) {
            e.printStackTrace();
        }
    }


    @Test
    public void checkTicketUpdates() {
        long cspTicketId = 138093128;
        Ticket.Service ibmTicketService = ticketService(cspTicketId);
        List<Update> updates = ibmTicketService.getUpdates();
        if (updates != null) {
            log.debug("updates {}", updates.size());
            for (Update update : updates) {
                IbmService.printIbmUpdate(update);
            }
        } else {
            log.debug("updates is null");
        }
    }

    @Test
    public void checkTicketAttachedFiles() {
        long cspTicketId = 138093128;
        Ticket.Service ibmTicketService = ticketService(cspTicketId);
        List<com.softlayer.api.service.ticket.attachment.File> attachedFiles = ibmTicketService.getAttachedFiles();
        if (attachedFiles != null) {
            log.debug("attachedFiles {}", attachedFiles.size());
            for (com.softlayer.api.service.ticket.attachment.File attachedFile : attachedFiles) {
                IbmService.printIbmAttachmentFile(attachedFile);
            }
        } else {
            log.debug("attachedFiles is null");
        }
    }

    @Test
    public void checkAll() {
        long cspTicketId = 138093128;
        checkTicketUpdates();
        checkTicketAttachedFiles();
    }

    private void onFoundTicketId(String freshdeskTicketId, String cspTicketId) {
        log.debug("FD: {} - CSP: {}", freshdeskTicketId, cspTicketId);
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

}
