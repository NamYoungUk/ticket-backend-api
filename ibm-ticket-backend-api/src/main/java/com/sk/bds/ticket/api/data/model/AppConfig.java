package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.sk.bds.ticket.api.util.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;

@Slf4j
@Data
public class AppConfig {
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static SystemEnv systemEnv = new SystemEnv();

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final boolean CONFIGURATION_ENCRYPTION = true;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final String AppConfigFileName = "ticket.conf";
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final String AppDeployInfoFileName = "deploy-info.dat";
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final String TicketSyncRecordFileName = "sync-record.dat";
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final String TicketSyncRevRecordFileName = "sync-rev-record.dat";
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final String TicketPublicUrlMappingFileName = "ticket-url-map.json";
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final String AES_ENCRYPT_KEY = "ibm.ticket.config";

    @JsonIgnore
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static PropertyReader appProperty;

    @JsonIgnore
    @Setter(AccessLevel.PRIVATE)
    private static AppConfig defaultConfigInstance;

    @Setter(AccessLevel.PRIVATE)
    private CommonConfig commonConfig;
    @Setter(AccessLevel.PRIVATE)
    private CloudZConfig cloudzConfig;
    @Setter(AccessLevel.PRIVATE)
    private OpsgenieConfig opsgenieConfig;
    @Setter(AccessLevel.PRIVATE)
    private FreshdeskConfig freshdeskConfig;
    @Setter(AccessLevel.PRIVATE)
    private SlackConfig slackConfig;
    @Setter(AccessLevel.PRIVATE)
    private IBMConfig ibmConfig;
    @Setter(AccessLevel.PRIVATE)
    private MessageTemplate messageTemplate;
    @Setter(AccessLevel.PRIVATE)
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private Date configuredTime;

    @Data
    @Setter(AccessLevel.PRIVATE)
    public static class SystemEnv {
        String appVersion;
        String appPath;
        String appDataPath;
        String appConfigPath;
        String appReportPath;
        String appMetadataPath;
        String appLogPath;

        public SystemEnv() {
            //TODO. Important!! synchronizes with Dockerfile Environment
            //실제 배포되는 Kubernetes환경에서 PVC 마운트 경로가 /ticket/app 으로 변경되면 코드에서 경로 변경.
            appVersion = "1.1";
            appPath = "/ticket/app";
            appDataPath = "/ticket/appdata";
            appConfigPath = appDataPath + "/conf"; //"/ticket/appdata/conf";
            appReportPath = appDataPath + "/reports"; //"/ticket/appdata/reports";
            appMetadataPath = appDataPath + "/meta"; //"/ticket/appdata/meta";
            appLogPath = appDataPath + "/logs"; //"/ticket/appdata/logs";
        }
    }

    @Data
    @Setter(AccessLevel.PRIVATE)
    public class CommonConfig {
        String serviceEndPoint;
        String serviceStage;
        boolean escalationCheckEnabled;
        boolean syncEnabled;
        long syncInterval;
        boolean reverseSyncEnabled;
        long reverseSyncCheckingSleepTime;
        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        Date syncTargetTime;
        int syncConversationMax;
        int syncConcurrentMax;
        boolean betaTestEnabled;
        List<String> ignoreTicketCreationFreshdeskIds;
        List<String> ignoreTicketCreationCspIds;
        List<String> ignoreTicketCreationFreshdeskTitles;
        List<String> ignoreTicketCreationCspTitles;
        List<String> ignoreTicketSyncFreshdeskIds;
        List<String> ignoreTicketSyncCspIds;
        boolean ignoreTicketCreationFreshdeskIdEnabled;
        boolean ignoreTicketCreationCspIdEnabled;
        boolean ignoreTicketCreationFreshdeskTitleEnabled;
        boolean ignoreTicketCreationCspTitleEnabled;
        boolean ignoreTicketSyncFreshdeskIdEnabled;
        boolean ignoreTicketSyncCspIdEnabled;
        List<Agent> l1Agents;
        List<Agent> l2Agents;
        List<String> betaTesters;
        boolean slaReportEnabled;
        int slaTargetDays;
        int slaTimeL1;
        int slaTimeL2;
        int requiredErrorCountForReporting;
        String serviceApiAccessKey;
        boolean httpRequestTimeoutEnabled;
        int httpRequestTimeout;

        private void init() {
            syncInterval = AppConstants.TICKET_SYNC_INTERVAL_TIME_MILLIS;
            reverseSyncCheckingSleepTime = AppConstants.TICKET_REVERSE_SYNC_CHECKING_SLEEP_TIME_MILLIS;
            slaTargetDays = AppConstants.SLA_TARGET_DAYS;
            slaTimeL1 = AppConstants.SLA_TIME_L1;
            slaTimeL2 = AppConstants.SLA_TIME_L2;
            ignoreTicketCreationFreshdeskIds = new ArrayList<>();
            ignoreTicketCreationCspIds = new ArrayList<>();
            ignoreTicketCreationFreshdeskTitles = new ArrayList<>();
            ignoreTicketCreationCspTitles = new ArrayList<>();
            ignoreTicketSyncFreshdeskIds = new ArrayList<>();
            ignoreTicketSyncCspIds = new ArrayList<>();
            l1Agents = new ArrayList<>();
            l2Agents = new ArrayList<>();
            betaTesters = new ArrayList<>();
        }

        public CommonConfig() {
            init();
        }

