package com.sk.bds.ticket.api.data.model.freshdesk;

import com.sk.bds.ticket.api.util.Util;
import lombok.Data;

@Data
public class FreshdeskCspAccountField {
    private boolean valid;
    private String email;
    private String accountId;
    private String fieldString;

    /*public FreshdeskCspAccountField(String email, String accountId) {
        this.email = email;
        this.accountId = accountId;
        if (email != null && accountId != null) {
            this.fieldString = email + "/" + accountId;
            this.valid = Util.isValidEmailAddress(email);
        } else {
            this.fieldString = null;
            this.valid = false;
        }
    }*/

    public FreshdeskCspAccountField(String fieldString) {
        this.fieldString = fieldString;
        parse();
    }

    private void parse() {
        this.email = null;
        this.accountId = null;
        this.valid = false;

        if (fieldString != null && fieldString.contains("/")) {
            String[] temp = fieldString.split("/"); //Email / Account
            if (temp.length == 2) {
                this.email = temp[0];
                this.accountId = temp[1];
                valid = Util.isValidEmailAddress(email);
            }
        }
    }

    public boolean isEmpty() {
        return (fieldString == null || fieldString.trim().length() < 3);
    }

    public static FreshdeskCspAccountField from(String fieldString) {
        return new FreshdeskCspAccountField(fieldString);
    }

    /*public static FreshdeskCspAccountField from(String email, String accountId) {
        return new FreshdeskCspAccountField(email, accountId);
    }*/
}
