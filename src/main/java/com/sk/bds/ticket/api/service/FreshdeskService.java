package com.sk.bds.ticket.api.service;

import com.ifountain.opsgenie.client.swagger.ApiException;
import com.ifountain.opsgenie.client.swagger.model.CreateAlertRequest;
import com.ifountain.opsgenie.client.swagger.model.SuccessResponse;
import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskAttachment;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketResponse;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketStatus;
import com.sk.bds.ticket.api.data.model.ibm.IbmTicketSolveReason;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.util.RestApiUtil;
import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;

@Slf4j
public class FreshdeskService {

    public static final String KeyResults = "results";
    public static final String KeyTotalCount = "total";
    //freshdesk API Rate limit가 있습니다.
    //5000 call/hour 가 limit입니다.

    /*
     //https://developers.freshdesk.com/api/#list_all_tickets
     2. A maximum of 300 pages (9000 tickets) will be returned.
    */
    public static final int TICKET_LIST_PAGE_MAX = 300;

    /*
    //Pagination: https://developers.freshdesk.com/api/#pagination
    API responses that return a list of objects, such as View List of Tickets and View List of Contacts are paginated.
    To scroll through the pages, add the parameter page to the query string. The page number starts with 1.
    By default, the number of objects returned per page is 30.
    This can be adjusted by adding the per_page parameter to the query string.
    The maximum number of objects that can be retrieved per page is 100.
    Invalid values and values greater than 100 will result in an error.
    */
    public static final int PAGE_START_NUMBER = 1;
    public static final int CONVERSATION_LIST_ITEMS_PER_PAGE = 100;
    public static final int CONTACT_LIST_ITEMS_PER_PAGE = 100;
    public static final int AGENT_LIST_ITEMS_PER_PAGE = 100;
    public static final int TICKET_LIST_ITEMS_PER_PAGE = 100;

    /*
    //https://developers.freshdesk.com/api/#filter_tickets
    8. The number of objects returned per page is 30 also the total count of the results will be returned along with the result
    9. To scroll through the pages add page parameter to the url. The page number starts with 1 and should not exceed 10
    */
    public static final int TICKET_SEARCH_PAGE_MAX = 10;
    public static final int TICKET_SEARCH_ITEMS_PER_PAGE = 30;
    public static final int TICKET_SEARCH_ITEMS_TOTAL_MAX = (TICKET_SEARCH_PAGE_MAX * TICKET_SEARCH_ITEMS_PER_PAGE);

    private static final int WARNING_REMAIN_COUNT_500 = 500;
    private static final int WARNING_REMAIN_COUNT_200 = 200;
    private static int rateLimitTotalCount = 0;
    private static int rateLimitRemainingCount = 0;
    private static int rateLimitUsedCurrentCount = 0;
    private static int rateLimitRetryAfterSeconds = 0;
    private static long rateLimitApiCallBlockExpireTime = 0;
    private static boolean warning500AlertSent = false;
    private static boolean warning200AlertSent = false;
    private static Date rateLimitCountResetTime = null;
    private static final String UTF8 = "UTF-8";
    private static final String RateLimitTotal = "X-Ratelimit-Total";
    private static final String RateLimitRemaining = "X-Ratelimit-Remaining";
    private static final String RateLimitUsedCurrent = "X-Ratelimit-Used-CurrentRequest";
    private static final String RetryAfter = "Retry-After";
    //https://developers.freshdesk.com/api/ #Rate Limit
    //the latest API rate limits
    //X-RateLimit-Total					Total number of API calls allowed per minute.
    //X-RateLimit-Remaining				The number of requests remaining in the current rate limit window.
    //X-RateLimit-Used-CurrentRequest	The number of API calls consumed by the current request. Most API requests consume one call, however, including additional information in the response will consume more calls.
    //Retry-After						The number in seconds that you will have to wait to fire your next API request. This header will be returned only when the rate limit has been reached.
    //the old API rate limits
    //X-RateLimit-Total	                Total number of API calls allowed per hour.
    //X-RateLimit-Remaining	            The number of requests remaining in the current rate limit window.
    //X-RateLimit-Used-CurrentRequest	The number of API calls consumed by the current request. Most API requests consume one call, however, including additional information in the response will consume more calls.
    //Retry-After	                    The number in seconds that you will have to wait to fire your next API request. This header will be returned only when the rate limit has been reached.

    private static final AppConfig config = AppConfig.getInstance();

