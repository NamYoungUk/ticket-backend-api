package com.sk.bds.ticket.api.data.model.freshdesk;

public class FreshdeskTicketPriority {
    //https://developers.freshdesk.com/api/#tickets
    //PRIORITY	VALUE
    //Low	    1
    //Medium	2
    //High	    3
    //Urgent	4
    public static final int Low = 1;
    public static final int Medium = 2;
    public static final int High = 3;
    public static final int Urgent = 4;

    public static String toString(int value) {
        switch (value) {
            case Low:
                return "Low";
            case Medium:
                return "Medium";
            case High:
                return "High";
            case Urgent:
                return "Urgent";
        }
        return "Invalid Priority";
    }
}
