package com.sk.bds.ticket.api.data.model.ibm;

public class IbmTicketEditorType {
    public static final String Agent = "AGENT";
    public static final String Bridge = "BRIDGE";
    public static final String Auto = "AUTO";
    public static final String Employee = "EMPLOYEE";
    public static final String User = "USER";

    public static boolean isAgent(String editorType) {
        if (editorType != null) {
            return Agent.equals(editorType);
        }
        return false;
    }

    public static boolean isEmployee(String editorType) {
        if (editorType != null) {
            return Employee.equals(editorType);
        }
        return false;
    }

    public static boolean isUser(String editorType) {
        if (editorType != null) {
            return User.equals(editorType);
        }
        return false;
    }
}