package com.sk.bds.ticket.api;

import com.sk.bds.ticket.api.data.model.*;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.freshdesk.*;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.service.*;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.ticket.Update;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.TreeMap;

//@PropertySource(value = "application.properties", encoding = "UTF-8")
//@PropertySource("classpath:application.properties")
//@ActiveProfiles("local")
//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringRunner.class)
//@TestPropertySource(properties = {"my.value=value"})
@TestPropertySource("classpath:application.properties")
@Slf4j
public class TicketCheckTests {
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

    /**
     * freshdeskTicketId를 가지고, cspCaseId를 조회하는 테스트
     * @throws Exception
     */
    private void checkCspTicket(String freshdeskTicketId) throws Exception {
        // String freshdeskTicketId = "40468"; // 40435, 40468

        String supplyCode = CloudZService.SupplyCode.SoftLayer;
        JSONObject allAccounts = CloudZService.getAllAccounts(supplyCode);
        if(allAccounts == null || allAccounts.isEmpty())
            throw new Exception("allAccounts is empty");

        FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
        JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
        if (freshdeskTicketData == null) {
            // 응답헤더의 내용을 티켓에 추가되는 에러 노트와 OG Alert에 추가할 필요는 없고 로그에만 찍히도록 하면 될 것 같습니다.
            // 에러 노트는 Support Portal 운영자, 에이전트 들이 보는 내용이라 굳이 표시하지 않아도 될 듯 합니다. by 김민정 @2022-07-22
            // String xRequestId = FreshdeskService.getXRequestId(ticketResponse);
            throw new Exception("freshdeskTicketData is null");
        }

        if (!freshdeskTicketData.has(FreshdeskTicketField.CustomFields)) {
            throw new Exception("freshdeskTicketData do not have customFields");
        }

        int fdTicketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
        String ticketStatus = FreshdeskTicketStatus.toString(fdTicketStatus);

        JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
        if (customData == null)
            throw new Exception("customData is null");

        String csp = customData.optString(FreshdeskTicketField.CfCsp);
        if (!TicketUtil.isValidCsp(csp)) {
            throw new Exception("This ticket is not IBM Ticket.");
        }

        String cspAccount = customData.optString(FreshdeskTicketField.CfCspAccount);
        if (StringUtils.isEmpty(cspAccount))
            throw new Exception("cspAccount is Empty");

        String escalation = customData.optString(FreshdeskTicketField.CfEscalation);
        FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(cspAccount);
        if (!accountField.isValid())
            throw new Exception("invalid freshdeskCspAccountField");

        String cspAccountId = accountField.getAccountId();
        if(StringUtils.isEmpty(cspAccountId))
            throw new Exception("cspAccountId is Empty");

        // cspTicketIdRequired == true인 조건
//        boolean cspTicketIdRequired = "Open".equals(ticketStatus) && !StringUtils.isEmpty(cspAccountId) && "Y".equals(escalation);
//        cspTicketIdRequired = true;
//        if (!cspTicketIdRequired) {
//            log.warn("cspTicketIdRequired is False, ticketStatus:{}, cspAccountId{}, escalation:{}"
//                    , ticketStatus, cspAccountId, escalation);
//            return;
//        }

        TicketMetadata ticketMetadata = TicketMetadata.build(freshdeskTicketData, true); //operator.getMonitoringTicket(freshdeskTicketId);
        if (ticketMetadata == null)
            throw new Exception("ticketMetadata is null, build fail");

        CloudZCspApiInfo cspApiInfo = CloudZService.getCspApiInfo(ticketMetadata.getCspAccountEmail(), ticketMetadata.getCspAccountId());
        if (cspApiInfo == null)
            throw new Exception("cspApiInfo is null");

        ApiClient ibmClient = cspApiInfo.buildApiClient();
        if  (ibmClient == null)
            throw new Exception("ibmClient is null");

        // String cspCaseId = customData.optString(FreshdeskTicketField.CfCspCaseId);
        String cspCaseId = ticketMetadata.getCspTicketId();
        if (StringUtils.isEmpty(cspCaseId))
            throw new Exception("cspCaseId is Empty");

        Ticket.Service ibmTicketService = Ticket.service(ibmClient, Long.valueOf(cspCaseId));

        com.softlayer.api.service.ticket.Status ibmTicketStatus = ibmTicketService.getStatus();
        if (ibmTicketStatus == null) {
            throw new Exception("ibmTicketStatus is null");
        }

        Update firstUpdate = ibmTicketService.getFirstUpdate();
        if (firstUpdate == null) {
            throw new Exception("firstUpdate is null");
        }

        TreeMap<String, String> cspConversationMap = new TreeMap();
        TreeMap<String, String> freshdeskConversationMap = new TreeMap();
        long ibmCaseBodyId = firstUpdate.getId();
        for (Update update : ibmTicketService.getUpdates()) {
            if (update == null) continue;
            if (ibmCaseBodyId == update.getId()) { //티켓 본문은 동기화할 대화에서 제외.
                continue;
            }
            if (TicketUtil.isAttachmentNote(update)) { ////첨부파일에 대한 Note는 동기화할 대화에서 제외.
                continue;
            }
            String ibmBodyContent = update.getEntry();
            if (ibmBodyContent == null || !TicketUtil.isTaggedFreshdesk(ibmBodyContent)) { //Conversation is originated on IBM.
                String conversationId = String.valueOf(update.getId());
                cspConversationMap.put(conversationId, conversationId);
            }

            if (ibmBodyContent != null && TicketUtil.isTaggedFreshdesk(ibmBodyContent)) { //Conversation is originated on Freshdesk.
              String conversationId = TicketUtil.getIdFromBodyTag(ibmBodyContent, AppConstants.CREATED_FROM_FRESHDESK, AppConstants.CSP_LINEFEED);
              freshdeskConversationMap.put(conversationId, conversationId);
            }
        }

        StringBuffer cspConversationIDs = new StringBuffer();
        for(String key : cspConversationMap.keySet()) {
            String value = cspConversationMap.get(key);
            cspConversationIDs.append(value + ",");
        }

        StringBuffer freshdeskConversationIDs = new StringBuffer();
        for(String key : freshdeskConversationMap.keySet()) {
            String value = freshdeskConversationMap.get(key);
            freshdeskConversationIDs.append(value + ",");
        }

        String freshdeskServicePortalEndpoint = propertyReader.getFreshdeskServicePortalEndpoint();
        String cspStatus = ibmTicketStatus.getName();
        Long cspTicketId = firstUpdate.getTicketId();
        Date cspCreateDate = firstUpdate.getCreateDate().getTime();
        String cspEntry = firstUpdate.getEntry();

        log.info("freshdeskServicePortalEndpoint:{}, freshdeskTicketId:{}, cspTicketId:{}, cspStatus:{}, cspCreateDate:{}, freshdeskConversationIDs:{}, cspConversationIDs:{}"
                , freshdeskServicePortalEndpoint, freshdeskTicketId, cspTicketId, cspStatus, cspCreateDate, freshdeskConversationIDs.toString(), cspConversationIDs.toString());
    }

    @Test
    public void Test() throws Exception {
        String[] freshdeskTicketIds = {"40435", "40468"};
        for (String freshdeskTicketId : freshdeskTicketIds) {
            checkCspTicket(freshdeskTicketId);
        }
    }

}
