package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
public class SlaViolationEntity {
    private static final String KeyId = "ticketId";
    private static final String KeyAssignedLevel = "assignedLevel";
    private static final String KeyTicketStatus = "ticketStatus";
    private static final String KeyL1TimeSeconds = "l1TimeSeconds"; //elapsed time by L1. Lead Time
    private static final String KeyL2TimeSeconds = "l2TimeSeconds"; //elapsed time by L2 Lead Time
    private static final String KeyTicketCreatedTime = "ticketCreatedTime";
    private static final String KeyL1ResponseTime = "l1ResponseTime";
    private static final String KeyL2AssignTime = "l2AssignTime";
    private static final String KeyL2ResponseTime = "l2ResponseTime";
    private static final String KeyL1 = "l1";
    private static final String KeyL2 = "l2";

    @JsonProperty(KeyId)
    String ticketId;
    @JsonProperty(KeyAssignedLevel)
    AgentLevel assignedLevel;
    @JsonProperty(KeyTicketStatus)
    TicketStatus ticketStatus;
    @JsonProperty(KeyTicketCreatedTime)
    //@JsonSerialize(using = LocalDateSerializer.class)
    //@JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = AppConstants.LOCAL_TIME_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date ticketCreatedTime;
    @JsonProperty(KeyL1ResponseTime)
    //@JsonSerialize(using = LocalDateSerializer.class)
    //@JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = AppConstants.LOCAL_TIME_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date l1ResponseTime;
    @JsonProperty(KeyL2AssignTime)
    //@JsonSerialize(using = LocalDateSerializer.class)
    //@JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = AppConstants.LOCAL_TIME_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date l2AssignTime;
    @JsonProperty(KeyL2ResponseTime)
    //@JsonSerialize(using = LocalDateSerializer.class)
    //@JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = AppConstants.LOCAL_TIME_FORMAT, timezone = AppConstants.LOCAL_TIME_ZONE_ID)
    Date l2ResponseTime;

    @JsonProperty(KeyL1TimeSeconds)
    boolean l1Responded;
    @JsonProperty(KeyL2TimeSeconds)
    boolean l2Responded;
    @JsonProperty(KeyL1TimeSeconds)
    int l1TimeSeconds;
    @JsonProperty(KeyL2TimeSeconds)
    int l2TimeSeconds;

    @JsonProperty(KeyL1)
    Agent l1;
    @JsonProperty(KeyL2)
    Agent l2;

    public SlaViolationEntity() {
        init();
    }

    public SlaViolationEntity(String ticketId, TicketStatus ticketStatus) {
        init();
        this.ticketId = ticketId;
        this.ticketStatus = ticketStatus;
    }

    private void init() {
        this.assignedLevel = AgentLevel.notAssigned;
        this.ticketStatus = TicketStatus.opened;
        this.l1Responded = false;
        this.l2Responded = false;
        this.l1TimeSeconds = 0;
        this.l2TimeSeconds = 0;
    }

    public void setL1(Agent l1) {
        //this.l1 = l1;
    }

    public void setL2(Agent l2) {
        //this.l2 = l2;
    }

    public boolean isL1Violation(int slaTimeSeconds) {
        if (assignedLevel == AgentLevel.l1 || assignedLevel == AgentLevel.l2) {
            return (l1TimeSeconds > slaTimeSeconds);
        }
        return false;
    }

    public boolean isL2Violation(int slaTimeSeconds) {
        if (assignedLevel == AgentLevel.l2) {
            return (l2TimeSeconds > slaTimeSeconds);
        }
        return false;
    }
}
