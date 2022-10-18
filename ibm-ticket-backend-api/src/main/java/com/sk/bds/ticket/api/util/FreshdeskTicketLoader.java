package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.TicketStatus;
import com.sk.bds.ticket.api.service.FreshdeskService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Slf4j
public class FreshdeskTicketLoader {
    private boolean hasMore;
    private int targetPage;
    private String csp;
    private Date startDate;
    private Date endDate;
    private boolean afterTime;
    private long targetTime;
    private TicketStatus ticketStatus;
    private int totalTicketCount;

    private SimpleDateFormat queryTimeFormat;
    private Calendar calendar;

    //https://support.freshdesk.com/support/solutions/articles/225152-on-what-time-zone-are-the-ticket-counting-timers-based-
    //On what time zone are the ticket counting timers based?
    //The timers are based on the Helpdesk time zone that can be configured according to your location.
    //Please navigate to Admin -> General settings -> click on Helpdesk to change the timezone.
    //현재 Freshdesk > 관리 > 일반설정 > 헬프데스크 확인 결과, (GMT+09:00) Seoul 로 설정되어 있음.
    //queryTimeFormat의 날짜 포맷시 Seoul로 설정.
    //참고: Dockerfile의 시스템 타임존 설정 ==> ENV TZ=Asia/Seoul
    //시스템 타임존 설정에 따라 TimeZone.getDefault()와 SimpleDateFormat의 기본 Timezone은 Seoul로 적용됨.

    private FreshdeskTicketLoader(String csp, Date targetDate, boolean afterTime, TicketStatus ticketStatus) {
        queryTimeFormat = new SimpleDateFormat(AppConstants.FRESHDESK_SEARCH_QUERY_TIME_FORMAT);
        queryTimeFormat.setTimeZone(AppConstants.getLocalTimeZone());
        calendar = Calendar.getInstance();
        targetTime = 0;
        targetPage = 0;
        hasMore = true;
        totalTicketCount = 0;
        this.csp = csp;
        this.startDate = targetDate;
        this.endDate = null;
        this.afterTime = afterTime;
        this.ticketStatus = ticketStatus;
        calendar.setTimeInMillis(targetDate.getTime());
    }

    private FreshdeskTicketLoader(String csp, Date start, Date end, TicketStatus ticketStatus) {
        queryTimeFormat = new SimpleDateFormat(AppConstants.FRESHDESK_SEARCH_QUERY_TIME_FORMAT);
        queryTimeFormat.setTimeZone(AppConstants.getLocalTimeZone());
        calendar = Calendar.getInstance();
        targetPage = 0;
        hasMore = true;
        this.csp = csp;
        this.startDate = start;
        this.endDate = end;
        this.ticketStatus = ticketStatus;
        calendar.setTimeInMillis(startDate.getTime());
    }

    public long getCurrentLoadingTime() {
        return targetTime;
    }

    public int getCurrentLoadingPage() {
        return targetPage;
    }

    public int getTotalTicketCount() {
        return totalTicketCount;
    }

    public boolean hasNext() {
        return hasMore;
    }

    public JSONArray next() {
        try {
            targetPage++;
            targetTime = calendar.getTimeInMillis();
            String filterDate = queryTimeFormat.format(targetTime);
            JSONArray ticketArray;
            if ((targetPage >= FreshdeskService.PAGE_START_NUMBER) && (targetPage <= FreshdeskService.TICKET_SEARCH_PAGE_MAX)) {
                JSONObject result;
                log.info("reading freshdesk tickets. filterDate: {}, targetPage:{}", filterDate, targetPage);
                if (ticketStatus == TicketStatus.opened) {
                    result = FreshdeskService.getOpenedTicketsOfDay(csp, filterDate, targetPage);
                } else if (ticketStatus == TicketStatus.closed) {
                    result = FreshdeskService.getClosedTicketsOfDay(csp, filterDate, targetPage);
                } else {
                    result = FreshdeskService.getTicketsOfDay(csp, filterDate, targetPage);
                }
                if (result != null) {
                    ticketArray = result.optJSONArray(FreshdeskService.KeyResults);
                    //페이이별로 읽는 동안 totalCount가 변경될 수 있으므로. count 비교 안함.
                    //int totalCount = result.optInt(FreshdeskService.KeyTotalCount);
                    //int readCount = ((currentPage-1)*ITEMS_MAX) + ticketArray.length();
                    hasMore = (targetPage < FreshdeskService.TICKET_SEARCH_PAGE_MAX) && (ticketArray.length() >= FreshdeskService.TICKET_SEARCH_ITEMS_PER_PAGE);
                    log.info("filterDate: {}, targetPage:{}, ticket count:{}, hasMore:{}", filterDate, targetPage, ticketArray.length(), hasMore);
                    if (targetPage == FreshdeskService.PAGE_START_NUMBER) { //first page.
                        totalTicketCount = result.optInt(FreshdeskService.KeyTotalCount);
                    }
                } else {
                    log.error("{} Cannot read freshdesk ticket filterDate: {} - targetPage: {}.", filterDate, targetPage);
                    hasMore = false;
                    totalTicketCount = 0;
                    ticketArray = new JSONArray();
                }

            } else {
                log.info("The maximum page has been reached. no more ticket at this date. {}", filterDate);
                hasMore = false;
                ticketArray = new JSONArray();
            }

            if (!hasMore && (afterTime || endDate != null)) { //지정된 날짜의 티켓을 모두 읽은 경우, 다음 날짜의 티켓 조회가 가능하면 다음 날짜의 티켓을 조회하도록 변경.
                //AfterTime으로 티켓 조회를 하는 경우. 현재 시간까지 확인해야함. 각 페이지별로 읽는 동안 날짜가 변경될 수 있으므로.
                String filterDateMax;
                if (afterTime) {
                    filterDateMax = queryTimeFormat.format(System.currentTimeMillis());
                } else { //byPeriod
                    filterDateMax = queryTimeFormat.format(endDate.getTime());
                }

                log.info("Checking the next target date. filterDate: {}, filterDateMax:{}", filterDate, filterDateMax);
                if (filterDate.compareTo(filterDateMax) >= 0) {
                    hasMore = false;
                    log.info("no more tickets. This date is the last.");
                } else {
                    hasMore = true;
                    targetPage = 0;
                    calendar.add(Calendar.DATE, 1);
                    log.info("more date are remains.");
                }
            }
            return ticketArray;
        } catch (Exception e) {
            hasMore = false;
            log.error("tickets getting failed. {}", e);
        }
        return new JSONArray();
    }

    public static FreshdeskTicketLoader byDay(String csp, Date date, TicketStatus ticketStatus) {
        return new FreshdeskTicketLoader(csp, date, false, ticketStatus);
    }

    public static FreshdeskTicketLoader afterTime(String csp, Date date, TicketStatus ticketStatus) {
        return new FreshdeskTicketLoader(csp, date, true, ticketStatus);
    }

    public static FreshdeskTicketLoader byPeriod(String csp, Date startDate, Date endDate, TicketStatus ticketStatus) {
        return new FreshdeskTicketLoader(csp, startDate, endDate, ticketStatus);
    }
}