    private static String getApiEndpoint() {
        return config.getFreshdeskApiEndpoint();
    }

    private static UsernamePasswordCredentials getCredentials() {
        return new UsernamePasswordCredentials(config.getFreshdeskApiKey(), "X");
    }

    public static JSONObject getCurrentRateLimit() {
        JSONObject output = new JSONObject();
        long now = System.currentTimeMillis();
        if (rateLimitCountResetTime != null) {
            output.put("RateLimitCountResetTime", TicketUtil.getLocalTimeString(rateLimitCountResetTime));
        } else {
            output.put("RateLimitCountResetTime", "UnKnown");
        }
        output.put("RateLimitTotalCount", rateLimitTotalCount);
        output.put("RateLimitRemainingCount", rateLimitRemainingCount);
        if (rateLimitApiCallBlockExpireTime > now) {
            output.put("ApiCallBlockExpireTime", TicketUtil.getLocalTimeString(new Date(rateLimitApiCallBlockExpireTime)));
            output.put("RateLimitRetryAfterSeconds", (rateLimitApiCallBlockExpireTime - now) / 1000);
        } else {
            output.put("ApiCallBlockExpireTime", "No blocked");
        }
        output.put("warning500AlertSent", warning500AlertSent);
        output.put("warning200AlertSent", warning200AlertSent);
        return output;
    }

    public static boolean canApiCall() {
        return (rateLimitApiCallBlockExpireTime < System.currentTimeMillis());
    }

    public static int getRateLimitTotalCount() {
        return rateLimitTotalCount;
    }

    public static int getRateLimitRemainingCount() {
        return rateLimitRemainingCount;
    }

    public static int getRateLimitRetryAfterSeconds() {
        return rateLimitRetryAfterSeconds;
    }

    public static long getRateLimitResetTime() {
        if (rateLimitCountResetTime != null) {
            return rateLimitCountResetTime.getTime();
        }
        return 0;
    }

    public static long getRemainingTimeUntilApiBlockExpires() {
        long now = System.currentTimeMillis();
        if (rateLimitApiCallBlockExpireTime > now) {
            return rateLimitApiCallBlockExpireTime - now;
        }
        return 0;
    }

    private static void checkApiCall(String api, HttpMethod method) throws AppInternalError {
        if (!canApiCall()) {
            String errorMessage = "[" + method + "] " + api + " - " + rateLimitErrorMessage();
            log.error(errorMessage);
            throw AppInternalError.freshdeskApiCallRateLimitExceed(errorMessage);
        }
    }

    private static String rateLimitErrorMessage() {
        String blockingMessage = "";
        long now = System.currentTimeMillis();
        if (rateLimitApiCallBlockExpireTime > now) {
            String until = TicketUtil.getLocalTimeString(new Date(rateLimitApiCallBlockExpireTime));
            long retryAfter = (rateLimitApiCallBlockExpireTime - now) / 1000;
            blockingMessage = String.format("Freshdesk API calls have reached maximum(%d) of plan. Freshdesk API calls are blocked until %s. Retry after %d seconds.", rateLimitTotalCount, until, retryAfter);
        }
        return blockingMessage;
    }