        public CommonConfig(PropertyReader prop) {
            init();
            if (prop != null) {
                setServiceEndPoint(prop.getServiceEndpoint());
                setServiceStage(prop.getServiceStage());
                setEscalationCheckEnabled(prop.isEscalationCheckEnabled());
                setSyncEnabled(prop.isSyncEnabled());
                setSyncInterval(prop.getSyncInterval());
                setReverseSyncEnabled(prop.isReverseSyncEnabled());
                setReverseSyncCheckingSleepTime(prop.getReverseSyncCheckingSleepTime());
                setSyncTargetTimeByFormattedTime(prop.getSyncTargetTime());
                setSyncConversationMax(prop.getSyncConversationMax());
                setSyncConcurrentMax(prop.getSyncConcurrentMax());
                setRequiredErrorCountForReporting(prop.getRequiredErrorCountForReporting());
                setBetaTestEnabled(prop.isBetaTestEnabled());

                String ignoreTicketCreationFreshdeskIdsText = prop.getIgnoreTicketCreationFreshdeskIds();
                if (ignoreTicketCreationFreshdeskIdsText != null && ignoreTicketCreationFreshdeskIdsText.trim().length() > 0) {
                    String[] ticketIdArray = ignoreTicketCreationFreshdeskIdsText.split(";");
                    for (String ticketId : ticketIdArray) {
                        if (!ignoreTicketCreationFreshdeskIds.contains(ticketId)) {
                            ignoreTicketCreationFreshdeskIds.add(ticketId);
                        }
                    }
                }

                String ignoreTicketCreationCspIdsText = prop.getIgnoreTicketCreationCspIds();
                if (ignoreTicketCreationCspIdsText != null && ignoreTicketCreationCspIdsText.trim().length() > 0) {
                    String[] ticketIdArray = ignoreTicketCreationCspIdsText.split(";");
                    for (String ticketId : ticketIdArray) {
                        if (!ignoreTicketCreationCspIds.contains(ticketId)) {
                            ignoreTicketCreationCspIds.add(ticketId);
                        }
                    }
                }

                String ignoreTicketCreationFreshdeskTitlesText = prop.getIgnoreTicketCreationFreshdeskTitles();
                if (ignoreTicketCreationFreshdeskTitlesText != null && ignoreTicketCreationFreshdeskTitlesText.trim().length() > 0) {
                    String[] ticketTitles = ignoreTicketCreationFreshdeskTitlesText.split("\n");
                    for (String title : ticketTitles) {
                        if (title.trim().length() > 1) {
                            if (!ignoreTicketCreationFreshdeskTitles.contains(title)) {
                                ignoreTicketCreationFreshdeskTitles.add(title);
                            }
                        }
                    }
                }

                String ignoreTicketCreationCspTitlesText = prop.getIgnoreTicketCreationCspTitles();
                if (ignoreTicketCreationCspTitlesText != null && ignoreTicketCreationCspTitlesText.trim().length() > 0) {
                    String[] ticketTitles = ignoreTicketCreationCspTitlesText.split("\n");
                    for (String title : ticketTitles) {
                        if (title.trim().length() > 1) {
                            if (!ignoreTicketCreationCspTitles.contains(title)) {
                                ignoreTicketCreationCspTitles.add(title);
                            }
                        }
                    }
                }

                String ignoreTicketSyncFreshdeskIdsText = prop.getIgnoreTicketSyncFreshdeskIds();
                if (ignoreTicketSyncFreshdeskIdsText != null && ignoreTicketSyncFreshdeskIdsText.trim().length() > 0) {
                    String[] ticketIdArray = ignoreTicketSyncFreshdeskIdsText.split(";");
                    for (String ticketId : ticketIdArray) {
                        if (!ignoreTicketSyncFreshdeskIds.contains(ticketId)) {
                            ignoreTicketSyncFreshdeskIds.add(ticketId);
                        }
                    }
                }

                String ignoreTicketSyncCspIdsText = prop.getIgnoreTicketSyncCspIds();
                if (ignoreTicketSyncCspIdsText != null && ignoreTicketSyncCspIdsText.trim().length() > 0) {
                    String[] ticketIdArray = ignoreTicketSyncCspIdsText.split(";");
                    for (String ticketId : ticketIdArray) {
                        if (!ignoreTicketSyncCspIds.contains(ticketId)) {
                            ignoreTicketSyncCspIds.add(ticketId);
                        }
                    }
                }

                setIgnoreTicketCreationFreshdeskIdEnabled(prop.isIgnoreTicketCreationFreshdeskIdEnabled());
                setIgnoreTicketCreationCspIdEnabled(prop.isIgnoreTicketCreationCspIdEnabled());
                setIgnoreTicketCreationFreshdeskTitleEnabled(prop.isIgnoreTicketCreationFreshdeskTitleEnabled());
                setIgnoreTicketCreationCspTitleEnabled(prop.isIgnoreTicketCreationCspTitleEnabled());
                setIgnoreTicketSyncFreshdeskIdEnabled(prop.isIgnoreTicketSyncFreshdeskIdEnabled());
                setIgnoreTicketSyncCspIdEnabled(prop.isIgnoreTicketSyncCspIdEnabled());

                String l1AgentsString = prop.getL1Agents();
                if (l1AgentsString != null && l1AgentsString.trim().length() > 0) {
                    String[] l1Array = l1AgentsString.split(";");
                    for (String info : l1Array) {
                        String[] param = info.split("/");
                        if (param.length == 2) {
                            Agent agent = new Agent(param[0], param[1]);
                            agent.setLevel(AgentLevel.l1);
                            if (!l1Agents.contains(agent)) {
                                l1Agents.add(agent);
                            }
                        }
                    }
                }
                String l2AgentsString = prop.getL2Agents();
                if (l2AgentsString != null && l2AgentsString.trim().length() > 0) {
                    String[] l2Array = l2AgentsString.split(";");
                    for (String info : l2Array) {
                        String[] param = info.split("/");
                        if (param.length == 2) {
                            Agent agent = new Agent(param[0], param[1]);
                            agent.setLevel(AgentLevel.l2);
                            if (!l2Agents.contains(agent)) {
                                l2Agents.add(agent);
                            }
                        }
                    }
                }

                String betaTestersString = prop.getBetaTestersString();
                if (betaTestersString != null && betaTestersString.trim().length() > 0) {
                    String[] testerArray = betaTestersString.split(";");
                    for (String tester : testerArray) {
                        betaTesters.add(tester);
                    }
                }

                setSlaReportEnabled(prop.isSlaReportEnabled());
                setServiceApiAccessKey(prop.getServiceApiAccessKey());
                setSlaTargetDays(prop.getSlaTargetDays());
                setSlaTimeL1(prop.getSlaTimeL1());
                setSlaTimeL2(prop.getSlaTimeL2());

                setHttpRequestTimeoutEnabled(prop.isHttpRequestTimeoutEnabled());
                setHttpRequestTimeout(prop.getHttpRequestTimeout());
            }
        }

        public void setSyncTargetTimeByFormattedTime(String formattedTimeString) {
            if (formattedTimeString != null) {
                DateFormat localTimeFormat = TicketUtil.getLocalDateFormat();
                try {
                    Date parsedTime = localTimeFormat.parse(formattedTimeString);
                    setSyncTargetTime(parsedTime);
                } catch (ParseException e) {
                    log.error("sync target time format error. {}", e);
                    setSyncTargetTime(new Date());
                }
            }
        }
    }

    @Data
    @Setter(AccessLevel.PRIVATE)
    public static class CloudZConfig {
        String apiEndpoint = null;
        String clientId = null;
        String clientSecret = null;
        boolean userUseMasterAccount;

        public CloudZConfig() {
        }

        public CloudZConfig(PropertyReader prop) {
            if (prop != null) {
                setApiEndpoint(prop.getCloudzApiEndpoint());
                setClientId(prop.getCloudzClientId());
                setClientSecret(prop.getCloudzClientSecret());
                setUserUseMasterAccount(prop.isCloudzUserUseMasterAccount());
            }
        }
    }

    @Data
    @Setter(AccessLevel.PRIVATE)
    public static class OpsgenieConfig {
        String apiEndpoint;
        String apiKey;
        String alertTeam;

        public OpsgenieConfig() {
        }

