package com.sk.bds.ticket.api.data.model.freshdesk;

import lombok.Data;
import org.apache.http.Header;
import org.json.JSONObject;

@Data
public class FreshdeskTicketResponse {
    Header[] headers;
    JSONObject responseBody;
}
