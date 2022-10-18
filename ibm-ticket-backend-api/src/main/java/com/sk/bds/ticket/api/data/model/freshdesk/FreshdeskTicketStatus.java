package com.sk.bds.ticket.api.data.model.freshdesk;

/*
    https://developers.freshdesk.com/api/#ticket_attributes
    Ticket Properties
    Every ticket uses certain fixed numerical values to denote its Status, and Priorities.
    These numerical values along with their meanings are given below.
*/
public class FreshdeskTicketStatus {
    public static final int Open = 2;
    public static final int Pending = 3;
    public static final int Resolved = 4;
    public static final int Closed = 5;
    public static final int WaitingOnCustomer = 6;
    public static final int WaitingOnThirdParty = 7;

    public static String toString(int value) {
        switch (value) {
            case Open:
                return "Open";
            case Pending:
                return "Pending";
            case Resolved:
                return "Resolved";
            case Closed:
                return "Closed";
            case WaitingOnCustomer:
                return "Waiting on Customer";
            case WaitingOnThirdParty:
                return "Waiting on Third Party";
        }
        return "Invalid Status";
    }

    public static boolean isClosed(int status) {
        return (status == Resolved) || (status == Closed);
    }

    public static boolean isOpen(int status) {
        return !isClosed(status);
    }
}