        public OpsgenieConfig(PropertyReader prop) {
            if (prop != null) {
                setApiEndpoint(prop.getOpsgenieApiEndpoint());
                setApiKey(prop.getOpsgenieApiKey());
                setAlertTeam(prop.getOpsgenieApiAlertTeam());
            }
        }
    }

    @Data
    @Setter(AccessLevel.PRIVATE)
    public static class FreshdeskConfig {
        String servicePortalEndpoint;
        String apiEndpoint;
        String apiKey;
        long attachmentMaxSize;
        boolean openTicketStatusIncludesPending;
        boolean closedTicketStatusIncludesResolved;

        public FreshdeskConfig() {
        }

        public FreshdeskConfig(PropertyReader prop) {
            if (prop != null) {
                setServicePortalEndpoint(prop.getFreshdeskServicePortalEndpoint());
                setApiEndpoint(prop.getFreshdeskApiEndpoint());
                setApiKey(prop.getFreshdeskApiKey());
                setAttachmentMaxSize(prop.getFreshdeskAttachmentTotalSizeMax());
                setOpenTicketStatusIncludesPending(prop.isFreshdeskOpenTicketStatusIncludesPending());
                setClosedTicketStatusIncludesResolved(prop.isFreshdeskClosedTicketStatusIncludesResolved());
            }
        }
    }

    @Data
    @Setter(AccessLevel.PRIVATE)
    public static class SlackConfig {
        String workSpace;
        String apiToken;

        public SlackConfig() {
        }

        public SlackConfig(PropertyReader prop) {
            if (prop != null) {
                setWorkSpace(prop.getSlackWorkSpace());
                setApiToken(prop.getSlackApiToken());
            }
        }
    }

    @Data
    @Setter(AccessLevel.PRIVATE)
    public static class IBMConfig {
        String consoleEndpoint;
        String softlayerApiEndPoint;
        String accountId;
        String apiKey;
        int updateBodyMaxLength;
        int attachmentTotalSizeMax;
        int attachmentContentSizeMax;
        int attachmentCountMax;
        String agentL1Email;
        List<String> unplannedEvents;
        List<IbmBrandAccount> reverseSyncAccounts;
        long softLayerApiDelayTime;

        public IBMConfig() {
        }

        public IBMConfig(PropertyReader prop) {
            if (prop != null) {
                setConsoleEndpoint(prop.getIbmConsoleEndpoint());
                setSoftlayerApiEndPoint(prop.getIbmSoftLayerApiEndpoint());
                setAccountId(prop.getIbmAccountId());
                setApiKey(prop.getIbmAccountKey());
                setUpdateBodyMaxLength(prop.getIbmUpdateBodyMaxLength());
                setAttachmentTotalSizeMax(prop.getIbmAttachmentTotalSizeMax());
                setAttachmentContentSizeMax(prop.getIbmAttachmentContentSizeMax());
                setAttachmentCountMax(prop.getIbmAttachmentCountMax());
                setAgentL1Email(prop.getIbmAgentL1Email());
                setSoftLayerApiDelayTime(prop.getIbmSoftLayerApiDelayTime());

                //ReverseSyncAccount
                List<IbmBrandAccount> accounts = new ArrayList<>();
                accounts.add(new IbmBrandAccount(
                        prop.getIbmReverseSyncBrandId1(),
                        prop.getIbmReverseSyncAccountId1(),
                        prop.getIbmReverseSyncAccountKey1(),
                        prop.getIbmReverseSyncEmail1()
                ));
                accounts.add(new IbmBrandAccount(
                        prop.getIbmReverseSyncBrandId2(),
                        prop.getIbmReverseSyncAccountId2(),
                        prop.getIbmReverseSyncAccountKey2(),
                        prop.getIbmReverseSyncEmail2()
                ));
                setReverseSyncAccounts(accounts);

                //UnplannedEvent
                String keywords = prop.getIbmUnplannedEventKeywords();
                List<String> events = new ArrayList<>();
                if (keywords != null && keywords.trim().length() > 0) {
                    String[] keywordArray = keywords.split("\n");
                    for (String keyword : keywordArray) {
                        events.add(keyword);
                    }
                }
                setUnplannedEvents(events);
            }
        }
    }

    @Data
    @Setter(AccessLevel.PRIVATE)
    public static class MessageTemplate {
        String skSupportPortalUsingRequest;
        String agentL1TicketTemplate;

        public MessageTemplate() {
        }

        public MessageTemplate(PropertyReader prop) {
            if (prop != null) {
                setSkSupportPortalUsingRequest(prop.getSkSupportPortalUsingRequestMessage());
                setAgentL1TicketTemplate(prop.getAgentL1TicketTemplate());
            }
        }
    }

    public static void store(AppConfig config) {
        if (config != null) {
            config.storeConfiguration();
        }
    }

    private static class LazyHolder {
        private static final AppConfig AppConfigInstance = new AppConfig();
    }

    public static AppConfig getInstance() {
        return LazyHolder.AppConfigInstance;
    }

    public static AppConfig getDefaultConfig() {
        if (defaultConfigInstance == null) {
            createDefault(appProperty);
        }
        return defaultConfigInstance;
    }

    private static boolean isConfigurableField(Class clazz) {
        if (clazz.getSimpleName().equals(CommonConfig.class.getSimpleName())
                || clazz.getSimpleName().equals(CloudZConfig.class.getSimpleName())
                || clazz.getSimpleName().equals(OpsgenieConfig.class.getSimpleName())
                || clazz.getSimpleName().equals(FreshdeskConfig.class.getSimpleName())
                || clazz.getSimpleName().equals(SlackConfig.class.getSimpleName())
                || clazz.getSimpleName().equals(IBMConfig.class.getSimpleName())
                || clazz.getSimpleName().equals(MessageTemplate.class.getSimpleName())) {
            return true;
        } else {
            return false;
        }
    }

    public static Map<String, Class> getConfigGroupMap() {
        Map<String, Class> classMap = new HashMap<>();
        Field[] fields = AppConfig.class.getDeclaredFields();
        for (Field f : fields) {
            if (isConfigurableField(f.getType())) {
                classMap.put(f.getName(), f.getType());
            }
        }
        return classMap;
    }

    public static Map<String, Map<String, String>> getConfigDetailMap() {
        Map<String, Map<String, String>> detailMap = new HashMap<>();
        Field[] configGroups = AppConfig.class.getDeclaredFields();
        for (Field group : configGroups) {
            if (isConfigurableField(group.getType())) {
                Field[] configFields = group.getType().getDeclaredFields();
                if (configFields != null) {
                    Map<String, String> typeMap = new HashMap<>();
                    for (Field field : configFields) {
                        typeMap.put(field.getName(), field.getType().getSimpleName());
                    }
                    detailMap.put(group.getName(), typeMap);
                }
            }
        }
        return detailMap;
    }

