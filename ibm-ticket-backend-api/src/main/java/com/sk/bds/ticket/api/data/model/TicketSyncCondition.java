package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.exception.AppInternalError;

public enum TicketSyncCondition {
    emptyTicketData,
    invalidCsp,
    notBetaTester,
    outOfSyncTimeRange,
    invalidCspAccount,
    notAvailableCspAccount,
    emptyCspTicketId,
    syncable;

    public boolean isSyncable() {
        return (this == syncable);
    }

    public String getErrorMessage() {
        switch (this) {
            case emptyTicketData:
                return "Empty ticket data.";
            case invalidCsp:
                return "Invalid csp field. csp field is empty or not " + AppConstants.CSP_NAME;
            case notBetaTester:
                return "Not beta tester's ticket.";
            case outOfSyncTimeRange:
                return "Out of sync time range.";
            case invalidCspAccount:
                return "Invalid csp account.";
            case notAvailableCspAccount:
                return "Not available csp account.";
            case emptyCspTicketId:
                return "Empty csp case id";
        }
        return "";
    }

    public AppInternalError buildInternalError() {
        switch (this) {
            case emptyTicketData:
                return AppInternalError.emptyTicketData(getErrorMessage());
            case invalidCsp:
                return AppInternalError.invalidCsp(getErrorMessage());
            case notBetaTester:
                return AppInternalError.notBetaTester(getErrorMessage());
            case outOfSyncTimeRange:
                return AppInternalError.outOfSyncTargetTimeRange(getErrorMessage());
            case invalidCspAccount:
                return AppInternalError.invalidCspAccount(getErrorMessage());
            case notAvailableCspAccount:
                return AppInternalError.invalidCspAccount(getErrorMessage());
            case emptyCspTicketId:
                return AppInternalError.emptyCspTicketId(getErrorMessage());
        }
        return null;

    }
}
