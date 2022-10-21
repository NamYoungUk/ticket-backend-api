package com.sk.bds.ticket.api.data.model.cloudz;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CloudZCustomer {
    enum CustomerType {
        company,
        company_group,
        freetrial,
        individual,
        referral_company_group,
        referral_individual,
        reseller_company_group
    }

    public static final String KeyCustomerInfo = "customerInfo";
    public static final String KeyCustomerId = "customerId";
    public static final String KeyCustomerType = "customerType";
    public static final String KeyCustomerName = "customerName";
    public static final String KeyCustomerManagerName = "customerManagerName";
    public static final String KeyCustomerManagerEmail = "customerManagerEmail";
    public static final String KeyParentCustomerId = "parentCustomerId";
    public static final String KeyMasterUserId = "masterUserId";
    public static final String KeyCreateDate = "createDate";
    public static final String KeyModifyDate = "modifyDate";

    @JsonProperty(KeyCustomerId)
    String customerId;
    @JsonProperty(KeyCustomerType)
    CustomerType customerType;
    @JsonProperty(KeyCustomerName)
    String customerName;
    @JsonProperty(KeyCustomerManagerName)
    String customerManagerName;
    @JsonProperty(KeyCustomerManagerEmail)
    String customerManagerEmail;
    @JsonProperty(KeyParentCustomerId)
    String parentCustomerId;
    @JsonProperty(KeyMasterUserId)
    String masterUserId;
    //@JsonSerialize(using = LocalDateSerializer.class)
    //@JsonDeserialize(using = LocalDateDeserializer.class)
    //@JsonProperty(KeyCreateDate)
    //Date createDate;
    //@JsonSerialize(using = LocalDateSerializer.class)
    //@JsonDeserialize(using = LocalDateDeserializer.class)
    //@JsonProperty(KeyModifyDate)
    //Date modifyDate;
    //long instanceCreatedTime;

    public boolean equals(CloudZCustomer other) {
        if (other != null && customerId != null) {
            return customerId.equals(other.getCustomerId());
        }
        return false;
    }

    public boolean equalsCustomerId(String otherCustomerId) {
        if (otherCustomerId != null && customerId != null) {
            return customerId.equals(otherCustomerId);
        }
        return false;
    }
}