    public static AppConfig getOverlapped(JSONObject inputSource) {
        if (inputSource != null) {
            Map<String, Class> classMap = getConfigGroupMap();
            AppConfig current = AppConfig.getInstance();
            try {
                String objectString = JsonUtil.marshal(current);
                JSONObject target = new JSONObject(objectString);
                for (String configGroup : classMap.keySet()) {
                    if (inputSource.has(configGroup)) {
                        try {
                            Object item = inputSource.get(configGroup);
                            if (item instanceof JSONObject) {
                                JSONObject groupConfig = inputSource.getJSONObject(configGroup);
                                JSONObject groupTarget = target.getJSONObject(configGroup);
                                for (String field : groupConfig.keySet()) {
                                    try {
                                        groupTarget.put(field, groupConfig.get(field));
                                    } catch (JSONException e) {
                                        log.error("error: {}", e);
                                    }
                                }
                            } else {
                                target.put(configGroup, item);
                            }
                        } catch (JSONException e) {
                            log.error("error: {}", e);
                        }
                    }
                }
                AppConfig replacedConfig = JsonUtil.unmarshal(target.toString(), AppConfig.class);
                return replacedConfig;
            } catch (IOException e) {
                log.error("error: {}", e);
            }
        }
        return null;
    }

    private static void createDefault(PropertyReader property) {
        if (defaultConfigInstance == null) {
            defaultConfigInstance = new AppConfig();
        }
        defaultConfigInstance.apply(property);
    }

    private static void checkSystemEnv() {
        log.debug("@@@ System Environment @@@");
        Map<String, String> envMap = System.getenv();
        for (Map.Entry<String, String> entry : envMap.entrySet()) {
            log.debug("=============================================");
            log.debug("Name:" + entry.getKey() + ", Value:" + entry.getValue());
        }
        log.debug("=============================================");
        log.debug("System Env contains (TICKET_APP_VER):{}", envMap.containsKey(AppConstants.TICKET_APP_VER));
        log.debug("System Env contains (TICKET_APP_PATH):{}", envMap.containsKey(AppConstants.TICKET_APP_PATH));
        log.debug("System Env contains (TICKET_APP_DATA_PATH):{}", envMap.containsKey(AppConstants.TICKET_APP_DATA_PATH));
        log.debug("System Env contains (TICKET_APP_CONFIG_PATH):{}", envMap.containsKey(AppConstants.TICKET_APP_CONFIG_PATH));
        log.debug("System Env contains (TICKET_APP_REPORT_PATH):{}", envMap.containsKey(AppConstants.TICKET_APP_REPORT_PATH));
        log.debug("System Env contains (TICKET_APP_LOG_PATH):{}", envMap.containsKey(AppConstants.TICKET_APP_LOG_PATH));

        if (envMap.containsKey(AppConstants.TICKET_APP_VER)) {
            String appVersion = envMap.get(AppConstants.TICKET_APP_VER);
            systemEnv.setAppVersion(appVersion);
        }
        if (envMap.containsKey(AppConstants.TICKET_APP_PATH)) {
            String appPath = envMap.get(AppConstants.TICKET_APP_PATH);
            systemEnv.setAppPath(appPath);
        }
        if (envMap.containsKey(AppConstants.TICKET_APP_DATA_PATH)) {
            String appDataPath = envMap.get(AppConstants.TICKET_APP_DATA_PATH);
            systemEnv.setAppDataPath(appDataPath);
        }
        if (envMap.containsKey(AppConstants.TICKET_APP_CONFIG_PATH)) {
            String appConfigPath = envMap.get(AppConstants.TICKET_APP_CONFIG_PATH);
            systemEnv.setAppConfigPath(appConfigPath);
        }
        if (envMap.containsKey(AppConstants.TICKET_APP_REPORT_PATH)) {
            String appReportPath = envMap.get(AppConstants.TICKET_APP_REPORT_PATH);
            systemEnv.setAppReportPath(appReportPath);
        }
        if (envMap.containsKey(AppConstants.TICKET_APP_LOG_PATH)) {
            String appLogPath = envMap.get(AppConstants.TICKET_APP_LOG_PATH);
            systemEnv.setAppLogPath(appLogPath);
        }

        File dir = new File(systemEnv.getAppDataPath());
        if (!dir.exists()) {
            log.info("create directory " + dir.getPath());
            dir.mkdirs();
        }
        dir = new File(systemEnv.getAppConfigPath());
        if (!dir.exists()) {
            log.info("create directory " + dir.getPath());
            dir.mkdirs();
        }
        dir = new File(systemEnv.getAppReportPath());
        if (!dir.exists()) {
            log.info("create directory " + dir.getPath());
            dir.mkdirs();
        }
        dir = new File(systemEnv.getAppMetadataPath());
        if (!dir.exists()) {
            log.info("create directory " + dir.getPath());
            dir.mkdirs();
        }
        dir = new File(getAppErrorNotePath());
        if (!dir.exists()) {
            log.info("create directory " + dir.getPath());
            dir.mkdirs();
        }
        dir = new File(systemEnv.getAppLogPath());
        if (!dir.exists()) {
            log.info("create directory " + dir.getPath());
            dir.mkdirs();
        }
    }

    public static void initialize(PropertyReader property) {
        appProperty = property;
        checkSystemEnv();
        createDefault(property);

        if (!getInstance().load()) {
            getInstance().apply(property);
            getInstance().storeConfiguration();
        }
    }

    private AppConfig() {
    }

    private void apply(PropertyReader property) {
        configuredTime = new Date();
        this.commonConfig = new CommonConfig(property);
        this.cloudzConfig = new CloudZConfig(property);
        this.opsgenieConfig = new OpsgenieConfig(property);
        this.freshdeskConfig = new FreshdeskConfig(property);
        this.slackConfig = new SlackConfig(property);
        this.ibmConfig = new IBMConfig(property);
        this.messageTemplate = new MessageTemplate(property);
    }

    public void apply(AppConfig config) {
        if (config != null) {
            this.configuredTime = config.getConfiguredTime();
            this.commonConfig = config.getCommonConfig();
            this.cloudzConfig = config.getCloudzConfig();
            this.opsgenieConfig = config.getOpsgenieConfig();
            this.freshdeskConfig = config.getFreshdeskConfig();
            this.slackConfig = config.getSlackConfig();
            this.ibmConfig = config.getIbmConfig();
            this.messageTemplate = config.getMessageTemplate();
            log.error("AppConfig apply() - applied successfully.");
        } else {
            log.error("AppConfig apply() - apply failed.");
        }
    }

