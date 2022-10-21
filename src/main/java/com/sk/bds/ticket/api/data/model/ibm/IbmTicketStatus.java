package com.sk.bds.ticket.api.data.model.ibm;

public enum IbmTicketStatus {
    Open, //1001
    Closed, //1002
    Deleted,
    Assigned; //1004

    public boolean isAssigned() {
        return Assigned.equals(this);
    }

    public boolean isClosed() {
        return Closed.equals(this);
    }

    public boolean isDeleted() {
        return Deleted.equals(this);
    }

    public boolean isOpen() {
        return Open.equals(this);
    }

    public int getIntValue() {
        switch (this) {
            case Open:
                return 1001;
            case Closed:
                return 1002;
            case Assigned:
                return 1004;
        }
        return -1;
    }

    /*
    https://api.softlayer.com/rest/v3.1/SoftLayer_Ticket/133069828/getStatus
    {
        "id": 1001,
        "name": "Open"
    },
    {
        "id": 1002,
        "name": "Closed"
    },
    {
        "id": 1004,
        "name": "Assigned"
    }
    */
}
