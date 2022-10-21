package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sk.bds.ticket.api.util.JsonUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Data
@Slf4j
public class RequestBodyParam {
    private static final String KeyPublicUrl = "public_url";
    @JsonProperty(KeyPublicUrl)
    String publicUrl;

    public RequestBodyParam() {
        publicUrl = null;
    }

    public static RequestBodyParam from(String jsonText) {
        if (jsonText != null) {
            try {
                return JsonUtil.unmarshal(jsonText, RequestBodyParam.class);
            } catch (IOException e) {
                log.error("Failed deserialize to RequestBodyParam");
            }
        }
        return new RequestBodyParam();
    }
}
