package com.sk.bds.ticket.api.data.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

public class AppConstants {
    public static final String LOG_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:dd";
    public static final String LOG_DELIMITER = "\t";

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAIL = "FAIL";

    public static final String ATTR_LOG_ITEM = "logItem";
    public static final String ATTR_USER_ID = "ssoId";


    public static final String TICKET_APP_VER = "TICKET_APP_VER";
    public static final String TICKET_APP_PATH = "TICKET_APP_PATH";
    public static final String TICKET_APP_DATA_PATH = "TICKET_APP_DATA_PATH";
    public static final String TICKET_APP_CONFIG_PATH = "TICKET_APP_CONFIG_PATH";
    public static final String TICKET_APP_REPORT_PATH = "TICKET_APP_REPORT_PATH";
    public static final String TICKET_APP_LOG_PATH = "TICKET_APP_LOG_PATH";

    public static final Charset CharsetUTF8 = StandardCharsets.UTF_8;//Charset.forName(UTF8);
    public static final String UTF8 = "UTF-8"; //CharsetUTF8.name();
    public static final String CSP_NAME = "IBM";
    public static final String CREATED_FROM_TICKET_MONITORING = "CREATED_FROM_TICKET_MONITORING";
    public static final String CREATED_FROM_FRESHDESK = "CREATED_FROM_FRESHDESK";
    public static final String CREATED_FROM_CSP = "CREATED_FROM_IBM";
    public static final String CONVERSATION_PREFIX_FOR_SK_CUSTOMER = "[For SK customer]<br>";

    public static final String MAX_THREAD_LIMIT = "MAX_THREAD_LIMIT";
    public static final String FRESHDESK_EMPTY_LINE = "<br>\n"; //freshdesk empty line ==> <div dir=\"ltr\"><br></div>\n
    public static final String FRESHDESK_LINEFEED = "<br>";
    public static final String FRESHDESK_LINEFEED_TWO_LINE = "<br><br>";
    public static final String FRESHDESK_CSP_DEVICE_DELIMITER = "\n";
    public static final String CSP_LINEFEED = "\n";
    public static final String CSP_LINEFEED_TWO_LINE = "\n\n";
    public static final String FRESHDESK_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String FRESHDESK_SEARCH_QUERY_TIME_FORMAT = "yyyy-MM-dd"; //"message": "It should be a valid date in the 'yyyy-mm-dd' format"
    public static final String CSP_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss XXX "; //"yyyy-MM-dd'T'HH:mm:ss+09:00"
    public static final String LOCAL_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss XXX"; //"yyyy-MM-dd'T'HH:mm:ss 'UTC+09:00'"; //"yyyy-MM-dd'T'HH:mm:ss XXX";
    public static final String UTC_TIME_ZONE_ID = "UTC"; //For ZoneId "UTC". for TimeZone "GMT";
    public static final String LOCAL_TIME_ZONE_ID = "Asia/Seoul"; //For ZoneId "Asia/Seoul". for TimeZone "KST";
    public static final String PUBLIC_URL_HEADER = "[티켓 공개 URL] ";
    public static final int MAX_RESULTS = 100;
    public static final int SLA_TARGET_DAYS = 14;
    public static final int SLA_TIME_L1 = 300;
    public static final int SLA_TIME_L2 = 300;
    public static final long TICKET_SYNC_INTERVAL_TIME_MILLIS = (360 * 1000);
    public static final long TICKET_REVERSE_SYNC_CHECKING_SLEEP_TIME_MILLIS = (120 * 1000);

    public static final String ERROR_HEADER_INTERNAL = "[Internal Error] ";
    public static final String ERROR_HEADER_CSP = "[IBM Error] ";
    public static final String ERROR_HEADER_FRESHDESK = "[Freshdesk Error] ";
    public static final String ERROR_HEADER_CLOUDZ = "[CloudZ Error] ";
    public static final String ERROR_HEADER_OPSGENIE = "[Opsgenie Error] ";
    public static final String ERROR_HEADER_SLACK = "[Slack Error] ";

    private static final TimeZone UTCTimeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID);
    //private static final TimeZone CDTTimeZone = TimeZone.getTimeZone("CDT"); //CDT timezone is not exists. //-05:00 //Central Daylight Time, //NACDT – North American Central Daylight Time
    private static final TimeZone CSTTimeZone = TimeZone.getTimeZone("CST"); //-06:00 //Central Standard Time, //NACST – North American Central Standard Time
    private static final TimeZone LocalTimeZone = TimeZone.getTimeZone(LOCAL_TIME_ZONE_ID); //+09:00

    public static TimeZone getUTCTimeZone() {
        return UTCTimeZone;
    }

    public static TimeZone getLocalTimeZone() {
        return LocalTimeZone;
    }

    public static TimeZone getCSTTimeZone() {
        return CSTTimeZone;
    }

    ///////////// IBM
    public static final String CLOSE_NOTE_MESSAGE = "Please close the case.";
    public static final String CLOSE_NOTE_RESOLUTION_PREFIX = "Close notes: ";
    public static final String FRESHDESK_FILE_SUFFIX = "__from_freshdesk";
    public static final String IBM_FILE_CONTENT_BODY_HEADER = "File attached from IBM<br>-------------------------<br>";
    public static final String IBM_ACCOUNT_PREFIX = "IBM";

    //UTC 기준으로 5시간 편차 발생함. IBM Brand Account timezone 설정 문제임.
    //3월~11월 첫 주 까지 써머타임 적용되는 기간에는 6시간 편차 발생.
    //The following SL document covers date handling for the API: https://sldn.softlayer.com/article/date-handling-softlayer-api/
    //Data in the API is centric to the Central time zone (CST/CDT). Which means that unless you specify a timezone in your request, CST/CDT will be assumed.
    //public static final long IBM_TICKET_MISSED_TIME_MILLIS = (6 * 3600 * 1000);
    //IbmTicketLoader에서 getCSTTimeZone()으로 타임존 설정하여 문제 해결.
    //https://sldn.softlayer.com/reference/services/SoftLayer_Ticket/addUpdate/
    //Add an update to a ticket. A ticket update’s entry has a maximum length of 4000 characters,
    //so ‘‘addUpdate()’’ splits the ‘‘entry’’ property in the ‘‘templateObject’’ parameter into 3900 character blocks and creates one entry per 3900 character block.
    //public static final int IBM_TICKET_BODY_TEXT_LENGTH_MAX = 3800; //Margins are required to prevent to the length problems caused by character encoding. //IBM can maximum 4000 characters.

}
