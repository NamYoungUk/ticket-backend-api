package com.sk.bds.ticket.api.data.model.cloudz;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.softlayer.api.ApiClient;
import com.softlayer.api.RestApiClient;
import lombok.Data;
import org.apache.http.auth.UsernamePasswordCredentials;

@Data
public class CloudZCspApiInfo {
    public static final String KeyClassObject = "slApiInfo";
    public static final String KeyApiId = "apiId";
    public static final String KeyApiKey = "apiKey";
    @JsonProperty(KeyApiId)
    String apiId;
    @JsonProperty(KeyApiKey)
    String apiKey;

    public CloudZCspApiInfo() {
    }

    public CloudZCspApiInfo(String apiId, String apiKey) {
        this.apiId = apiId;
        this.apiKey = apiKey;
    }

    public boolean isAvailable() {
        return (apiId != null && apiKey != null);
    }

    public UsernamePasswordCredentials credentials() {
        return new UsernamePasswordCredentials(apiId, apiKey);
    }

    public ApiClient buildApiClient() {
        if(isAvailable()) {
            return new RestApiClient().withCredentials(apiId, apiKey);
        }
        return null;
    }

    public String coveredKey() {
        final int SHOW_SIZE = 6;
        if (apiKey != null) {
            if (apiKey.length() > SHOW_SIZE) {
                return "..." + apiKey.substring(apiKey.length() - SHOW_SIZE);
            }
        }
        return apiKey;
    }

    @Override
    public String toString() {
        return "CloudZCspApiInfo{" +
                "apiId='" + apiId + '\'' +
                ", apiKey='" + coveredKey() + '\'' +
                '}';
    }
}
