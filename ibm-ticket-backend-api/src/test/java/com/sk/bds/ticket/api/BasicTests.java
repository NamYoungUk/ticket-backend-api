package com.sk.bds.ticket.api;

import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.TimeSection;
import com.sk.bds.ticket.api.data.model.TimeSectionGroup;
import com.sk.bds.ticket.api.data.model.UnplannedEvents;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.ibm.IbmAttachedFileMetadata;
import com.sk.bds.ticket.api.exception.AppInternalErrorReason;
import com.sk.bds.ticket.api.service.CloudZService;
import com.sk.bds.ticket.api.service.IbmService;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.ticket.Update;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//@RunWith(SpringRunner.class)
//@PropertySource(value = "application-test.properties", encoding = "UTF-8")
//@PropertySource("classpath:sk-stg/application.properties")
//@ActiveProfiles("local")
@SpringBootTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Slf4j
public class BasicTests {
    private static String apiId() {
        return "IBM838391";
    }

    private static String apiKey() {
        return "960a59134dba43b3afa801e9ab7cc587a4f35ad2bcb005257874a8c4a6134443";
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
    public void aaeaa() {
        AppInternalErrorReason aaa = AppInternalErrorReason.ExceedConversationLimit.withDetails("aaaaaaaaaa");
        AppInternalErrorReason bbb = AppInternalErrorReason.ExceedConversationLimit.withDetails("bbbbbbb");
        log.debug("==: {}", aaa == bbb);
        log.debug("equals: {}", aaa.equals(bbb));
    }

    @Test
    public void aeafad() {
        String freshdeskBodyHtml1 = "<div>IBM ticket has been linked.<br>Case Id: 138121940<br>Case Display Id: CS2402699<br><br>[MAX_THREAD_LIMIT:2021-07-20T10:02:53 +09:00]</div>";
        boolean found1 = TicketUtil.isTaggedBody(freshdeskBodyHtml1, AppConstants.MAX_THREAD_LIMIT, AppConstants.FRESHDESK_LINEFEED);
        log.debug("found1: {}", found1);
        String freshdeskBodyHtml2 = "<div>IBM ticket has been linked.<br>Case Id: 138121940<br>Case Display Id: CS2402699<br><br>[CREATED_FROM_TICKET_MONITORING:2021-07-20T10:02:53 +09:00]</div>";
        boolean found2 = TicketUtil.isTaggedBody(freshdeskBodyHtml2, AppConstants.MAX_THREAD_LIMIT, AppConstants.FRESHDESK_LINEFEED);
        log.debug("found2: {}", found2);
    }

    @Test
    public void aa() {
        String accountId = "IBM123456789IBM123123IBM123";
        String abc = TicketUtil.detachIbmAccountPrefix(accountId);
        log.info("detached account id: {}", abc);
    }

    @Test
    public void bb() {
        String text = "1234567890,1234567890,1234567890,1234567890,1234567890,1234567890";
        String pool = text;
        for (int i = 0; i < 5; i++) {
            log.debug("pool:{}", pool);
            int pos = pool.indexOf(",");
            String contentPart = pool.substring(0, pos + 1);
            log.debug("contentPart:{}", contentPart);
            pool = pool.substring(pos + 1);
        }
    }

    @Test
    public void cc() {
        //String aa = "opdown_blank dynamic_sections\" id=\"helpdesk_ticket_ticket_type\" name=\"helpdesk_ticket[ticket_type]\"><option value=\"\">...</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"사업문의\" data-id=\"2043001582603\">사업문의</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"기술문의\" data-id=\"2043001582604\">기술문의</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"장애\" data-id=\"2043001582605\">장애</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"Planned Maintenance\" data-id=\"2043001582606\">Planned Maintenance</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"기타\" data-id=\"2043002294287\">기타</option></select></td>\n</tr>\n<tr>\n<td></td>\n<td>            </div> </td>\n</tr>\n<tr>\n<td></td>\n<td>      </div></td>\n</tr>\n<tr>\n<td></td>\n<td>      <div class=\"control-group \" ></td>\n</tr>\n<tr>\n<td></td>\n<td>         <label class=\" required control-label cf_csp_1117635-label \" for=\"helpdesk_ticket_cf_csp_1117635\">서비스 유형</label></td>\n</tr>\n<tr>\n<td></td>\n<td>            <div class=\"controls   \"></td>\n</tr>\n<tr>\n<td></td>\n<td>              <select class=\" required dropdown_blank dynamic_sections\" id=\"helpdesk_ticket_custom_field_cf_csp_1117635\" name=\"helpdesk_ticket[custom_field][cf_csp_1117635]\"><option value=\"\">...</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"Cloud Z\" data-id=\"2043001928340\">Cloud Z</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"IBM\" data-id=\"2043001928341\">IBM</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"AWS\" data-id=\"2043001928342\">AWS</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"Azure\" data-id=\"2043002110771\">Azure</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"GCP\" data-id=\"2043001928343\">GCP</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"MSP\" data-id=\"2043002239443\">MSP</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"SK DT Platform\" data-id=\"2043002294289\">SK DT Platform</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"기타\" data-id=\"2043002294290\">기타</option></select></td>\n</tr>\n<tr>\n<td></td>\n<td>            </div> </td>\n</tr>\n<tr>\n<td></td>\n<td>      </div></td>\n</tr>\n<tr>\n<td></td>\n<td>          <textarea class=\"picklist_section_2043001928340 hide\"></td>\n</tr>\n<tr>\n<td></td>\n<td>                <div class=\"control-group ticket_section\"></td>\n</tr>\n<tr>\n<td></td>\n<td>                   <label class=\" required control-label cf_l1_1117635-label \" for=\"helpdesk_ticket_cf_l1_1117635\">대상 서비스 L1</label></td>\n</tr>\n<tr>\n<td></td>\n<td>            <div class=\"controls nested_field  \"></td>\n</tr>\n<tr>\n<td></td>\n<td>              <select class=\" required nested_field section_field\" id=\"helpdesk_ticket_custom_field_cf_l1_1117635_2043001928340\" name=\"helpdesk_ticket[custom_field][cf_l1_1117635]\"><option value=\"\">...</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"경기도 클라우드 지원사업\">경기도 클라우드 지원사업</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"Cloud Z Care\">Cloud Z Care</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"플랫폼(Platform)\">플랫폼(Platform)</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"개별 시스템\">개별 시스템</option></td>\n</tr>\n<tr>\n<td></td>\n<td><option value=\"기타\">기타</option></select><div class=\"level_2\"><label class=\"required\">대상 서비스 L2</label><select class=\" required nested_field section_field\" id=\"helpdesk_ticket_custom_field_cf_l2_1117635_2043001928340\" name=\"helpdesk_ticket[custom_field][cf_l2_1117635]\"><option value=\"\">...</option></td>\n</tr>\n<tr>\n<td></td>\n<td></select></d";
        //log.debug("string length:{}, byes:{}", aa.length(), aa.getBytes().length);
        String aa = "가a나b다c라d마e바f사g아h자i차j카타파하";
        String cut1 = Util.substringByBytes(aa, 6);
        log.debug("--------------------------------\n");
        log.debug("cut1:{}, {} bytes length", cut1, cut1.getBytes().length);
        log.debug("--------------------------------\n");
        String cut2 = Util.substringByBytes(aa, 10);
        log.debug("--------------------------------\n");
        log.debug("cut2:{}, {} bytes length", cut2, cut2.getBytes().length);
        log.debug("--------------------------------\n");
        String cut3 = Util.substringByBytes(aa, 20);
        log.debug("--------------------------------\n");
        log.debug("cut3:{}, {} bytes length", cut3, cut3.getBytes().length);
        log.debug("--------------------------------\n");
        String cut4 = Util.substringByBytes(aa, 40);
        log.debug("--------------------------------\n");
        log.debug("cut4:{}, {} bytes length", cut4, cut4.getBytes().length);
        log.debug("--------------------------------\n");
        String cut5 = Util.substringByBytes(aa, 43);
        log.debug("--------------------------------\n");
        log.debug("cut5:{}, {} bytes length", cut5, cut5.getBytes().length);
        log.debug("--------------------------------\n");
        String cut6 = Util.substringByBytes(aa, 45);
        log.debug("--------------------------------\n");
        log.debug("cut6:{}, {} bytes length", cut6, cut6.getBytes().length);
        log.debug("--------------------------------\n");
    }

    @Test
    public void dd() {
        String jsonText = "{\n" +
                "    \"SynchronizationTargetTime\": \"2021-03-01T00:00:00 +09:00\",\n" +
                "    \"SynchronizationEnabled\": true,\n" +
                "    \"EscalationCheckEnabled\": false,\n" +
                "    \"ReverseSynchronizationEnabled\": true,\n" +
                "    \"CurrentSynchronizationTicketCount\": 0,\n" +
                "    \"Service\": {\n" +
                "        \"InitializedTime\": \"2021-03-18T11:14:30 +09:00\",\n" +
                "        \"ConfiguredTime\": \"2021-03-18T11:17:11 +09:00\",\n" +
                "        \"Version\": \"1.0\",\n" +
                "        \"DeployTime\": \"2021-03-18T10:55:43 +09:00\",\n" +
                "        \"StartTime\": \"2021-03-18T11:14:28 +09:00\",\n" +
                "        \"Stage\": \"local\",\n" +
                "        \"BuildTime\": \"2021-03-18T11:13:57 +09:00\",\n" +
                "        \"Name\": \"IBM-TICKET-MANAGEMENT-SERVICE\"\n" +
                "    },\n" +
                "    \"LastSynchronizedTime\": \"2021-03-01T00:00:00 +09:00\",\n" +
                "    \"BetaTestEnabled\": true,\n" +
                "    \"SynchronizationRunning\": true,\n" +
                "    \"SynchronizationInterval\": 300000,\n" +
                "    \"CurrentSynchronizationTickets\": [],\n" +
                "    \"BetaTester\": [\n" +
                "        \"seoingood@sk.com\",\n" +
                "        \"sunwoods@sk.com\",\n" +
                "        \"dt_admin@sk.com\"\n" +
                "    ]\n" +
                "}";
        JSONObject json = new JSONObject(jsonText);
        log.debug("json:{}", json);
        long start = System.currentTimeMillis();
        log.debug("prettyJsonPrint:{}", JsonUtil.prettyPrint(json));
        long end = System.currentTimeMillis();
        log.debug("elapsed time: {} ms.", (end - start));
    }

    @Test
    public void eee() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2021);
        cal.set(Calendar.MONTH, 2);
        cal.set(Calendar.DAY_OF_MONTH, 7);
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 33);
        cal.set(Calendar.SECOND, 00); //12
        cal.set(Calendar.MILLISECOND, 0); //12
        long startTime = cal.getTimeInMillis();
        log.debug("========================================================");
        TimeSectionGroup timeGroup = new TimeSectionGroup(startTime, AppConstants.getLocalTimeZone(), TimeSectionGroup.SectionInterval.date1);
        log.debug("RealTime Sections = 1 hour interval");
        while (timeGroup.hasNext()) {
            TimeSection section = timeGroup.next();
            log.debug("{}", section.print());
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }
    }


    @Test
    public void timezones() {
        final TimeZone CDTTimeZone = TimeZone.getTimeZone("CDT"); //-05:00 //Central Daylight Time, //NACDT – North American Central Daylight Time
        final TimeZone CSTTimeZone = TimeZone.getTimeZone("CST"); //-06:00 //Central Standard Time, //NACST – North American Central Standard Time
        final TimeZone CST6CDTTimeZone = TimeZone.getTimeZone("CST6CDT"); //-06:00 //Central Standard Time, //NACST – North American Central Standard Time
        log.debug("CDTTimeZone: {}", CDTTimeZone);
        log.debug("CSTTimeZone: {}", CSTTimeZone);
        log.debug("CST6CDTTimeZone: {}", CST6CDTTimeZone);
        for (String timezoneId : TimeZone.getAvailableIDs()) {
            log.debug("timezoneId: {}", timezoneId);
        }
    }

    @Test
    public void accountTest() {
        CloudZService service = new CloudZService();
        try {
            JSONArray result = service.getAcceptableUserApiInfoListByEmail("msp@uws.co.kr");
            log.debug("result:{}", result);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void aaaa() {
        boolean result = UnplannedEvents.isUnplannedEvent("필수 유지보수 - Raid 경보");
        log.debug("result:{}", result);
    }

    @Test
    public void bbbb() {
        List<String> aaa = Arrays.asList("aa", "bb", "cc", "a1", "z2");
        List<String> bbb = new ArrayList<>();
        aaa.forEach(item -> bbb.add("--" + item));
        log.info("bbb: {}", bbb);
    }

    @Test
    public void checkTicketUpdates() {
        long cspTicketId = 138095722;
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
        long cspTicketId = 138095722;
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
    public void adda() {
        String htmlBody = "<div>File attached from IBM<br>-------------------------<br>test1.jpg [12345678,2021-11-25T11:45:17 +09:00]<br><br>[CREATED_FROM_IBM:680012898,2021-11-25T11:45:17 +09:00]</div>";
        String ibmUpdateIdString = TicketUtil.getIdFromBodyTag(htmlBody, AppConstants.CREATED_FROM_CSP, AppConstants.FRESHDESK_LINEFEED);
        long updateId = Long.valueOf(ibmUpdateIdString);
        //Not allowed characters - "[:\\\\/%*?:|\"<>]"
        //<div>File attached from IBM<br>-------------------------<br>image.png [ibm-첨부파일-Id,2021-11-19T19:22:08 +09:00]<br><br>[CREATED_FROM_IBM:IBM대화Id,2021-11-19T19:24:08 +09:00]</div>
        log.debug("=========== pattern 1");
        final String searchPattern1 = "<br>([^\\\\/:*?\"<>|]+)[\\s]+[\\[]{1}([\\d]*),[\\s]?([\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2})[\\]]{1}";
        Pattern pattern = Pattern.compile(searchPattern1);
        Matcher matcher = pattern.matcher(htmlBody);
        while (matcher.find()) {
            //freshdesk tag encoding에 의해 파일 이름이 변경되는 문제 방지.
            String unEscapedFileName = StringEscapeUtils.unescapeHtml4(matcher.group(1));
            Date fileCreateTime = TicketUtil.parseLocalTime(matcher.group(3));
            long fileId = Long.valueOf(matcher.group(2));
            IbmAttachedFileMetadata meta = new IbmAttachedFileMetadata(unEscapedFileName, fileCreateTime, fileId, updateId);
            log.debug("meta: {}", meta);
        }

        log.debug("=========== pattern 2");
        //<div>File attached from IBM<br>-------------------------<br>image.png [2020-05-28T14:38:04 +09:00]<br><br>[CREATED_FROM_IBM:0,2020-05-28T14:38:04 +09:00]</div>
        final String searchPattern2 = "<br>([^\\\\/:*?\"<>|]+)[\\s]+[\\[]{1}([\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}\\s{1}\\+[\\d]{2}:[\\d]{2})[\\]]{1}";
        final long ibmFileId = 0;
        pattern = Pattern.compile(searchPattern2);
        matcher = pattern.matcher(htmlBody);
        while (matcher.find()) {
            //freshdesk tag encoding에 의해 파일 이름이 변경되는 문제 방지.
            String unEscapedFileName = StringEscapeUtils.unescapeHtml4(matcher.group(1));
            Date fileCreateTime = TicketUtil.parseLocalTime(matcher.group(2));
            IbmAttachedFileMetadata meta = new IbmAttachedFileMetadata(unEscapedFileName, fileCreateTime, ibmFileId, updateId);
            log.debug("meta: {}", meta);
        }
    }
}
