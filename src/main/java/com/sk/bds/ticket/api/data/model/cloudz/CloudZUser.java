package com.sk.bds.ticket.api.data.model.cloudz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CloudZUser {
    public static final String KeyUserId = "userId";
    public static final String KeyUserType = "userType";
    public static final String KeyUserName = "userName";
    public static final String KeyUserEmail = "userEmail";
    public static final String KeyMemberType = "memberType";
    public static final String KeyCspApiInfo = "slApiInfo";

    //In some APIs(getUserCloudApiInfoList), customer information may be included.
    public static final String KeyCustomerId = "customerId";
    //public static final String KeyCustomerName = "customerName";
    //public static final String KeyCustomerType = "customerType";

    enum UserType {
        master,
        user
    }

    enum MemberType {
        Group,
        User,
        General,
        Company,
        Freetrial
    }

    @JsonProperty(KeyUserId)
    String userId;
    @JsonProperty(KeyUserType)
    UserType userType;
    @JsonProperty(KeyMemberType)
    MemberType memberType;
    @JsonProperty(KeyUserName)
    String userName;
    @JsonProperty(KeyUserEmail)
    String userEmail;
    @JsonProperty(KeyCspApiInfo)
    CloudZCspApiInfo cspApiInfo;

    @JsonProperty(KeyCustomerId)
    String customerId;
    //@JsonProperty(KeyCustomerName)
    //String customerName;
    //@JsonProperty(KeyCustomerType)
    //CloudZCustomer.CustomerType customerType;

    @JsonIgnore
    public boolean hasCspApiInfo() {
        return (cspApiInfo != null);
    }

    @JsonIgnore
    public boolean equalsApiId(String apiId) {
        if (cspApiInfo != null && apiId != null) {
            return apiId.equals(cspApiInfo.getApiId());
        }
        return false;
    }

    public boolean equals(CloudZUser other) {
        if (other != null && userId != null) {
            return userId.equals(other.getUserId());
        }
        return false;
    }

    public boolean isMaster() {
        return (userType == UserType.master);
    }

    public boolean isUser() {
        return (userType == UserType.user);
    }

}