    private static RestApiUtil.RestApiResult request(String api, HttpMethod method, HttpEntity body) throws AppInternalError {
        checkApiCall(api, method);

        URL targetUrl = null; // URL object from API endpoint:
        RestApiUtil.RestApiResult result = null;
        try {
            targetUrl = new URL(getApiEndpoint() + api);
            result = RestApiUtil.request(targetUrl.toString(), method, null, body, getCredentials());
            Header[] responseHeaders = result.getHeaders();
            for (Header header : responseHeaders) {
                if (RateLimitTotal.equalsIgnoreCase(header.getName())) {
                    rateLimitTotalCount = Integer.valueOf(header.getValue());
                } else if (RateLimitRemaining.equalsIgnoreCase(header.getName())) {
                    int currentRemainCount = Integer.valueOf(header.getValue());
                    if (currentRemainCount > rateLimitRemainingCount) {
                        log.info("RateLimitRemainCount increased. before:{}, current: {}", rateLimitRemainingCount, currentRemainCount);
                        rateLimitCountResetTime = new Date();
                        rateLimitApiCallBlockExpireTime = 0;
                        rateLimitRetryAfterSeconds = 0;
                    }
                    rateLimitRemainingCount = currentRemainCount;
                } else if (RateLimitUsedCurrent.equalsIgnoreCase(header.getName())) {
                    rateLimitUsedCurrentCount = Integer.valueOf(header.getValue());
                } else if (RetryAfter.equalsIgnoreCase(header.getName())) {
                    rateLimitRetryAfterSeconds = Integer.valueOf(header.getValue());
                    if (rateLimitRetryAfterSeconds > 0) {
                        final int blockMargin = 10000;
                        rateLimitApiCallBlockExpireTime = System.currentTimeMillis() + (rateLimitRetryAfterSeconds * 1000) + blockMargin;
                        log.info("Block Freshdesk api call until {}", TicketUtil.getLocalTimeString(new Date(rateLimitApiCallBlockExpireTime)));
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw AppInternalError.freshdeskApiError(api + " call failed. {}", e);
        }

        String logMessage = String.format("Freshdesk API RateLimit - Total:%d, Remaining:%d, UsedCurrent:%d, RetryAfter:%d, Method:%s, URL:%s", rateLimitTotalCount, rateLimitRemainingCount, rateLimitUsedCurrentCount, rateLimitRetryAfterSeconds, method.toString(), targetUrl.toString());
        log.debug(logMessage);
        if (rateLimitRemainingCount <= WARNING_REMAIN_COUNT_500) {
            if (!warning500AlertSent) {
                sendOpsgenieAlert(targetUrl.toString(), logMessage);
            } else if (!warning200AlertSent && (rateLimitRemainingCount <= WARNING_REMAIN_COUNT_200)) {
                sendOpsgenieAlert(targetUrl.toString(), logMessage);
            }
        } else {
            warning500AlertSent = false;
            warning200AlertSent = false;
        }

        checkApiCall(api, method);

        return result;
    }

    private static void addFileEntity(MultipartEntityBuilder entityBuilder, List<FreshdeskAttachment> attachments) {
        if (entityBuilder != null && attachments != null) {
            Charset charsetUtf8 = Charset.forName(UTF8);
            FileNameMap fileNameMap = URLConnection.getFileNameMap();
            //String mimeType = URLConnection.guessContentTypeFromName("sample.txt");
            for (FreshdeskAttachment attachment : attachments) {
                ContentType contentType = ContentType.APPLICATION_OCTET_STREAM.withCharset(UTF8); //ContentType.TEXT_PLAIN
                String mimeType = fileNameMap.getContentTypeFor(attachment.getFileName());
                if (mimeType != null) {
                    contentType = ContentType.create(mimeType, charsetUtf8);
                }
                entityBuilder.addBinaryBody(FreshdeskTicketField.Attachments + "[]", attachment.getData(), contentType, attachment.getFileName());
            }
        }
    }

    private static HttpEntity buildHttpEntity(JSONObject jsonData, List<FreshdeskAttachment> attachments) {
        HttpEntity entity;
        if (jsonData == null && attachments == null) {
            log.error(TicketUtil.freshdeskErrorText("http content is empty."));
            return null;
        }

        if (attachments == null) {
            entity = new StringEntity(jsonData.toString(), ContentType.APPLICATION_JSON.withCharset(Charset.forName(UTF8)));
        } else {
            MultipartEntityBuilder meb = MultipartEntityBuilder.create();
            meb.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            if (jsonData != null) {
                for (String key : jsonData.keySet()) {
                    if (FreshdeskTicketField.CustomFields.equals(key)) {
                        JSONObject custom_f = new JSONObject(jsonData.optString(key));
                        for (String key1 : custom_f.keySet()) {
                            meb.addTextBody(FreshdeskTicketField.CustomFields + "[" + key1 + "]", custom_f.optString(key1), ContentType.MULTIPART_FORM_DATA.withCharset(UTF8));
                        }
                    } else if (FreshdeskTicketField.Tags.equals(key)) {
                        JSONArray tagArray = jsonData.getJSONArray(FreshdeskTicketField.Tags);
                        //meb.addTextBody(FreshdeskTicketField.Tags, "[]"); //if list is empty
                        if (tagArray != null && tagArray.length() > 0) {
                            for (int i = 0; i < tagArray.length(); i++) {
                                meb.addTextBody(FreshdeskTicketField.Tags + "[]", tagArray.optString(i), ContentType.MULTIPART_FORM_DATA.withCharset(UTF8));
                            }
                        }
                    } else if (FreshdeskTicketField.RelatedTicketIds.equals(key)) {
                        //Freshdesk createTicket api always fails if request the related_ticket_ids field and attachment field in same time.
                    /*JSONArray idArray = data.getJSONArray(FreshdeskTicketField.RelatedTicketIds);
                    if (idArray != null && idArray.length() > 0) {
                        for (int i = 0; i < idArray.length(); i++) {
                            //byte[] ticketIdBytes = ByteBuffer.allocate(4).putInt(idArray.optInt(i)).array();
                            //meb.addBinaryBody(FreshdeskTicketField.RelatedTicketIds + "[]", ticketIdBytes);
                            meb.addTextBody(FreshdeskTicketField.RelatedTicketIds + "[]", idArray.optString(i), ContentType.MULTIPART_FORM_DATA.withCharset(UTF8));
                        }
                    }*/
                    } else if (FreshdeskTicketField.CcEmails.equals(key) || FreshdeskTicketField.ToEmails.equals(key) || FreshdeskTicketField.NotifyEmails.equals(key)) {
                        JSONArray emailsArray = jsonData.getJSONArray(key);
                        if (emailsArray != null && emailsArray.length() > 0) {
                            for (int i = 0; i < emailsArray.length(); i++) {
                                meb.addTextBody(key + "[]", emailsArray.optString(i), ContentType.MULTIPART_FORM_DATA.withCharset(UTF8));
                            }
                        }
                    } else {
                        meb.addTextBody(key, jsonData.optString(key), ContentType.MULTIPART_FORM_DATA.withCharset(UTF8));
                    }
                }
            }
            addFileEntity(meb, attachments);
            entity = meb.build();
        }
        return entity;
    }


    public static FreshdeskTicketResponse getTicket(String ticketId) throws AppInternalError {

        FreshdeskTicketResponse response = new FreshdeskTicketResponse();

        if (ticketId == null) {
            log.error("ticketId is null.");
            return response;
        }

        RestApiUtil.RestApiResult result = null;
        try {
            result = request(String.format("/api/v2/tickets/%s", ticketId), HttpMethod.GET, null);
        } catch (AppInternalError e) {
            throw e; // bypassing the error handling in the caller method.
        } finally {
            response.setHeaders(result.getHeaders());

            String xRequestId = findHeader(response, "X-Request-Id");
            String xTraceId = findHeader(response, "X-Trace-Id");
            log.trace(TicketUtil.freshdeskErrorText("status: {}, ticketId: {}, X-Request-Id: {}, X-Trace-Id: {}") , result.getStatus(), ticketId, xRequestId, xTraceId);
            log.trace(TicketUtil.freshdeskErrorText("status: {}, body: {}") , result.getStatus(), result.getResponseBody());
        }

        if (result.isOK()) {
            response.setResponseBody(new JSONObject(result.getResponseBody()));
            return response;
        }

        return response;
    }

    // Freshdesk API call checker for debug purpose. @2022-07-05
    private static String findHeader(FreshdeskTicketResponse response, String searchKey) {
        if (response == null)
            return "response is null.";

        if (searchKey == null)
            return "searchKey is null.";

        Header[] responseHeaders = response.getHeaders();
        if (responseHeaders == null)
            return "responseHeader is null.";

        for (Header header : responseHeaders) {
            if (header == null) continue;
            if (searchKey.equals(header.getName()))
                return header.getValue();
        }
        return searchKey + " Not Exist.";
    }

    public static JSONObject createTicket(JSONObject ticketData, List<FreshdeskAttachment> attachments) throws AppInternalError {
        HttpEntity entity = buildHttpEntity(ticketData, attachments);
        if (entity != null) {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets"), HttpMethod.POST, entity);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        }
        return null;
    }

    public static JSONObject updateTicket(String ticketId, JSONObject ticketData) throws AppInternalError {
        if (ticketId != null && ticketData != null) {
            HttpEntity entity = new StringEntity(ticketData.toString(), ContentType.APPLICATION_JSON.withCharset(Charset.forName(UTF8)));
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s", ticketId), HttpMethod.PUT, entity);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        }
        return null;
    }

    public static JSONObject updateTicket(String ticketId, JSONObject ticketData, List<FreshdeskAttachment> attachments) throws AppInternalError {
        if (ticketId != null) {
            HttpEntity entity = buildHttpEntity(ticketData, attachments);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s", ticketId), HttpMethod.PUT, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            }
        }
        return null;
    }

    public static JSONObject closeTicket(String ticketId, JSONObject customFields) throws AppInternalError {
        if (ticketId != null) {
            JSONObject updateData = new JSONObject();
            updateData.put(FreshdeskTicketField.Status, FreshdeskTicketStatus.Closed); //Resolved
            if (customFields != null) {
                updateData.put(FreshdeskTicketField.CustomFields, customFields);
            }
            return updateTicket(ticketId, updateData);
        }
        return null;
    }

    public static JSONObject changeTicketStatus(String ticketId, int status) throws AppInternalError {
        if (ticketId != null) {
            JSONObject updateData = new JSONObject();
            updateData.put(FreshdeskTicketField.Status, status);
            return updateTicket(ticketId, updateData);
        }
        return null;
    }

    public static JSONObject createReply(String ticketId, JSONObject conversationData, List<FreshdeskAttachment> attachments) throws AppInternalError {
        if (ticketId != null && conversationData != null) {
            HttpEntity entity = buildHttpEntity(conversationData, attachments);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s/reply", ticketId), HttpMethod.POST, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            }
        }
        return null;
    }

    public static JSONObject createReply(String ticketId, String replyHtmlText, List<FreshdeskAttachment> attachments) throws AppInternalError {
        if (ticketId != null && replyHtmlText != null) {
            JSONObject conversationData = new JSONObject();
            conversationData.put(FreshdeskTicketField.ConversationBodyHtml, replyHtmlText);
            HttpEntity entity = buildHttpEntity(conversationData, attachments);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s/reply", ticketId), HttpMethod.POST, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            }
        }
        return null;
    }

    public static JSONObject createNote(String ticketId, JSONObject conversationData, List<FreshdeskAttachment> attachments) throws AppInternalError {
        //By default, any note that you add will be private. If you wish to add a public note, set the parameter to false.
        if (ticketId != null && conversationData != null) {
            HttpEntity entity = buildHttpEntity(conversationData, attachments);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s/notes", ticketId), HttpMethod.POST, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            }
        }
        return null;
    }

    public static JSONObject createNote(String ticketId, String noteHtmlText, List<FreshdeskAttachment> attachments) throws AppInternalError {
        //By default, any note that you add will be private. If you wish to add a public note, set the parameter to false.
        if (ticketId != null && noteHtmlText != null) {
            JSONObject conversationData = new JSONObject();
            conversationData.put(FreshdeskTicketField.ConversationBodyHtml, noteHtmlText);
            HttpEntity entity = buildHttpEntity(conversationData, attachments);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s/notes", ticketId), HttpMethod.POST, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            }
        }
        return null;
    }

    public static JSONObject createErrorNote(String ticketId, String errorNote) throws AppInternalError {
        //By default, any note that you add will be private. If you wish to add a public note, set the parameter to false.
        if (ticketId != null && errorNote != null) {
            JSONObject conversationData = new JSONObject();
            conversationData.put(FreshdeskTicketField.ConversationBodyHtml, String.format("<div>%s</div>", errorNote));
            conversationData.put(FreshdeskTicketField.Private, true);
            HttpEntity entity = buildHttpEntity(conversationData, null);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s/notes", ticketId), HttpMethod.POST, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            }
        }
        return null;
    }

    public static JSONObject getTicketsByAfterTime(String csp, String date, int page) throws AppInternalError {
        final String query = String.format("\"cf_csp:%s AND created_at:>'%s'\"", csp, date);
        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    //https://developers.freshdesk.com/api/#filter_tickets
    //4. Get the list of locked tickets belong to Finance or Marketing sector (Custom Fields: locked, sector).
    //    "(cf_sector:'finance' OR cf_sector:'marketing') AND cf_locked:true"
    //6. Relational operators greater than or equal to :> and less than or equal to :< can be used along with date fields and numeric fields
    //final String query = String.format("\"cf_csp:%s AND status:%d AND created_at:>'%s' AND created_at:<'%s'\"", csp, FreshdeskTicketStatus.Open, startDate, endDate);
    public static JSONObject getOpenedTicketsByAfterTime(String csp, String date, int page) throws AppInternalError {
        String query;
        if (config.isFreshdeskOpenTicketStatusIncludesPending()) {
            query = String.format("\"cf_csp:%s AND (status:%d OR status:%d) AND created_at:>'%s'\"", csp, FreshdeskTicketStatus.Open, FreshdeskTicketStatus.Pending, date);
        } else {
            query = String.format("\"cf_csp:%s AND status:%d AND created_at:>'%s'\"", csp, FreshdeskTicketStatus.Open, date);
        }

        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONObject getClosedTicketsByAfterTime(String csp, String date, int page) throws AppInternalError {
        String query;
        if (config.isFreshdeskClosedTicketStatusIncludesResolved()) {
            query = String.format("\"cf_csp:%s AND (status:%d OR status:%d) AND created_at:>'%s'\"", csp, FreshdeskTicketStatus.Closed, FreshdeskTicketStatus.Resolved, date);
        } else {
            query = String.format("\"cf_csp:%s AND status:%d AND created_at:>'%s'\"", csp, FreshdeskTicketStatus.Closed, date);
        }

        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONObject getTicketsOfDay(String csp, String date, int page) throws AppInternalError {
        final String query = String.format("\"cf_csp:%s AND created_at:'%s'\"", csp, date);
        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONObject getOpenedTicketsOfDay(String csp, String date, int page) throws AppInternalError {
        String query;
        if (config.isFreshdeskOpenTicketStatusIncludesPending()) {
            query = String.format("\"cf_csp:%s AND (status:%d OR status:%d) AND created_at:'%s'\"", csp, FreshdeskTicketStatus.Open, FreshdeskTicketStatus.Pending, date);
        } else {
            query = String.format("\"cf_csp:%s AND status:%d AND created_at:'%s'\"", csp, FreshdeskTicketStatus.Open, date);
        }

        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONObject getClosedTicketsOfDay(String csp, String date, int page) throws AppInternalError {
        String query;
        if (config.isFreshdeskClosedTicketStatusIncludesResolved()) {
            query = String.format("\"cf_csp:%s AND (status:%d OR status:%d) AND created_at:'%s'\"", csp, FreshdeskTicketStatus.Closed, FreshdeskTicketStatus.Resolved, date);
        } else {
            query = String.format("\"cf_csp:%s AND status:%d AND created_at:'%s'\"", csp, FreshdeskTicketStatus.Closed, date);
        }
        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONObject getTicketsByPeriod(String csp, String startDate, String endDate, int page) throws AppInternalError {
        //6. Relational operators greater than or equal to :> and less than or equal to :< can be used along with date fields and numeric fields
        final String query = String.format("\"cf_csp:%s AND created_at:>'%s' AND created_at:<'%s'\"", csp, startDate, endDate);
        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONObject getOpenedTicketsByPeriod(String csp, String startDate, String endDate, int page) throws AppInternalError {
        String query;
        if (config.isFreshdeskOpenTicketStatusIncludesPending()) {
            query = String.format("\"cf_csp:%s AND (status:%d OR status:%d) AND created_at:>'%s' AND created_at:<'%s'\"", csp, FreshdeskTicketStatus.Open, FreshdeskTicketStatus.Pending, startDate, endDate);
        } else {
            query = String.format("\"cf_csp:%s AND status:%d AND created_at:>'%s' AND created_at:<'%s'\"", csp, FreshdeskTicketStatus.Open, startDate, endDate);
        }

        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONObject getClosedTicketsByPeriod(String csp, String startDate, String endDate, int page) throws AppInternalError {
        String query;
        if (config.isFreshdeskClosedTicketStatusIncludesResolved()) {
            query = String.format("\"cf_csp:%s AND (status:%d OR status:%d) AND created_at:>'%s' AND created_at:<'%s'\"", csp, FreshdeskTicketStatus.Closed, FreshdeskTicketStatus.Resolved, startDate, endDate);
        } else {
            query = String.format("\"cf_csp:%s AND status:%d AND created_at:>'%s' AND created_at:<'%s'\"", csp, FreshdeskTicketStatus.Closed, startDate, endDate);
        }

        try {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/search/tickets?query=%s&page=%d", URLEncoder.encode(query, UTF8), page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        } catch (UnsupportedEncodingException e) {
            throw AppInternalError.internalProcessingError(e);
        }
        return null;
    }

    public static JSONArray getConversations(String ticketId, int page) throws AppInternalError {
        if (ticketId != null) {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/tickets/%s/conversations?per_page=%d&page=%d", ticketId, CONVERSATION_LIST_ITEMS_PER_PAGE, page), HttpMethod.GET, null);
            if (result.isOK()) {
                return new JSONArray(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        }
        return null;
    }

    public static JSONObject getLastConversation(String ticketId) throws AppInternalError {
        int page = 1;
        int size = 0;
        JSONObject result = null;
        if (ticketId != null) {
            do {
                JSONArray fdConversations = getConversations(ticketId, page++);
                if (fdConversations != null) {
                    size = fdConversations.length();
                    if (size > 0) {
                        result = fdConversations.getJSONObject(fdConversations.length() - 1);
                    }
                } else {
                    break;
                }
            } while (size >= CONVERSATION_LIST_ITEMS_PER_PAGE);
        }
        return result;
    }

    public static String searchPublicUrlFromConversations(String ticketId) {
        if (ticketId != null) {
            try {
                int page = 1;
                int size = 0;
                do {
                    JSONArray fdConversations = getConversations(ticketId, page++);
                    if (fdConversations != null) {
                        size = fdConversations.length();
                        if (size > 0) {
                            for (int i = 0; i < fdConversations.length(); i++) {
                                JSONObject conversation = fdConversations.getJSONObject(i);
                                if (conversation.getBoolean(FreshdeskTicketField.Private)) {
                                    String conversationId = conversation.optString(FreshdeskTicketField.Id);
                                    String freshdeskBodyText = conversation.optString(FreshdeskTicketField.ConversationBodyText);
                                    if (freshdeskBodyText.contains(AppConstants.PUBLIC_URL_HEADER)) {
                                        String publicUrl = freshdeskBodyText.substring(freshdeskBodyText.indexOf(AppConstants.PUBLIC_URL_HEADER) + AppConstants.PUBLIC_URL_HEADER.length());
                                        return publicUrl;
                                    }
                                }
                            }
                        }
                    } else {
                        break;
                    }
                } while (size >= CONVERSATION_LIST_ITEMS_PER_PAGE);
            } catch (AppInternalError e) {
                log.error(TicketUtil.freshdeskErrorText("Ticket conversations loading failed for {}: {}"), ticketId, e.getMessage());
            }
        }
        return null;
    }

    public static JSONObject getAgent(long userId) throws AppInternalError {
        RestApiUtil.RestApiResult result = request(String.format("/api/v2/agents/%d", userId), HttpMethod.GET, null);
        if (result.isOK()) {
            return new JSONObject(result.getResponseBody());
        } else {
            log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
        }
        return null;
    }

    public static JSONArray getAgents() throws AppInternalError {
        int page = 1;
        int size = 0;
        JSONArray agents = new JSONArray();
        do {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/agents?per_page=%d&page=%d", AGENT_LIST_ITEMS_PER_PAGE, page++), HttpMethod.GET, null);
            if (result.isOK()) {
                JSONArray list = new JSONArray(result.getResponseBody());
                agents.putAll(list);
                size = list.length();
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                break;
            }
        } while (size >= AGENT_LIST_ITEMS_PER_PAGE);
        return agents;
    }

    public static JSONObject getContact(long userId) throws AppInternalError {
        RestApiUtil.RestApiResult result = request(String.format("/api/v2/contacts/%d", userId), HttpMethod.GET, null);
        if (result.isOK()) {
            return new JSONObject(result.getResponseBody());
        } else {
            log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
        }
        return null;
    }

    public static JSONArray getContacts() throws AppInternalError {
        int page = 1;
        int size = 0;
        JSONArray agents = new JSONArray();
        do {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/contacts?per_page=%d&page=%d", CONTACT_LIST_ITEMS_PER_PAGE, page++), HttpMethod.GET, null);
            if (result.isOK()) {
                JSONArray list = new JSONArray(result.getResponseBody());
                agents.putAll(list);
                size = list.length();
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                break;
            }
        } while (size >= CONTACT_LIST_ITEMS_PER_PAGE);
        return agents;
    }

    public static JSONArray getTicketFields() throws AppInternalError {
        //"https://skcczcareservice.freshdesk.com/api/v2/admin/ticket_fields"
        RestApiUtil.RestApiResult result = request("/api/v2/admin/ticket_fields", HttpMethod.GET, null);
        if (result.isOK()) {
            return new JSONArray(result.getResponseBody());
        } else {
            log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
        }
        return null;
    }

    public static JSONObject getTicketField(String fieldId) throws AppInternalError {
        //https://skcczcareservice.freshdesk.com/api/v2/admin/ticket_fields/2043002699944
        RestApiUtil.RestApiResult result = request(String.format("/api/v2/admin/ticket_fields/%s", fieldId), HttpMethod.GET, null);
        if (result.isOK()) {
            return new JSONObject(result.getResponseBody());
        } else {
            log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
        }
        return null;
    }

    public static JSONObject createTicketFields(JSONObject requestBody) throws AppInternalError {
        if (requestBody != null) {
            HttpEntity entity = buildHttpEntity(requestBody, null);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request("/api/v2/admin/ticket_fields", HttpMethod.POST, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            }
        }
        return null;
    }

    public static JSONObject updateTicketField(String fieldId, JSONObject requestBody) throws AppInternalError {
        //log.debug("fieldId: {}", fieldId);
        //log.debug("requestBody: {}", requestBody);
        if (fieldId != null && requestBody != null) {
            HttpEntity entity = buildHttpEntity(requestBody, null);
            if (entity != null) {
                RestApiUtil.RestApiResult result = request(String.format("/api/v2/admin/ticket_fields/%s", fieldId), HttpMethod.PUT, entity);
                if (result.isOK()) {
                    return new JSONObject(result.getResponseBody());
                } else {
                    log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
                }
            } else {
                log.error("Empty body.");
            }
        }
        return null;
    }

    public static JSONObject deleteTicketField(String fieldId) throws AppInternalError {
        if (fieldId != null) {
            RestApiUtil.RestApiResult result = request(String.format("/api/v2/admin/ticket_fields/%s", fieldId), HttpMethod.DELETE, null);
            if (result.isOK()) {
                return new JSONObject(result.getResponseBody());
            } else {
                log.error(TicketUtil.freshdeskErrorText("status:{}, body: {}"), result.getStatus(), result.getResponseBody());
            }
        }
        return null;
    }

    private static Semaphore alertLocker = new Semaphore(1, true);

    private static void sendOpsgenieAlert(String url, String logMessage) {
        log.debug("createOpsgenieAlert() - remains:{}, warning500AlertSent: {}", rateLimitRemainingCount, warning500AlertSent);
        alertLocker.tryAcquire();
        boolean needAlert = false;
        if (rateLimitRemainingCount <= WARNING_REMAIN_COUNT_500) {
            if (!warning500AlertSent && rateLimitRemainingCount > WARNING_REMAIN_COUNT_200) {
                needAlert = true;
            } else if (!warning200AlertSent && (rateLimitRemainingCount <= WARNING_REMAIN_COUNT_200)) {
                needAlert = true;
            }
        }

        if (needAlert) {
            DateFormat timeFormat = TicketUtil.getLocalDateFormat();
            String eventTime = timeFormat.format(System.currentTimeMillis());

            String title = String.format("[SP] WARNING - Freshdesk API rate limit remains %d", rateLimitRemainingCount);
            String description = "Freshdesk API Rate Limit has reached the warning level.\n";
            description += "\nTime : " + eventTime;
            description += "\nURL : " + url;
            description += "\nLog : " + logMessage;
            try {
                SuccessResponse response = OpsgenieService.createAlert(title, description, CreateAlertRequest.PriorityEnum.P5);
                log.info("Opsgenie Alert created. - response:" + response);
                if (rateLimitRemainingCount > WARNING_REMAIN_COUNT_200) {
                    warning500AlertSent = true;
                } else {
                    warning200AlertSent = true;
                }
            } catch (ApiException e) {
                log.error(TicketUtil.opsgenieErrorText("Opsgenie createAlert failed. " + e));
            }
        }
        alertLocker.release();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //IBM Only
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static JSONObject closeTicketForIbm(String ticketId, String solveReasonText) throws AppInternalError {
        if (ticketId != null) {
            JSONObject updateData = new JSONObject();
            String solveReason = IbmTicketSolveReason.TheOthers;
            updateData.put(FreshdeskTicketField.Status, FreshdeskTicketStatus.Closed);
            if (solveReasonText != null) {
                for (String reason : IbmTicketSolveReason.SolveReasons) {
                    if (solveReasonText.equals(reason)) {
                        solveReason = solveReasonText;
                        break;
                    }
                }
            }
            JSONObject updateCustomData = new JSONObject();
            updateCustomData.put(FreshdeskTicketField.CfSolveReason, solveReason);
            updateData.put(FreshdeskTicketField.CustomFields, updateCustomData);
            return updateTicket(ticketId, updateData);
        }
        return null;
    }
}
