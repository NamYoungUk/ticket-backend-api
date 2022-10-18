package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.util.Util;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Getter
@Configuration
@PropertySource(value = "application.properties", encoding = "UTF-8")
public class PropertyReader {
    //Ticket Service properties
    @Value("${ticket.service.api.endpoint}")
    String serviceEndpoint;
    @Value("${ticket.service.stage}")
    String serviceStage;
    @Value("#{new Boolean('${ticket.service.escalation.check.enabled}')}")
    boolean escalationCheckEnabled;
    @Value("#{new Boolean('${ticket.service.sync.enabled}')}")
    boolean syncEnabled;
    @Value("#{new Long('${ticket.service.sync.interval}')}")
    long syncInterval;
    @Value("#{new Boolean('${ticket.service.sync.reverse.enabled}')}")
    boolean reverseSyncEnabled;
    @Value("#{new Long('${ticket.service.sync.reverse.checking.sleep.time}')}")
    long reverseSyncCheckingSleepTime;
    @Value("${ticket.service.sync.target.time}")
    String syncTargetTime;
    @Value("#{new Integer('${ticket.service.sync.conversation.max}')}")
    int syncConversationMax;
    @Value("#{new Integer('${ticket.service.sync.concurrent.max}')}")
    int syncConcurrentMax;
    @Value("#{new Boolean('${ticket.service.beta.enabled}')}")
    boolean betaTestEnabled;
    @Value("${ticket.service.beta.testers}")
    String betaTestersString;
    @Value("#{new Integer('${ticket.service.error.report.required.count}')}")
    int requiredErrorCountForReporting;
    @Value("${ticket.service.api.access.key}")
    String serviceApiAccessKey;

    @Value("${ticket.service.ignore.creation.ticket.freshdesk.ids}")
    String ignoreTicketCreationFreshdeskIds;
    @Value("${ticket.service.ignore.creation.ticket.csp.ids}")
    String ignoreTicketCreationCspIds;
    @Value("${ticket.service.ignore.creation.ticket.freshdesk.titles}")
    String ignoreTicketCreationFreshdeskTitles;
    @Value("${ticket.service.ignore.creation.ticket.csp.titles}")
    String ignoreTicketCreationCspTitles;
    @Value("${ticket.service.ignore.sync.ticket.freshdesk.ids}")
    String ignoreTicketSyncFreshdeskIds;
    @Value("${ticket.service.ignore.sync.ticket.csp.ids}")
    String ignoreTicketSyncCspIds;

    @Value("#{new Boolean('${ticket.service.ignore.creation.freshdesk.id.enabled}')}")
    boolean ignoreTicketCreationFreshdeskIdEnabled;
    @Value("#{new Boolean('${ticket.service.ignore.creation.csp.id.enabled}')}")
    boolean ignoreTicketCreationCspIdEnabled;
    @Value("#{new Boolean('${ticket.service.ignore.creation.freshdesk.title.enabled}')}")
    boolean ignoreTicketCreationFreshdeskTitleEnabled;
    @Value("#{new Boolean('${ticket.service.ignore.creation.csp.title.enabled}')}")
    boolean ignoreTicketCreationCspTitleEnabled;
    @Value("#{new Boolean('${ticket.service.ignore.sync.freshdesk.id.enabled}')}")
    boolean ignoreTicketSyncFreshdeskIdEnabled;
    @Value("#{new Boolean('${ticket.service.ignore.sync.csp.id.enabled}')}")
    boolean ignoreTicketSyncCspIdEnabled;

    //Agent
    @Value("${ticket.service.agent.l1}")
    String l1Agents;
    @Value("${ticket.service.agent.l2}")
    String l2Agents;

    //SLA Policy
    @Value("#{new Boolean('${ticket.service.sla.report.enabled}')}")
    boolean slaReportEnabled;
    @Value("#{new Integer('${ticket.service.sla.target.days}')}")
    int slaTargetDays;
    @Value("#{new Integer('${ticket.service.sla.time.l1}')}")
    int slaTimeL1;
    @Value("#{new Integer('${ticket.service.sla.time.l2}')}")
    int slaTimeL2;

    //Http settings
    @Value("#{new Boolean('${ticket.service.http.timeout.enabled}')}")
    boolean httpRequestTimeoutEnabled;
    @Value("#{new Integer('${ticket.service.http.timeout}')}")
    int httpRequestTimeout;

    //Cloud Z Portal
    @Value("${cloudz.api.endpoint}")
    String cloudzApiEndpoint;
    @Value("${cloudz.client.id}")
    String cloudzClientId;
    @Value("${cloudz.client.secret}")
    String cloudzClientSecret;
    @Value("#{new Boolean('${cloudz.user.use.master-account}')}")
    boolean cloudzUserUseMasterAccount;

    //Opsgenie
    @Value("${opsgenie.api.endpoint}")
    String opsgenieApiEndpoint;
    @Value("${opsgenie.api.key}")
    String opsgenieApiKey;
    @Value("${opsgenie.api.alert.team}")
    String opsgenieApiAlertTeam;