    private boolean load() {
        AppConfig config = getLastConfigured();
        //저장된 config를 정상적으로 읽었다면 모든 설정 값이 저장된 상태라고 판단한다.
        //따라서, 저장된 모든 설정 값을 그대로 현재 설정 값으로 대입한다.
        if (config != null) {
            this.configuredTime = config.getConfiguredTime();
            this.commonConfig = config.getCommonConfig();
            this.cloudzConfig = config.getCloudzConfig();
            this.opsgenieConfig = config.getOpsgenieConfig();
            this.freshdeskConfig = config.getFreshdeskConfig();
            this.slackConfig = config.getSlackConfig();
            this.ibmConfig = config.getIbmConfig();
            this.messageTemplate = config.getMessageTemplate();
            log.info("AppConfig load() - saved config loaded successfully.");
            return true;
        } else {
            log.error("AppConfig load() - saved config loading failed.");
            return false;
        }
    }

    @JsonIgnore
    private static boolean mergeNewFields(JSONObject source, JSONObject target) throws JSONException {
        boolean foundNewField = false;
        for (String key : JSONObject.getNames(source)) {
            Object value = source.get(key);
            if (!target.has(key)) {
                // new value for "key":
                log.info("mergeNewFields() - New field found. add field to target. {} - {}", key, value);
                target.put(key, value);
                foundNewField = true;
            } else {
                // existing value for "key" - recursively deep merge:
                if (value instanceof JSONObject) {
                    JSONObject valueJson = (JSONObject) value;
                    if (mergeNewFields(valueJson, target.getJSONObject(key))) {
                        foundNewField = true;
                    }
                } else {
                    //Important!!!
                    //이미 존재하는 필드 인 경우 기존 값을 유지하도록 함. 값을 변경하면 안됨.
                    //target.put(key, value);
                }
            }
        }
        return foundNewField;
    }

    @JsonIgnore
    private static AppConfig getLastConfigured() {
        try {
            String configText = readConfigurationFile();
            log.info("stored configText:{}", configText);
            if (configText != null) {
                JSONObject stored = new JSONObject(configText);
                JSONObject appProperties = new JSONObject(JsonUtil.marshal(getDefaultConfig()));
                //application.properties 에 새로 추가된 필드를 저장된 설정 정보에 필드 추가.
                boolean foundNewField = mergeNewFields(appProperties, stored);
                configText = stored.toString();
                AppConfig config = JsonUtil.unmarshal(configText, AppConfig.class);
                if (foundNewField) {
                    log.info("AppConfig getLastConfigured() - Store configuration for New field found.");
                    config.storeConfiguration();
                }
                log.info("AppConfig getLastConfigured() - loaded successfully.");
                return config;
            }
        } catch (IOException e) {
            Util.ignoreException(e);
            log.error("AppConfig getLastConfigured() - saved config loading failed.");
        }
        return null;
    }

    private void storeConfiguration() {
        try {
            configuredTime = new Date();
            writeConfigurationFile(JsonUtil.marshal(this));
            log.info("AppConfig storeConfiguration() - saved successfully.");
        } catch (IOException e) {
            Util.ignoreException(e);
            log.error("AppConfig storeConfiguration() failed");
        }
    }

