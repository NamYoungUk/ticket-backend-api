package com.sk.bds.ticket.api.data.model;

public enum SyncTriggerType {
    Invalid,
    Instant,
    Auto,
    Manual,
    Schedule;

    public boolean isInvalid() {
        return Invalid.equals(this);
    }

    public boolean isInstant() {
        return Instant.equals(this);
    }

    public boolean isAuto() {
        return Auto.equals(this);
    }

    public boolean isManual() {
        return Manual.equals(this);
    }

    public boolean isSchedule() {
        return Schedule.equals(this);
    }

    public static boolean isInstant(String triggerName) {
        return Instant.name().equalsIgnoreCase(triggerName);
    }

    public static boolean isAuto(String triggerName) {
        return Auto.name().equalsIgnoreCase(triggerName);
    }

    public static boolean isManual(String triggerName) {
        return Manual.name().equalsIgnoreCase(triggerName);
    }

    public static boolean isSchedule(String triggerName) {
        return Schedule.name().equalsIgnoreCase(triggerName);
    }

    public static SyncTriggerType from(String triggerName) {
        if (Instant.name().equalsIgnoreCase(triggerName)) {
            return Instant;
        } else if (Auto.name().equalsIgnoreCase(triggerName)) {
            return Auto;
        } else if (Manual.name().equalsIgnoreCase(triggerName)) {
            return Manual;
        } else if (Schedule.name().equalsIgnoreCase(triggerName)) {
            return Schedule;
        }
        return Invalid;
    }
}
