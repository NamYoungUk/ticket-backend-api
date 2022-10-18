package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.softlayer.api.service.Ticket;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
public class IbmTicketLoader {
    private static final String SOFTLAYER_TIME_FORMAT = "MM/dd/yyyy HH:mm:ss";
    private static final int RESULT_MAX = 100;
    @Setter
    private int pageSize;
    private boolean hasMore;
    private String requestUrl;
    private UsernamePasswordCredentials credentials;
    private String queryParams;
    private int offset;

    private IbmTicketLoader(String requestUrl, UsernamePasswordCredentials credentials, String queryParams) {
        this.requestUrl = requestUrl;
        this.credentials = credentials;
        this.queryParams = queryParams;
        this.offset = 0;
        this.hasMore = true;
        this.pageSize = RESULT_MAX;
    }

    public boolean hasNext() {
        return hasMore;
    }

    public List<Ticket> next() {
        final String urlTemplate = requestUrl + "?resultLimit=%d,%d";
        if (queryParams == null) {
            queryParams = "";
        }
        List<Ticket> ticketList = new ArrayList<>();
        RestApiUtil.RestApiResult result = null;
        try {
            String targetUrl = String.format(urlTemplate, offset, pageSize) + queryParams;
            log.info("read ibm ticket offset:{}, url:{}", offset, targetUrl);
            result = RestApiUtil.get(targetUrl, credentials);
            if (result.isOK()) {
                JSONArray ticketArray = new JSONArray(result.getResponseBody());
                log.info("read ibm tickets. {}", ticketArray.length());
                hasMore = (ticketArray.length() == pageSize);
                offset += ticketArray.length();
                if (ticketArray.length() > 0) {
                    for (int i = 0; i < ticketArray.length(); i++) {
                        JSONObject json = ticketArray.getJSONObject(i);
                        try {
                            Ticket ticket = JsonUtil.unmarshal(json.toString(), Ticket.class);
                            ticketList.add(ticket);
                        } catch (IOException e) {
                            log.error("ibm ticket unmarshalling failed. {}", e);
                        }
                    }
                }
            } else {
                log.error("ibm tickets getting failed. {} {}", result.getStatus(), result.getResponseBody());
            }
        } catch (Exception e) {
            hasMore = false;
            log.info("result:{}", result);
            log.error("ibm tickets getting failed. {}", e);
        }
        log.info("ibm ticket sorting... sort by create-time ascending");
        Collections.sort(ticketList, new Comparator<Ticket>() {
            @Override
            public int compare(Ticket o1, Ticket o2) {
                //Ascending sort
                long time1 = o1.getCreateDate().getTimeInMillis();
                long time2 = o2.getCreateDate().getTimeInMillis();
                if (time1 > time2) {
                    return 1;
                } else if (time1 < time2) {
                    return -1;
                }
                return 0;
            }
        });
        return ticketList;
    }

    public static IbmTicketLoader afterTime(CloudZCspApiInfo slApiInfo, Date afterTime, boolean openTicketOnly) {
        //Account.Service accountService = Account.service(getIbmClient(account.getAccountId(), account.getApiKey()));
        final String requestUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Account/getTickets";
        return new IbmTicketLoader(requestUrl, slApiInfo.credentials(), afterTimeQueryParam(afterTime, openTicketOnly));
    }

    public static IbmTicketLoader betweenTime(CloudZCspApiInfo slApiInfo, Date startTime, Date endTime, boolean openTicketOnly) {
        final String requestUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Account/getTickets";
        return new IbmTicketLoader(requestUrl, slApiInfo.credentials(), betweenTimeQueryParam(startTime, endTime, openTicketOnly));
    }

    public static IbmTicketLoader forDay(CloudZCspApiInfo slApiInfo, Date targetDay, boolean openTicketOnly) {
        final String requestUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Account/getTickets";
        return new IbmTicketLoader(requestUrl, slApiInfo.credentials(), forDayQueryParam(targetDay, openTicketOnly));
    }

    public static IbmTicketLoader afterTime(IbmBrandAccount brandAccount, Date afterTime, boolean openTicketOnly) {
        //Brand.Service brandService = Brand.service(getIbmBrandClient(brandAccount.getAccountId(), brandAccount.getApiKey()));
        //final String requestUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Brand/" + brandAccount.getBrandId() + "/getOpenTickets";
        final String requestUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Brand/" + brandAccount.getBrandId() + "/getTickets";
        return new IbmTicketLoader(requestUrl, brandAccount.credentials(), afterTimeQueryParam(afterTime, openTicketOnly));
    }

    public static IbmTicketLoader betweenTime(IbmBrandAccount brandAccount, Date startTime, Date endTime, boolean openTicketOnly) {
        final String requestUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Brand/" + brandAccount.getBrandId() + "/getTickets";
        return new IbmTicketLoader(requestUrl, brandAccount.credentials(), betweenTimeQueryParam(startTime, endTime, openTicketOnly));
    }

    public static IbmTicketLoader forDay(IbmBrandAccount brandAccount, Date targetDay, boolean openTicketOnly) {
        final String requestUrl = "https://api.softlayer.com/rest/v3.1/SoftLayer_Brand/" + brandAccount.getBrandId() + "/getTickets";
        return new IbmTicketLoader(requestUrl, brandAccount.credentials(), forDayQueryParam(targetDay, openTicketOnly));
    }

