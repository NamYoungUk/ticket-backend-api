package com.sk.bds.ticket.api.service;

import com.ifountain.opsgenie.client.OpsGenieClient;
import com.ifountain.opsgenie.client.swagger.ApiException;
import com.ifountain.opsgenie.client.swagger.model.CreateAlertRequest;
import com.ifountain.opsgenie.client.swagger.model.SuccessResponse;
import com.ifountain.opsgenie.client.swagger.model.TeamRecipient;
import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.util.RestApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OpsgenieService {
    private static final String UTF8 = "UTF-8";
    private static final int TITLE_MAX = 130;
    private static final int DESCRIPTION_MAX = 15000;

    private static final AppConfig config = AppConfig.getInstance();
    private static final OpsGenieClient client = new OpsGenieClient();

    static {
        client.setApiKey(config.getOpsgenieApiKey());
    }

    private static String getApiEndpoint() {
        return config.getOpsgenieApiEndpoint();
    }

    private static RestApiUtil.RestApiResult request(String api, HttpMethod method, HttpEntity body) throws IOException, URISyntaxException {
        //https://docs.opsgenie.com/docs/alert-api#create-alert
        URL targetUrl = new URL(getApiEndpoint() + api);
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(HttpHeaders.AUTHORIZATION, "GenieKey " + config.getOpsgenieApiKey()));
        return RestApiUtil.request(targetUrl.toString(), method, headers, body, null);
    }

    private static CreateAlertRequest buildAlertRequest(String title, String description, CreateAlertRequest.PriorityEnum priority) {
        //AlertApi client = new OpsGenieClient().alertV2();
        //client.getApiClient().setApiKey(config.getOpsgenieApiKey());
        if (title == null) {
            return null;
        }
        if (title.length() > TITLE_MAX) {
            title = title.substring(0, TITLE_MAX - 1);
        }
        if (description != null && description.length() > DESCRIPTION_MAX) {
            description = description.substring(0, DESCRIPTION_MAX - 1);
        }

        CreateAlertRequest request = new CreateAlertRequest();
        request.setMessage(title);
        if (description != null) {
            request.setDescription(description);
        }
        if (config.getOpsgenieAlertTeam() != null) {
            List<TeamRecipient> teams = new ArrayList<>();
            TeamRecipient target = new TeamRecipient();
            target.setName(config.getOpsgenieAlertTeam());
            teams.add(target);
            request.setTeams(teams);
        }
        if (priority != null) {
            request.setPriority(priority);
        }
        return request;
    }

    public static SuccessResponse createAlert(String title, String description) throws ApiException {
        log.info("title: {}, description:{}", title, description);
        //AlertApi client = new OpsGenieClient().alertV2();
        //client.getApiClient().setApiKey(config.getOpsgenieApiKey());
        CreateAlertRequest request = buildAlertRequest(title, description, null);
        SuccessResponse response = client.alertV2().createAlert(request);
        return response;
    }

    public static SuccessResponse createAlert(String title, String description, CreateAlertRequest.PriorityEnum priority) throws ApiException {
        log.info("title: {}, description:{}", title, description);
        CreateAlertRequest request = buildAlertRequest(title, description, priority);
        SuccessResponse response = client.alertV2().createAlert(request);
        return response;
    }

    public static JSONObject createAlertByHttp(String title, String description) throws IOException, URISyntaxException {
        log.info("title: {}, description:{}", title, description);
        JSONObject data = new JSONObject();
        if (title != null) {
            data.put("message", title);
        }
        if (description != null) {
            data.put("description", description);
        }
        if (config.getOpsgenieAlertTeam() != null) {
            JSONArray responders = new JSONArray();
            JSONObject team = new JSONObject();
            team.put("name", config.getOpsgenieAlertTeam());
            team.put("type", "team");
            responders.put(team);
            data.put("responders", responders);
        }
        HttpEntity entity = new StringEntity(data.toString(), ContentType.APPLICATION_JSON.withCharset(Charset.forName(UTF8)));
        RestApiUtil.RestApiResult result = request(String.format("/v2/alerts"), HttpMethod.POST, entity);
        return new JSONObject(result.getResponseBody());
    }
}
