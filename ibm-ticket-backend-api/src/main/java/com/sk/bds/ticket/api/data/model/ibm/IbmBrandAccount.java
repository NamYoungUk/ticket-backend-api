package com.sk.bds.ticket.api.data.model.ibm;

import com.softlayer.api.ApiClient;
import com.softlayer.api.RestApiClient;
import lombok.Data;
import org.apache.http.auth.UsernamePasswordCredentials;

@Data
public class IbmBrandAccount {
    private String brandId;
    private String accountId;
    private String apiKey;
    private String email;

    public IbmBrandAccount() {
    }

    public IbmBrandAccount(String brandId, String accountId, String apiKey, String email) {
        this.brandId = brandId;
        this.accountId = accountId;
        this.apiKey = apiKey;
        this.email = email;
    }

    public UsernamePasswordCredentials credentials() {
        return new UsernamePasswordCredentials(accountId, apiKey);
    }

    public ApiClient buildApiClient() {
        return new RestApiClient().withCredentials(accountId, apiKey);
    }
}