    //참고: Dockerfile의 시스템 타임존 설정 ==> ENV TZ=Asia/Seoul
    //시스템 타임존 설정에 따라 TimeZone.getDefault()와 SimpleDateFormat의 기본 Timezone은 Seoul로 적용됨.
    //SoftLayer Ticket의 경우
    //The following SL document covers date handling for the API: https://sldn.softlayer.com/article/date-handling-softlayer-api/
    //Data in the API is centric to the Central time zone (CST/CDT). Which means that unless you specify a timezone in your request, CST/CDT will be assumed.
    private static SimpleDateFormat getDateFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(SOFTLAYER_TIME_FORMAT);
        dateFormat.setTimeZone(AppConstants.getCSTTimeZone());
        return dateFormat;
    }

    //https://sldn.softlayer.com/article/object-filters/
    //It is not possible to SORT and SEARCH on the same property.
    private static String betweenTimeQueryParam(Date startTime, Date endTime, boolean openTicketOnly) {
        String queryParams = "";
        try {
            String objectFilter = null;
            if (startTime != null && endTime != null) {
                final SimpleDateFormat dateFormat = getDateFormat();
                final String startTimeString = dateFormat.format(startTime);
                final String endTimeString = dateFormat.format(endTime);
                String objectFilterTemplate;
                if (openTicketOnly) {
                    //objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"betweenDate\",\"options\":[{\"name\":\"startDate\",\"value\":[\"%s\"]}, {\"name\":\"endDate\",\"value\":[\"%s\"]}]},\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}}}";
                    objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"betweenDate\",\"options\":[{\"name\":\"startDate\",\"value\":[\"%s\"]}, {\"name\":\"endDate\",\"value\":[\"%s\"]}]},\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
                } else {
                    //objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"betweenDate\",\"options\":[{\"name\":\"startDate\",\"value\":[\"%s\"]}, {\"name\":\"endDate\",\"value\":[\"%s\"]}]}}}";
                    objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"betweenDate\",\"options\":[{\"name\":\"startDate\",\"value\":[\"%s\"]}, {\"name\":\"endDate\",\"value\":[\"%s\"]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
                }
                objectFilter = String.format(objectFilterTemplate, startTimeString, endTimeString);
            } else if (openTicketOnly) {
                //objectFilter = "{\"tickets\":{\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}}}";
                objectFilter = "{\"tickets\":{\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
            }

            if (objectFilter != null) {
                log.info("read ibm ticket objectFilter:{}", objectFilter);
                queryParams = "&objectFilter=" + URLEncoder.encode(objectFilter, AppConstants.UTF8);
            }
        } catch (UnsupportedEncodingException e) {
            log.error("URLEncoder exception. {}", e);
        }
        return queryParams;
    }

    private static String afterTimeQueryParam(Date afterTime, boolean openTicketOnly) {
        String queryParams = "";
        try {
            String objectFilter = null;
            if (afterTime != null) {
                final SimpleDateFormat dateFormat = getDateFormat();
                final String afterTimeString = dateFormat.format(afterTime);
                String objectFilterTemplate;
                if (openTicketOnly) {
                    //objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"greaterThanDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]},\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}}}";
                    objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"greaterThanDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]},\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
                } else {
                    //objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"greaterThanDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]}}}";
                    objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"greaterThanDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
                }
                objectFilter = String.format(objectFilterTemplate, afterTimeString);
            } else if (openTicketOnly) {
                //objectFilter = "{\"tickets\":{\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}}}";
                objectFilter = "{\"tickets\":{\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
            }

            if (objectFilter != null) {
                log.info("read ibm ticket objectFilter:{}", objectFilter);
                queryParams = "&objectFilter=" + URLEncoder.encode(objectFilter, AppConstants.UTF8);
            }
        } catch (UnsupportedEncodingException e) {
            log.error("URLEncoder exception. {}", e);
        }
        return queryParams;
    }

    private static String forDayQueryParam(Date targetDay, boolean openTicketOnly) {
        String queryParams = "";
        try {
            String objectFilter = null;
            if (targetDay != null) {
                final SimpleDateFormat dateFormat = getDateFormat();
                final String timeString = dateFormat.format(targetDay);
                String objectFilterTemplate;
                if (openTicketOnly) {
                    //objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"isDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]},\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}}}";
                    objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"isDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]},\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
                } else {
                    //objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"isDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]}}}";
                    objectFilterTemplate = "{\"tickets\":{\"createDate\":{\"operation\":\"isDate\",\"options\":[{\"name\":\"date\",\"value\":[\"%s\"]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
                }
                objectFilter = String.format(objectFilterTemplate, timeString);
            } else if (openTicketOnly) {
                //objectFilter = "{\"tickets\":{\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}}}";
                objectFilter = "{\"tickets\":{\"statusId\":{\"operation\":\"in\",\"options\":[{\"name\":\"data\",\"value\":[1001,1004]}]}, \"id\":{\"operation\":\"orderBy\",\"options\":[{\"name\":\"sort\",\"value\":[\"ASC\"]},{\"name\":\"sortOrder\",\"value\":[0]}]}}}";
            }
            if (objectFilter != null) {
                log.info("read ibm ticket objectFilter:{}", objectFilter);
                queryParams = "&objectFilter=" + URLEncoder.encode(objectFilter, AppConstants.UTF8);
            }
        } catch (UnsupportedEncodingException e) {
            log.error("URLEncoder exception. {}", e);
        }
        return queryParams;
    }
}
