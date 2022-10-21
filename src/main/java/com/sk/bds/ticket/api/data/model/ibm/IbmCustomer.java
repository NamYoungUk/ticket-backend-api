package com.sk.bds.ticket.api.data.model.ibm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.http.auth.UsernamePasswordCredentials;

@Data
public class IbmCustomer {
    //This class corresponds SoftLayer_User_Customer
    public static final String keyId = "id";
    public static final String keyEmail = "email";
    public static final String keyUserName = "username";
    public static final String KeyAccountId = "accountId";

    @JsonProperty(keyId)
    private String id;
    @JsonProperty(keyEmail)
    private String email;
    @JsonProperty(keyUserName)
    private String userName;
    @JsonProperty(KeyAccountId)
    private String accountId;

    public IbmCustomer() {
    }

    public IbmCustomer(String id, String email, String userName) {
        this.id = id;
        this.email = email;
        this.userName = userName;
    }

    public IbmCustomer(String id, String email, String userName, String accountId) {
        this.id = id;
        this.email = email;
        this.userName = userName;
        this.accountId = accountId;
    }

    public boolean equalsId(String id) {
        if (id != null) {
            return id.equals(getId());
        }
        return false;
    }

    public boolean equalsEmail(String email) {
        if (email != null) {
            return email.equals(getEmail());
        }
        return false;
    }

/*
[
    {
        "email": "jgyun@didim365.com",
        "id": 190658,
        "username": "SL326820"
    },
    {
        "email": "adaylily@slcloud.co.kr",
        "id": 195570,
        "username": "SL330470"
    }
]
*/

}