    //Freshdesk
    @Value("${freshdesk.support-portal.endpoint}")
    String freshdeskServicePortalEndpoint;
    @Value("${freshdesk.api.endpoint}")
    String freshdeskApiEndpoint;
    @Value("${freshdesk.api.key}")
    String freshdeskApiKey;
    @Value("#{new Integer('${freshdesk.attachment.limit.total.size}')}")
    int freshdeskAttachmentTotalSizeMax;
    @Value("#{new Boolean('${freshdesk.ticket.status.open.include-pending}')}")
    boolean freshdeskOpenTicketStatusIncludesPending;
    @Value("#{new Boolean('${freshdesk.ticket.status.closed.include-resolved}')}")
    boolean freshdeskClosedTicketStatusIncludesResolved;

    //Slack
    @Value("${slack.api.workspace}")
    String slackWorkSpace;
    @Value("${slack.api.token}")
    String slackApiToken;

    //IBM properties
    @Value("${ibm.console.endpoint}")
    String ibmConsoleEndpoint;
    @Value("${ibm.softlayer_api.endpoint}")
    String ibmSoftLayerApiEndpoint;
    @Value("${ibm.account}")
    String ibmAccountId;
    @Value("${ibm.account.key}")
    String ibmAccountKey;
    @Value("${ibm.reverse.sync.account1.brand}")
    String ibmReverseSyncBrandId1;
    @Value("${ibm.reverse.sync.account1.id}")
    String ibmReverseSyncAccountId1;
    @Value("${ibm.reverse.sync.account1.key}")
    String ibmReverseSyncAccountKey1;
    @Value("${ibm.reverse.sync.account1.email}")
    String ibmReverseSyncEmail1;
    @Value("${ibm.reverse.sync.account2.brand}")
    String ibmReverseSyncBrandId2;
    @Value("${ibm.reverse.sync.account2.id}")
    String ibmReverseSyncAccountId2;
    @Value("${ibm.reverse.sync.account2.key}")
    String ibmReverseSyncAccountKey2;
    @Value("${ibm.reverse.sync.account2.email}")
    String ibmReverseSyncEmail2;
    @Value("${ibm.agent.l1.email}")
    String ibmAgentL1Email;
    @Value("#{new Long('${ibm.api.softlayer.delay.time}')}")
    long ibmSoftLayerApiDelayTime;
    @Value("#{new Integer('${ibm.update.body.length.max}')}")
    int ibmUpdateBodyMaxLength;
    @Value("#{new Integer('${ibm.attachment.limit.total.size}')}")
    int ibmAttachmentTotalSizeMax;
    @Value("#{new Integer('${ibm.attachment.limit.content.size}')}")
    int ibmAttachmentContentSizeMax;
    @Value("#{new Integer('${ibm.attachment.limit.file.count}')}")
    int ibmAttachmentCountMax;
    @Value("${ibm.unplannedevent.keywords}")
    String ibmUnplannedEventKeywords;

    //Message Templates
    @Value("${message.sk.support.portal.using.request}")
    String skSupportPortalUsingRequestMessage;
    @Value("${message.l1.ticket.template}")
    String agentL1TicketTemplate;

    @PostConstruct
    private void initDefault() {
        //TODO. These operations are needed for broken text by encoding issue.
        byte[] stringBytes;
        if (ignoreTicketCreationFreshdeskTitles != null) {
            stringBytes = ignoreTicketCreationFreshdeskTitles.getBytes(StandardCharsets.ISO_8859_1);
            try {
                ignoreTicketCreationFreshdeskTitles = new String(stringBytes, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                log.error("error:{}", e);
            }
        }

        if (ignoreTicketCreationCspTitles != null) {
            stringBytes = ignoreTicketCreationCspTitles.getBytes(StandardCharsets.ISO_8859_1);
            try {
                ignoreTicketCreationCspTitles = new String(stringBytes, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                log.error("error:{}", e);
            }
        }

        if (l1Agents != null) {
            stringBytes = l1Agents.getBytes(StandardCharsets.ISO_8859_1);
            try {
                l1Agents = new String(stringBytes, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                log.error("error:{}", e);
            }
        }

        if (l2Agents != null) {
            stringBytes = l2Agents.getBytes(StandardCharsets.ISO_8859_1);
            try {
                l2Agents = new String(stringBytes, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                log.error("error:{}", e);
            }
        }

        if (ibmUnplannedEventKeywords != null) {
            stringBytes = ibmUnplannedEventKeywords.getBytes(StandardCharsets.ISO_8859_1);
            try {
                ibmUnplannedEventKeywords = new String(stringBytes, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                Util.ignoreException(e);
            }
        }

        if (skSupportPortalUsingRequestMessage != null) {
            stringBytes = skSupportPortalUsingRequestMessage.getBytes(StandardCharsets.ISO_8859_1);
            try {
                skSupportPortalUsingRequestMessage = new String(stringBytes, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                log.error("error:{}", e);
            }
        }

        if (agentL1TicketTemplate != null) {
            stringBytes = agentL1TicketTemplate.getBytes(StandardCharsets.ISO_8859_1);
            try {
                agentL1TicketTemplate = new String(stringBytes, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                log.error("error:{}", e);
            }
        }
    }
}
