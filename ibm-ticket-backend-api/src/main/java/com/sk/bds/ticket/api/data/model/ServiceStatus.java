package com.sk.bds.ticket.api.data.model;

import lombok.Data;

@Data
public class ServiceStatus {
    private String stage;
    private String name;
    private String version;
    private long buildTime;
    private long deployTime;
    private long startTime;
    private long initializedTime;

    public ServiceStatus() {
        startTime = System.currentTimeMillis();
        initializedTime = 0;
    }

    public boolean isInitialized() {
        return (initializedTime > 0);
    }

    public void setDeployInfo(AppDeployInfo info) {
        if (info != null) {
            name = info.getAppName();
            version = info.getAppVersion();
            buildTime = info.getBuildTime();
            deployTime = info.getDeployTime();
        }
    }
}
