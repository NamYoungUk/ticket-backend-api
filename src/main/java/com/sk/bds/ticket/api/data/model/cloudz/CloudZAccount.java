package com.sk.bds.ticket.api.data.model.cloudz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CloudZAccount {
    public static final String KeyAccountId = "accountId";
    public static final String KeyCustomerInfo = "customerInfo";
    public static final String KeyUserInfoList = "userInfoList";

    @JsonProperty(KeyAccountId)
    String accountId;
    @JsonProperty(KeyCustomerInfo)
    CloudZCustomer customer;
    @JsonProperty(KeyUserInfoList)
    List<CloudZUser> users;

    /*@JsonIgnore
    public String customerId() {
        if (customer != null) {
            return customer.getCustomerId();
        }
        return null;
    }*/

    @JsonIgnore
    public String customerManagerEmail() {
        if (customer != null) {
            return customer.getCustomerManagerEmail();
        }
        return null;
    }

    public boolean equalsCustomer(CloudZCustomer other) {
        if (customer != null && other != null) {
            return customer.equals(other);
        }
        return false;
    }

    public boolean equalsCustomerId(String otherCustomerId) {
        if (customer != null && otherCustomerId != null) {
            return customer.equalsCustomerId(otherCustomerId);
        }
        return false;
    }

    public boolean containsUserEmail(String userEmail) {
        if (userEmail != null && users != null) {
            for (CloudZUser user : users) {
                if (userEmail.equals(user.getUserEmail())) {
                    return true;
                }
            }
        }
        return false;
    }

    public CloudZUser getUserByUserId(String userId) {
        if (userId != null && users != null) {
            for (CloudZUser user : users) {
                if (userId.equals(user.getUserId())) {
                    return user;
                }
            }
        }
        return null;
    }
}
