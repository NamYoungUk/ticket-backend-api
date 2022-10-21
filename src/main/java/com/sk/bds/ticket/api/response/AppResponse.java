package com.sk.bds.ticket.api.response;

import com.sk.bds.ticket.api.data.model.AppConstants;
import lombok.Data;

import java.util.HashMap;

@Data
public class AppResponse {
    private String status;
    private HashMap<String, Object> data;

    public AppResponse() {
        status = AppConstants.STATUS_OK;
        data = new HashMap<>();
    }

    private AppResponse(HashMap<String, Object> data) {
        status = AppConstants.STATUS_OK;
        this.data = new HashMap<>();
        this.data.putAll(data);
    }

    public void setData(String key, Object value) {
        data.put(key, value);
    }

    public static AppResponse from() {
        return new AppResponse();
    }

    public static AppResponse from(HashMap<String, Object> data) {
        return new AppResponse(data);
    }
}
