package com.sk.bds.ticket.api.data.model;

import lombok.Data;

@Data
public class AppDeployInfo {
    private String appName;
    private String appVersion;
    private long buildTime;
    private long deployTime;
}
