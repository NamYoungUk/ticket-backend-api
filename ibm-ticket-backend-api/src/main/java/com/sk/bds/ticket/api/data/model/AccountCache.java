package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZAccount;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCustomer;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZUser;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class AccountCache {
    private static final String KeyResult = "result";
    private static final String KeyCode = "code";
    private static final String KeyMessage = "message";
    private static final String KeyCloudService = "cloudService";
    private static final String KeyCspAccountInfoList = "cspAccountInfoList";
    private static final String SuccessResultCode = "0000";
    private static final String SuccessResultMessage = "Success";

    Map<String, CloudZAccount> cloudZAccounts;
    Map<String, Map<String, CloudZUser>> cloudZUsers;
    Map<String, Map<String, CloudZUser>> dependentAccessUsers;
    Map<String, CloudZCspApiInfo> cspApiInfoList;
    AppConfig config;

    public AccountCache() {
        cloudZAccounts = new ConcurrentHashMap<>();
        cloudZUsers = new ConcurrentHashMap<>();
        dependentAccessUsers = new ConcurrentHashMap<>();
        cspApiInfoList = new ConcurrentHashMap<>();
        config = AppConfig.getInstance();
    }

    public AccountCache(JSONObject cloudZApiResult) {
        cloudZAccounts = new ConcurrentHashMap<>();
        cloudZUsers = new ConcurrentHashMap<>();
        dependentAccessUsers = new ConcurrentHashMap<>();
        cspApiInfoList = new ConcurrentHashMap<>();
        initialize(cloudZApiResult);
    }

    @JsonIgnore
    public synchronized void initialize(JSONObject cloudZApiResult) {
        cloudZAccounts.clear();
        cloudZUsers.clear();
        dependentAccessUsers.clear();
        cspApiInfoList.clear();
        update(cloudZApiResult);
    }

    @JsonIgnore
    public boolean isAvailable() {
        return (cloudZAccounts.size() > 0);
    }

    @JsonIgnore
    public synchronized void clear() {
        cloudZAccounts.clear();
        cloudZUsers.clear();
        dependentAccessUsers.clear();
        cspApiInfoList.clear();
    }

    @JsonIgnore
    public synchronized void update(JSONObject cloudZApiResult) {
        log.info("Updating...");
        if (cloudZApiResult != null) {
            if (cloudZApiResult.has(KeyResult)) {
                JSONObject result = cloudZApiResult.getJSONObject(KeyResult);
                String resultCode = result.optString(KeyCode);
                String resultMessage = result.optString(KeyMessage);
                if (SuccessResultCode.equalsIgnoreCase(resultCode) && SuccessResultMessage.equalsIgnoreCase(resultMessage)) {
                    if (cloudZApiResult.has(KeyCspAccountInfoList)) {
                        JSONArray cspAccountInfoArray = cloudZApiResult.getJSONArray(KeyCspAccountInfoList);
                        log.info("Updating...");
                        for (int i = 0; i < cspAccountInfoArray.length(); i++) {
                            JSONObject info = cspAccountInfoArray.getJSONObject(i);
                            try {
                                CloudZAccount cloudZAccount = JsonUtil.unmarshal(info.toString(), CloudZAccount.class);
                                addCloudZAccount(cloudZAccount);
                            } catch (IOException e) {
                                log.error("error: {}", e);
                            }
                        }
                    }
                }
            }
        }
    }

    private void addCloudZAccount(CloudZAccount cloudZAccount) {
        if (cloudZAccount != null) {
            cloudZAccounts.put(cloudZAccount.getAccountId(), cloudZAccount);
            updateCloudZUsers(cloudZAccount.getUsers());
        }
    }

    @JsonIgnore
    public void updateCloudZUsers(List<CloudZUser> cloudzUsers) {
        //매 4분마다 이 메소드의 로그가 출력되어 로그 파일이 커지는 문제. 1주기마다 512KBytes 출력됨(운영기준).
        //log.debug("cloudzUsers: {}", cloudzUsers);
        if (cloudzUsers != null) {
            for (CloudZUser cloudzUser : cloudzUsers) {
                String userEmail = cloudzUser.getUserEmail();
                if (userEmail != null) { //Some items may not have an email.
                    CloudZCspApiInfo apiInfo = cloudzUser.getCspApiInfo();
                    if (apiInfo != null && apiInfo.isAvailable()) {
                        Map<String, CloudZUser> userInfoMap = cloudZUsers.get(userEmail);
                        if (userInfoMap == null) {
                            userInfoMap = new ConcurrentHashMap<>();
                            cloudZUsers.put(userEmail, userInfoMap);
                        }
                        userInfoMap.put(apiInfo.getApiId(), cloudzUser);
                        cspApiInfoList.put(apiInfo.getApiId(), apiInfo);
                        //log.debug("{} user's {} account updated.", userEmail, apiInfo.getApiId());
                    }
                } else {
                    log.warn("There is no email in the user information. {}", cloudzUser.getUserId());
                }
            }
        }
    }

    @JsonIgnore
    public void addDependentAccess(String email, List<CloudZUser> cloudzUsers) {
        log.info("email: {}", email);
        if (email != null && cloudzUsers != null) {
            Map<String, CloudZUser> userInfoMap = new ConcurrentHashMap<>();
            for (CloudZUser cloudzUser : cloudzUsers) {
                CloudZCspApiInfo apiInfo = cloudzUser.getCspApiInfo();
                if (apiInfo != null && apiInfo.isAvailable()) {
                    userInfoMap.put(apiInfo.getApiId(), cloudzUser);
                    log.debug("{} user's dependent account {} added.", email, apiInfo.getApiId());
                }
            }
            log.info("Dependent access information is updated. email: {}", email);
            dependentAccessUsers.put(email, userInfoMap);
        }
    }

    @JsonIgnore
    public boolean hasDependentAccess(String email) {
        if (email != null) {
            return dependentAccessUsers.containsKey(email);
        }
        return false;
    }

    @JsonIgnore
    public synchronized void clearDependentAccess() {
        log.info("Clear dependent access information.");
        dependentAccessUsers.clear();
    }

    @JsonIgnore
    public CloudZCspApiInfo getDependentAccess(String email, String apiId) {
        if (hasDependentAccess(email)) {
            Map<String, CloudZUser> userInfoMap = dependentAccessUsers.get(email);
            if (userInfoMap != null) {
                CloudZUser userInfo = userInfoMap.get(apiId);
                if (userInfo != null && userInfo.hasCspApiInfo()) {
                    log.info("Found account in dependent users. {}/{}", email, apiId);
                    return userInfo.getCspApiInfo();
                } else {
                    log.info("Not found account in dependent users. {}/{}", email, apiId);
                }
            } else {
                log.info("Not found account map in dependent users. {}", email);
            }
        } else {
            log.info("No exists account map in dependent users. {}", email);
        }
        return null;
    }

    @JsonIgnore
    public CloudZAccount getAccountByAccountId(String accountId) {
        if (accountId != null) {
            String key = TicketUtil.detachIbmAccountPrefix(accountId);
            return cloudZAccounts.get(key);
        }
        return null;
    }

    @JsonIgnore
    public CloudZAccount getAccountByCustomer(CloudZCustomer customer) {
        if (customer != null) {
            for (CloudZAccount account : cloudZAccounts.values()) {
                if (account.equalsCustomer(customer)) {
                    return account;
                }
            }
        }
        return null;
    }

    @JsonIgnore
    public CloudZAccount getAccountByCustomerId(String customerId) {
        if (customerId != null) {
            for (CloudZAccount account : cloudZAccounts.values()) {
                if (account.equalsCustomerId(customerId)) {
                    return account;
                }
            }
        }
        return null;
    }

    @JsonIgnore
    public CloudZAccount getAccountByUser(CloudZUser user) {
        if (user != null) {
            String email = user.getUserEmail();
            if (email != null) {
                for (CloudZAccount account : cloudZAccounts.values()) {
                    if (account.containsUserEmail(email)) {
                        return account;
                    }
                }
            }
        }
        return null;
    }

    @JsonIgnore
    public CloudZCustomer getCustomerByUser(CloudZUser user) {
        if (user != null) {
            String email = user.getUserEmail();
            if (email != null) {
                for (CloudZAccount account : cloudZAccounts.values()) {
                    if (account.containsUserEmail(email)) {
                        return account.getCustomer();
                    }
                }
            }
        }
        return null;
    }

    @JsonIgnore
    public CloudZCustomer getCustomerByAccountId(String accountId) {
        if (accountId != null) {
            String key = TicketUtil.detachIbmAccountPrefix(accountId);
            CloudZAccount account = cloudZAccounts.get(key);
            if (account != null) {
                return account.getCustomer();
            }
        }
        return null;
    }

    @JsonIgnore
    public CloudZUser getMasterUserByCustomerId(String customerId) {
        if (customerId != null) {
            CloudZAccount account = getAccountByCustomerId(customerId);
            //CloudZCustomer customer = getCustomer(user);
            if (account != null) {
                CloudZCustomer customer = account.getCustomer();
                if (customer != null) {
                    String masterUserId = customer.getMasterUserId();
                    return account.getUserByUserId(masterUserId);
                }
            }
        }
        log.info("Not Found master for customerId: {}", customerId);
        return null;
    }

    @JsonIgnore
    public CloudZUser getMasterUserByAccountId(String accountId) {
        if (accountId != null) {
            String key = TicketUtil.detachIbmAccountPrefix(accountId);
            CloudZAccount account = getAccountByAccountId(key);
            //CloudZCustomer customer = getCustomer(user);
            if (account != null) {
                CloudZCustomer customer = account.getCustomer();
                if (customer != null) {
                    String masterUserId = customer.getMasterUserId();
                    return account.getUserByUserId(masterUserId);
                }
            }
        }
        return null;
    }

    @JsonIgnore
    public boolean existsCspApiInfo(String email, String apiId) {
        if (email != null && apiId != null) {
            Map<String, CloudZUser> userInfoMap = cloudZUsers.get(email);
            if (userInfoMap != null) {
                CloudZUser userInfo = userInfoMap.get(apiId);
                if (userInfo != null) {
                    return userInfo.hasCspApiInfo();
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public CloudZCspApiInfo getCspApiInfoByAccountId(String accountId) {
        if (accountId != null) {
            return cspApiInfoList.get(accountId);
        }
        return null;
    }

    @JsonIgnore
    public boolean hasUser(String email) {
        if (email != null) {
            return cloudZUsers.containsKey(email);
        }
        return false;
    }

    @JsonIgnore
    public CloudZCspApiInfo getCspApiInfo(String email, String apiId) {
        if (email != null && apiId != null) {
            Map<String, CloudZUser> userInfoMap = cloudZUsers.get(email);
            if (userInfoMap != null) {
                CloudZUser userInfo = userInfoMap.get(apiId);
                if (userInfo != null && userInfo.hasCspApiInfo()) {
                    log.debug("Found account into user accounts. {}-{}", email, apiId);
                    return userInfo.getCspApiInfo();
                } else {
                    log.debug("Not found apiId in {} api info list. Searching accountId in master accounts.", email);
                    //search api id from master account.
                    if (config.isCloudzUserUseMasterAccount()) {
                        log.debug("Searching apiId in master accounts for {}.", email);
                        for (CloudZUser user : userInfoMap.values()) {
                            if (user.isUser()) {
                                log.info("searching master by CustomerId: {}", user.getCustomerId());
                                CloudZUser master = getMasterUserByCustomerId(user.getCustomerId());
                                if (master != null && master.equalsApiId(apiId)) {
                                    log.info("Found account into master's accounts. {}-{} by CustomerId: {}", master.getUserEmail(), apiId, user.getCustomerId());
                                    return master.getCspApiInfo();
                                }
                            }
                        }
                    }
                }
            } else {
                //email에 해당하는 사용자 정보가 없는 경우.
                log.error("Not found userInfo map for {}.", email);
                log.info("Researching api info in dependent access users.");
                return getDependentAccess(email, apiId);
            }
        }
        return null;
    }

    @JsonIgnore
    public List<CloudZUser> getCloudZUserApiInfoList(String email) {
        if (email != null) {
            Map<String, CloudZUser> userInfoMap = cloudZUsers.get(email);
            if (userInfoMap != null) {
                List<CloudZUser> userApiInfoList = new ArrayList<>();
                userApiInfoList.addAll(userInfoMap.values());
                return userApiInfoList;
            }
        }
        return null;
    }

    @JsonIgnore
    public JSONObject export() {
        try {
            String jsonText = JsonUtil.marshal(cloudZUsers);
            return new JSONObject(jsonText);
        } catch (JsonProcessingException e) {
            log.error("error:{}", e);
        }
        return new JSONObject();
    }

    @JsonIgnore
    public JSONObject exportCachedApiIdInformation() {
        try {
            JSONObject result = new JSONObject();
            JSONObject cspAccount = new JSONObject();
            for (String email : cloudZUsers.keySet()) {
                Map<String, CloudZUser> userMap = cloudZUsers.get(email);
                if (userMap != null) {
                    JSONArray apiIdList = new JSONArray(userMap.keySet());
                    cspAccount.put(email, apiIdList);
                }
            }
            result.put("Csp Accounts", cspAccount);

            JSONObject dependentAccess = new JSONObject();
            for (String email : dependentAccessUsers.keySet()) {
                Map<String, CloudZUser> userMap = dependentAccessUsers.get(email);
                if (userMap != null) {
                    JSONArray apiIdList = new JSONArray(userMap.keySet());
                    dependentAccess.put(email, apiIdList);
                }
            }
            result.put("Dependent Access Accounts", dependentAccess);
            return result;
        } catch (JSONException e) {
            log.error("error:{}", e);
        }
        return new JSONObject();
    }

    @JsonIgnore
    public void printAllAccounts() {
        int total = 0;
        for (String email : cloudZUsers.keySet()) {
            log.debug("{} - {}", email, cloudZUsers.get(email));
            total += cloudZUsers.get(email).size();
        }
        log.debug("total emails:{}, total IBM accounts : {}", cloudZUsers.size(), total);
        log.debug("Account cache Id Map: {}", exportCachedApiIdInformation());
        log.debug("Account cache: {}", export());
    }
}