    private static String readConfigurationFile() throws IOException {
        String configText = Util.readFile(getAppConfigFilePath());
        if (CONFIGURATION_ENCRYPTION) {
            try {
                log.debug("decrypting...");
                return AesCipher.decrypt(configText, AES_ENCRYPT_KEY);
            } catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException | InvalidKeySpecException e) {
                log.error("configuration decryption failed. {}", e);
            }
        }
        return configText;
    }

    private void writeConfigurationFile(String configText) throws IOException {
        log.info("storing... configText:{}", configText);
        if (configText != null) {
            if (CONFIGURATION_ENCRYPTION) {
                try {
                    String encryptedConfig = AesCipher.encrypt(configText, AES_ENCRYPT_KEY);
                    Util.writeFile(getAppConfigFilePath(), encryptedConfig);
                } catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException | InvalidKeySpecException e) {
                    log.error("configuration encryption failed. {}", e);
                }
            } else {
                Util.writeFile(getAppConfigFilePath(), configText);
            }
        }
    }

    public String textOutput() {
        //String configureText = JsonUtil.marshal(this);
        return JsonUtil.prettyPrint(jsonOutput());
    }

    public JSONObject jsonOutput() {
        try {
            return new JSONObject(JsonUtil.marshal(this));
        } catch (JsonProcessingException e) {
            log.error("error: {}", e);
        }
        return new JSONObject();
    }

    //////////////////////////////////////////////////////
    ///////////// Ticket System Environment
    @JsonIgnore
    public static String getAppDataPath() {
        return systemEnv.getAppDataPath();
    }

    @JsonIgnore
    public static String getAppConfigPath() {
        return systemEnv.getAppConfigPath();
    }

    @JsonIgnore
    public static String getAppLogPath() {
        return systemEnv.getAppLogPath();
    }

    @JsonIgnore
    public static String getAppReportPath() {
        return systemEnv.getAppReportPath();
    }

    @JsonIgnore
    public static String getAppMetadataPath() {
        return systemEnv.getAppMetadataPath();
    }

    @JsonIgnore
    public static String getAppErrorNotePath() {
        //"/ticket/appdata/meta/error-notes";
        return systemEnv.getAppMetadataPath() + "/error-notes";
    }

    @JsonIgnore
    public static String getTicketUrlMappingFilePath() {
        return Util.pathName(systemEnv.getAppMetadataPath(), TicketPublicUrlMappingFileName);
    }

    @JsonIgnore
    private static String getAppConfigFilePath() {
        return Util.pathName(getAppConfigPath(), AppConfigFileName);
    }

    @JsonIgnore
    public static String getAppDeployInfoFilePath() {
        return Util.pathName(getAppConfigPath(), AppDeployInfoFileName);
    }

    @JsonIgnore
    public static String getTicketSyncRecordFilePath() {
        return Util.pathName(getAppConfigPath(), TicketSyncRecordFileName);
    }

    @JsonIgnore
    public static String getTicketReverseSyncRecordFilePath() {
        return Util.pathName(getAppConfigPath(), TicketSyncRevRecordFileName);
    }

    //////////////////////////////////////////////////////
    ///////////// Ticket Service Common
    @JsonIgnore
    public String getServiceEndPoint() {
        return commonConfig.getServiceEndPoint();
    }

    @JsonIgnore
    public String getServiceStage() {
        return commonConfig.getServiceStage();
    }

    @JsonIgnore
    public boolean isLocalStage() {
        return commonConfig.getServiceStage().equalsIgnoreCase(DeployStage.local.name());
    }

    @JsonIgnore
    public boolean isStagingStage() {
        return commonConfig.getServiceStage().equalsIgnoreCase(DeployStage.staging.name());
    }

    @JsonIgnore
    public boolean isProductionStage() {
        return commonConfig.getServiceStage().equalsIgnoreCase(DeployStage.production.name());
    }

    @JsonIgnore
    public boolean isEscalationCheckEnabled() {
        return commonConfig.isEscalationCheckEnabled();
    }

    @JsonIgnore
    public boolean isSyncEnabled() {
        return commonConfig.isSyncEnabled();
    }

    @JsonIgnore
    public long getTicketSyncInterval() {
        return commonConfig.getSyncInterval();
    }

    @JsonIgnore
    public boolean isReverseSyncEnabled() {
        return commonConfig.isReverseSyncEnabled();
    }

    @JsonIgnore
    public long getReverseSyncCheckingSleepTime() {
        return commonConfig.getReverseSyncCheckingSleepTime();
    }

    @JsonIgnore
    public long getTicketSyncTargetTime() {
        return commonConfig.getSyncTargetTime().getTime();
    }

    @JsonIgnore
    public int getTicketSyncConversationMax() {
        return commonConfig.getSyncConversationMax();
    }

    @JsonIgnore
    public int getTicketSyncConcurrentMax() {
        return commonConfig.getSyncConcurrentMax();
    }

    @JsonIgnore
    public int getRequiredErrorCountForReporting() {
        return commonConfig.getRequiredErrorCountForReporting();
    }

    @JsonIgnore
    public int getSlaTargetDays() {
        return commonConfig.getSlaTargetDays();
    }

    @JsonIgnore
    public int getSlaTimeL1() {
        return commonConfig.getSlaTimeL1();
    }

    @JsonIgnore
    public int getSlaTimeL2() {
        return commonConfig.getSlaTimeL2();
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByFreshdeskTicketId(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            if (commonConfig.isIgnoreTicketCreationFreshdeskIdEnabled()) {
                return commonConfig.getIgnoreTicketCreationFreshdeskIds().contains(freshdeskTicketId);
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByCspTicketId(String cspTicketId) {
        if (cspTicketId != null) {
            if (commonConfig.isIgnoreTicketCreationCspIdEnabled()) {
                return commonConfig.getIgnoreTicketCreationCspIds().contains(cspTicketId);
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByFreshdeskTicketTitle(String ticketTitle) {
        if (ticketTitle != null) {
            if (commonConfig.isIgnoreTicketCreationFreshdeskTitleEnabled()) {
                for (String ignoreTitle : commonConfig.getIgnoreTicketCreationFreshdeskTitles()) {
                    if (ticketTitle.startsWith(ignoreTitle)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByCspTicketTitle(String ticketTitle) {
        if (ticketTitle != null) {
            if (commonConfig.isIgnoreTicketCreationCspTitleEnabled()) {
                for (String ignoreTitle : commonConfig.getIgnoreTicketCreationCspTitles()) {
                    if (ticketTitle.startsWith(ignoreTitle)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isIgnoreTicketSyncByFreshdeskTicketId(String freshdeskTicketId) {
        if (freshdeskTicketId != null) {
            if (commonConfig.isIgnoreTicketSyncFreshdeskIdEnabled()) {
                return commonConfig.getIgnoreTicketSyncFreshdeskIds().contains(freshdeskTicketId);
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isIgnoreTicketSyncByCspTicketId(String cspTicketId) {
        if (cspTicketId != null) {
            if (commonConfig.isIgnoreTicketSyncCspIdEnabled()) {
                return commonConfig.getIgnoreTicketSyncCspIds().contains(cspTicketId);
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByFreshdeskTicketIdEnabled() {
        return commonConfig.isIgnoreTicketCreationFreshdeskIdEnabled();
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByCspTicketIdEnabled() {
        return commonConfig.isIgnoreTicketCreationCspIdEnabled();
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByFreshdeskTicketTitleEnabled() {
        return commonConfig.isIgnoreTicketCreationFreshdeskTitleEnabled();
    }

    @JsonIgnore
    public boolean isIgnoreTicketCreationByCspTicketTitleEnabled() {
        return commonConfig.isIgnoreTicketCreationCspTitleEnabled();
    }

    @JsonIgnore
    public boolean isIgnoreTicketSyncByFreshdeskIdEnabled() {
        return commonConfig.isIgnoreTicketSyncFreshdeskIdEnabled();
    }

    @JsonIgnore
    public boolean isIgnoreTicketSyncByCspIdEnabled() {
        return commonConfig.isIgnoreTicketSyncCspIdEnabled();
    }

    @JsonIgnore
    public void addIgnoreTicketCreationFreshdeskTicketId(String ticketId) {
        if (ticketId != null) {
            List<String> ignoreList = commonConfig.getIgnoreTicketCreationFreshdeskIds();
            if (!ignoreList.contains(ticketId)) {
                ignoreList.add(ticketId);
            }
        }
    }

    @JsonIgnore
    public void removeIgnoreTicketCreationFreshdeskTicketId(String ticketId) {
        if (ticketId != null) {
            commonConfig.getIgnoreTicketCreationFreshdeskIds().remove(ticketId);
        }
    }

    @JsonIgnore
    public void clearIgnoreTicketCreationFreshdeskTicketIds() {
        commonConfig.getIgnoreTicketCreationFreshdeskIds().clear();
    }

    @JsonIgnore
    public void addIgnoreTicketCreationCspTicketId(String ticketId) {
        if (ticketId != null) {
            List<String> ignoreList = commonConfig.getIgnoreTicketCreationCspIds();
            if (!ignoreList.contains(ticketId)) {
                ignoreList.add(ticketId);
            }
        }
    }

    @JsonIgnore
    public void removeIgnoreTicketCreationCspTicketId(String ticketId) {
        if (ticketId != null) {
            commonConfig.getIgnoreTicketCreationCspIds().remove(ticketId);
        }
    }

    @JsonIgnore
    public void clearIgnoreTicketCreationCspTicketIds() {
        commonConfig.getIgnoreTicketCreationCspIds().clear();
    }

    @JsonIgnore
    public void addIgnoreTicketCreationFreshdeskTicketTitle(String ticketTitle) {
        if (ticketTitle != null) {
            List<String> ignoreList = commonConfig.getIgnoreTicketCreationFreshdeskTitles();
            if (!ignoreList.contains(ticketTitle)) {
                ignoreList.add(ticketTitle);
            }
        }
    }

    @JsonIgnore
    public void removeIgnoreTicketCreationFreshdeskTicketTitle(String ticketTitle) {
        if (ticketTitle != null) {
            commonConfig.getIgnoreTicketCreationFreshdeskTitles().remove(ticketTitle);
        }
    }

    @JsonIgnore
    public void clearIgnoreTicketCreationFreshdeskTicketTitles() {
        commonConfig.getIgnoreTicketCreationFreshdeskTitles().clear();
    }

    @JsonIgnore
    public void addIgnoreTicketCreationCspTicketTitle(String ticketTitle) {
        if (ticketTitle != null) {
            List<String> ignoreList = commonConfig.getIgnoreTicketCreationCspTitles();
            if (!ignoreList.contains(ticketTitle)) {
                ignoreList.add(ticketTitle);
            }
        }
    }

    @JsonIgnore
    public void removeIgnoreTicketCreationCspTicketTitle(String ticketTitle) {
        if (ticketTitle != null) {
            commonConfig.getIgnoreTicketCreationCspTitles().remove(ticketTitle);
        }
    }

    @JsonIgnore
    public void clearIgnoreTicketCreationCspTicketTitles() {
        commonConfig.getIgnoreTicketCreationCspTitles().clear();
    }

    @JsonIgnore
    public void addIgnoreTicketSyncFreshdeskTicketId(String ticketId) {
        if (ticketId != null) {
            List<String> ignoreList = commonConfig.getIgnoreTicketSyncFreshdeskIds();
            if (!ignoreList.contains(ticketId)) {
                ignoreList.add(ticketId);
            }
        }
    }

    @JsonIgnore
    public void removeIgnoreTicketSyncFreshdeskTicketId(String ticketId) {
        if (ticketId != null) {
            commonConfig.getIgnoreTicketSyncFreshdeskIds().remove(ticketId);
        }
    }

    @JsonIgnore
    public void clearIgnoreTicketSyncFreshdeskTicketIds() {
        commonConfig.getIgnoreTicketSyncFreshdeskIds().clear();
    }

    @JsonIgnore
    public void addIgnoreTicketSyncCspTicketId(String ticketId) {
        if (ticketId != null) {
            List<String> ignoreList = commonConfig.getIgnoreTicketSyncCspIds();
            if (!ignoreList.contains(ticketId)) {
                ignoreList.add(ticketId);
            }
        }
    }

    @JsonIgnore
    public void removeIgnoreTicketSyncCspTicketId(String ticketId) {
        if (ticketId != null) {
            commonConfig.getIgnoreTicketSyncCspIds().remove(ticketId);
        }
    }

    @JsonIgnore
    public void clearIgnoreTicketSyncCspTicketIds() {
        commonConfig.getIgnoreTicketSyncCspIds().clear();
    }

    @JsonIgnore
    public synchronized List<Agent> getL1Agents() {
        return commonConfig.getL1Agents();
    }

    @JsonIgnore
    public synchronized List<Agent> getL2Agents() {
        return commonConfig.getL2Agents();
    }

    @JsonIgnore
    public synchronized boolean isL1AgentId(long userId) {
        for (Agent agent : getL1Agents()) {
            if (agent.equalsId(userId)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public synchronized boolean isL2AgentId(long userId) {
        for (Agent agent : getL2Agents()) {
            if (agent.equalsId(userId)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public synchronized boolean isL1AgentEmail(String email) {
        for (Agent agent : getL1Agents()) {
            if (agent.equalsEmail(email)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public synchronized boolean isL2AgentEmail(String email) {
        for (Agent agent : getL2Agents()) {
            if (agent.equalsEmail(email)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public synchronized Agent getL1AgentById(long userId) {
        for (Agent agent : getL1Agents()) {
            if (agent.equalsId(userId)) {
                return agent;
            }
        }
        return null;
    }

    @JsonIgnore
    public synchronized Agent getL2AgentById(long userId) {
        for (Agent agent : getL2Agents()) {
            if (agent.equalsId(userId)) {
                return agent;
            }
        }
        return null;
    }

    @JsonIgnore
    public synchronized Agent getL1AgentByEmail(String email) {
        for (Agent agent : getL1Agents()) {
            if (agent.equalsEmail(email)) {
                return agent;
            }
        }
        return null;
    }

    @JsonIgnore
    public synchronized Agent getL2AgentByEmail(String email) {
        for (Agent agent : getL2Agents()) {
            if (agent.equalsEmail(email)) {
                return agent;
            }
        }
        return null;
    }

    @JsonIgnore
    public synchronized boolean isAgentId(long userId) {
        for (Agent agent : getL1Agents()) {
            if (agent.equalsId(userId)) {
                return true;
            }
        }
        for (Agent agent : getL2Agents()) {
            if (agent.equalsId(userId)) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public synchronized boolean addL1Agents(List<Agent> agents) {
        boolean changed = false;
        if (agents != null && agents.size() > 0) {
            List<Agent> l1Agents = commonConfig.getL1Agents();
            if (l1Agents == null) {
                l1Agents = new ArrayList<>();
                commonConfig.setL1Agents(l1Agents);
            }
            for (Agent agent : agents) {
                if (!containsAgent(l1Agents, agent)) {
                    l1Agents.add(agent);
                    changed = true;
                }
            }
        }
        log.debug("changed:{}", changed);
        return changed;
    }

    @JsonIgnore
    public synchronized boolean addL2Agents(List<Agent> agents) {
        boolean changed = false;
        if (agents != null && agents.size() > 0) {
            List<Agent> l2Agents = commonConfig.getL2Agents();
            if (l2Agents == null) {
                l2Agents = new ArrayList<>();
                commonConfig.setL2Agents(l2Agents);
            }
            for (Agent agent : agents) {
                if (!containsAgent(l2Agents, agent)) {
                    l2Agents.add(agent);
                    changed = true;
                }
            }
        }
        log.debug("changed:{}", changed);
        return changed;
    }

    @JsonIgnore
    public synchronized boolean removeL1Agents(List<Agent> agents) {
        boolean changed = false;
        if (agents != null && agents.size() > 0) {
            List<Agent> l1Agents = commonConfig.getL1Agents();
            if (l1Agents != null) {
                for (Agent agent : agents) {
                    if (removeAgentByEmail(l1Agents, agent.getEmail())) {
                        changed = true;
                    }
                }
            }
        }
        log.debug("changed:{}", changed);
        return changed;
    }

    @JsonIgnore
    public synchronized boolean removeL2Agents(List<Agent> agents) {
        boolean changed = false;
        if (agents != null && agents.size() > 0) {
            List<Agent> l2Agents = commonConfig.getL2Agents();
            if (l2Agents != null) {
                for (Agent agent : agents) {
                    if (removeAgentByEmail(l2Agents, agent.getEmail())) {
                        changed = true;
                    }
                }
            }
        }
        log.debug("changed:{}", changed);
        return changed;
    }

    @JsonIgnore
    private boolean removeAgentByEmail(List<Agent> agents, String email) {
        if (agents != null && email != null) {
            for (Agent a : agents) {
                if (a.equalsEmail(email)) {
                    agents.remove(a);
                    return true;
                }
            }
        }
        return false;
    }

    @JsonIgnore
    private boolean containsAgent(List<Agent> agents, Agent agent) {
        if (agents != null && agent != null) {
            for (Agent a : agents) {
                if (agent.equalsEmail(a.getEmail())) {
                    return true;
                }
            }
        }
        return false;
    }

    @JsonIgnore
    private boolean updateAgentId(List<Agent> agents, String email, long userId) {
        if (agents != null && email != null) {
            for (Agent agent : agents) {
                if (agent.equalsEmail(email)) {
                    agent.setId(userId);
                    return true;
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public synchronized boolean updateAgentId(String email, long userId) {
        boolean changed = false;
        if (email != null) {
            if (updateAgentId(commonConfig.getL1Agents(), email, userId)) {
                changed = true;
            }
            if (updateAgentId(commonConfig.getL2Agents(), email, userId)) {
                changed = true;
            }
        }
        log.debug("changed:{}", changed);
        return changed;
    }

    @JsonIgnore
    public synchronized boolean updateAgentIds(List<Agent> updatedAgentIds) {
        boolean changed = false;
        if (updatedAgentIds != null && updatedAgentIds.size() > 0) {
            for (Agent updated : updatedAgentIds) {
                if (updateAgentId(commonConfig.getL1Agents(), updated.getEmail(), updated.getId())) {
                    changed = true;
                }
                if (updateAgentId(commonConfig.getL2Agents(), updated.getEmail(), updated.getId())) {
                    changed = true;
                }
            }
        }
        log.debug("changed:{}", changed);
        return changed;
    }

    @JsonIgnore
    public boolean isBetaTestEnabled() {
        return commonConfig.isBetaTestEnabled();
    }

    @JsonIgnore
    public List<String> getBetaTesters() {
        return commonConfig.getBetaTesters();
    }

    @JsonIgnore
    public boolean isBetaTester(String email) {
        if (email != null && commonConfig.getBetaTesters() != null) {
            for (String tester : commonConfig.getBetaTesters()) {
                if (tester.equals(email)) {
                    return true;
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isSlaReportEnabled() {
        return commonConfig.isSlaReportEnabled();
    }

    @JsonIgnore
    public String getServiceApiAccessKey() {
        return commonConfig.getServiceApiAccessKey();
    }

    @JsonIgnore
    public String getServiceDeployInfoFile() {
        return getAppDeployInfoFilePath();
    }

    @JsonIgnore
    public String getTicketSyncRecordFile() {
        return getTicketSyncRecordFilePath();
    }

    @JsonIgnore
    public String getTicketReverseSyncRecordFile() {
        return getTicketReverseSyncRecordFilePath();
    }

    @JsonIgnore
    public boolean isHttpRequestTimeoutEnabled() {
        return commonConfig.isHttpRequestTimeoutEnabled();
    }

    @JsonIgnore
    public int getHttpRequestTimeout() {
        return commonConfig.getHttpRequestTimeout();
    }

    ////////// Cloud Z
    @JsonIgnore
    public String getCloudzApiEndpoint() {
        return cloudzConfig.getApiEndpoint();
    }

    @JsonIgnore
    public String getCloudzClientId() {
        return cloudzConfig.getClientId();
    }

    @JsonIgnore
    public String getCloudzClientSecret() {
        return cloudzConfig.getClientSecret();
    }

    @JsonIgnore
    public boolean isCloudzUserUseMasterAccount() {
        return cloudzConfig.isUserUseMasterAccount();
    }

    /////////// Opsgenie
    @JsonIgnore
    public String getOpsgenieApiEndpoint() {
        return opsgenieConfig.getApiEndpoint();
    }

    @JsonIgnore
    public String getOpsgenieApiKey() {
        return opsgenieConfig.getApiKey();
    }

    @JsonIgnore
    public String getOpsgenieAlertTeam() {
        return opsgenieConfig.getAlertTeam();
    }

    /////////// Freshdesk
    @JsonIgnore
    public String getServicePortalEndpoint() {
        return freshdeskConfig.getServicePortalEndpoint();
    }

    @JsonIgnore
    public String getFreshdeskApiEndpoint() {
        return freshdeskConfig.getApiEndpoint();
    }

    @JsonIgnore
    public String getFreshdeskApiKey() {
        return freshdeskConfig.getApiKey();
    }

    @JsonIgnore
    public long getFreshdeskAttachmentMaxSize() {
        return freshdeskConfig.getAttachmentMaxSize();
    }

    @JsonIgnore
    public boolean isFreshdeskOpenTicketStatusIncludesPending() {
        return freshdeskConfig.isOpenTicketStatusIncludesPending();
    }

    @JsonIgnore
    public boolean isFreshdeskClosedTicketStatusIncludesResolved() {
        return freshdeskConfig.isClosedTicketStatusIncludesResolved();
    }

    /////////// Slack
    @JsonIgnore
    public String getSlackWorkSpace() {
        return slackConfig.getWorkSpace();
    }

    @JsonIgnore
    public String getSlackApiToken() {
        return slackConfig.getApiToken();
    }

    /////////// IBM
    @JsonIgnore
    public String getIbmConsoleEndpoint() {
        return ibmConfig.getConsoleEndpoint();
    }

    @JsonIgnore
    public String getSoftlayerApiEndpoint() {
        return ibmConfig.getSoftlayerApiEndPoint();
    }

    @JsonIgnore
    public String getIbmAccountId() {
        return ibmConfig.getAccountId();
    }

    @JsonIgnore
    public String getIbmApiKey() {
        return ibmConfig.getApiKey();
    }

    @JsonIgnore
    public long getIbmSoftLayerApiDelayTime() {
        return ibmConfig.getSoftLayerApiDelayTime();
    }

    @JsonIgnore
    public int getIbmUpdateBodyMaxLength() {
        return ibmConfig.getUpdateBodyMaxLength();
    }

    @JsonIgnore
    public int getIbmAttachmentTotalSizeMax() {
        return ibmConfig.getAttachmentTotalSizeMax();
    }

    @JsonIgnore
    public int getIbmAttachmentCountMax() {
        return ibmConfig.getAttachmentCountMax();
    }

    @JsonIgnore
    public int getIbmAttachmentContentSizeMax() {
        return ibmConfig.getAttachmentContentSizeMax();
    }

    @JsonIgnore
    public List<IbmBrandAccount> getReverseSyncAccounts() {
        return ibmConfig.getReverseSyncAccounts();
    }

    @JsonIgnore
    public IbmBrandAccount getReverseSyncAccount(String brandId) {
        if (brandId != null && ibmConfig.getReverseSyncAccounts() != null) {
            for (IbmBrandAccount account : ibmConfig.getReverseSyncAccounts()) {
                if (brandId.equals(account.getBrandId())) {
                    return account;
                }
            }
        }
        return null;
    }

    @JsonIgnore
    public List<String> getIbmUnplannedEvents() {
        return ibmConfig.getUnplannedEvents();
    }

    @JsonIgnore
    public String getIbmAgentL1Email() {
        return ibmConfig.getAgentL1Email();
    }

    @JsonIgnore
    public String getSkSupportPortalUsingRequestMessage() {
        return messageTemplate.getSkSupportPortalUsingRequest();
    }

    @JsonIgnore
    public String getAgentL1TicketTemplateMessage() {
        return messageTemplate.getAgentL1TicketTemplate();
    }
}
