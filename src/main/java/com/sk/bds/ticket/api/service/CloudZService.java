package com.sk.bds.ticket.api.service;

import com.sk.bds.ticket.api.data.model.AccountCache;
import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZUser;
import com.sk.bds.ticket.api.data.model.ibm.IbmBrandAccount;
import com.sk.bds.ticket.api.data.model.ibm.IbmCustomer;
import com.sk.bds.ticket.api.data.model.ibm.IbmCustomerCache;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.RestApiUtil;
import com.sk.bds.ticket.api.util.TicketUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CloudZService {
    public static class SupplyCode {
        public static final String SoftLayer = "01";
        public static final String Aws = "04";
        public static final String Azure = "05";
    }

    public static final String KeyCloudApiInfoList = "userCloudApiInfoList";
    public static final String KeyUserInfoList = "userInfoList";
    public static final String KeyUserInfo = "userInfo";
    public static final String KeyUserType = "userType";
    public static final String KeyUserId = "userId";
    public static final String KeyUserName = "userName";
    public static final String KeyUserEmail = "userEmail";
    public static final String KeyCustomerId = "customerId";
    public static final String KeyCustomerName = "customerName";
    public static final String KeyCustomerType = "customerType";
    public static final String KeySlApiInfo = "slApiInfo";
    public static final String KeyAwsApiInfo = "awsApiInfo";
    public static final String KeyAzureApiInfo = "azureApiInfo";
    public static final String KeyAccountId = "accountId";
    public static final String KeyAppId = "appId";
    public static final String KeyAppSecret = "appSecret";
    public static final String KeyCloudAccountDomain = "cloudAccountDomain";
    public static final String KeyParentAccountId = "parentAccountId";
    public static final String KeyAccessKeyId = "accessKeyId";
    public static final String KeySecretKey = "secretKey";
    public static final String KeyIsEDPAccount = "isEDPAccount";
    public static final String KeyApiKey = "apiKey";
    public static final String KeyApiId = "apiId";
    public static final String UserTypeMaster = "master";
    public static final String UserTypeUser = "user";

    private static AccountCache accountCache = null;
    private static final Object accountCacheLocker = new Object();
    private static long accountCacheUpdateTime;
    private static final long AccountCacheUpdateInterval = 180000; //3 minutes
    private static IbmCustomerCache ibmCustomerCache;
    private static final Object ibmCustomerCacheLocker = new Object();
    private static long ibmCustomerCacheUpdateTime;
    private static final long IbmCustomerCacheUpdateInterval = 180000; //3 minutes
    private static final String CspSupplyCode = SupplyCode.SoftLayer;

    private static final AppConfig config = AppConfig.getInstance();

    static {
        updateCspAccountCache();
        updateIbmCustomerCache();
    }

    public static boolean isAvailableAccountCache() {
        return (accountCache != null && accountCache.isAvailable());
    }

    public static void checkAndUpdateAccountCache() {
        long now = System.currentTimeMillis();
        if (accountCacheUpdateTime + AccountCacheUpdateInterval < now) {
            updateCspAccountCache();
        } else {
            log.info("AccountCache is up to date.");
        }
    }

    public static void updateCspAccountCache() {
        synchronized (accountCacheLocker) {
            long now = System.currentTimeMillis();
            if (accountCacheUpdateTime + AccountCacheUpdateInterval < now) {
                log.info("account cache refreshing.");
                if (accountCache == null) {
                    accountCache = new AccountCache();
                }
                try {
                    JSONObject result = getAllAccounts(CspSupplyCode);
                    accountCache.update(result);
                    accountCache.clearDependentAccess();
                    accountCacheUpdateTime = now;
                    log.info("account cache updated.");
                } catch (IOException | URISyntaxException e) {
                    log.error("failed to update account cache. error : {}", e);
                }
            } else {
                log.info("The account cache was updated {} seconds ago. aborted. updateTime:{}, updateInterval: {}, now: {}", ((now - accountCacheUpdateTime) / 1000), accountCacheUpdateTime, AccountCacheUpdateInterval, now);
            }
            //accountCache.printAllAccounts();
        }
    }

    public static String printAccountCache() {
        return accountCache.exportCachedApiIdInformation().toString();
    }

    public static String printCustomerCache() {
        return ibmCustomerCache.export().toString();
    }

    /*public void clearCspAccountCache() {
        accountCache.clear();
        accountCache = null;
    }*/

    private static boolean isBetaTestEnabled() {
        return config.isBetaTestEnabled();
    }

    private static boolean isBetaTester(String email) {
        return config.isBetaTester(email);
    }

    private static String getCloudzApiEndpoint() {
        return config.getCloudzApiEndpoint();
    }

    private static UsernamePasswordCredentials getCredentials() {
        return new UsernamePasswordCredentials(config.getCloudzClientId(), config.getCloudzClientSecret());
    }

    private static RestApiUtil.RestApiResult request(String api, HttpMethod method, HttpEntity body) throws IOException, URISyntaxException {
        log.debug("CloudZ Portal api call:{}, Method:{}", api, method);
        URL targetUrl = new URL(getCloudzApiEndpoint() + api); // URL object from API endpoint:
        return RestApiUtil.request(targetUrl.toString(), method, null, body, getCredentials());
    }

    private static boolean equalsSlApiInfo(JSONObject u1, JSONObject u2) {
        if (u1 != null && u2 != null) {
            if (u1.has(KeyApiId) && u1.has(KeyApiKey) && u2.has(KeyApiId) && u2.has(KeyApiKey)) {
                return u1.optString(KeyApiId).equals(u2.optString(KeyApiId)) && u1.optString(KeyApiKey).equals(u2.optString(KeyApiKey));
            }
        }
        return false;
    }

    private static boolean equalsAwsApiInfo(JSONObject u1, JSONObject u2) {
        if (u1 != null && u2 != null) {
            if (u1.has(KeyAccountId) && u1.has(KeyAccessKeyId) && u2.has(KeyAccountId) && u2.has(KeyAccessKeyId)) {
                return u1.optString(KeyAccountId).equals(u2.optString(KeyAccountId)) && u1.optString(KeyAccessKeyId).equals(u2.optString(KeyAccessKeyId));
            }
        }
        return false;
    }

    private static boolean equalsAzureApiInfo(JSONObject u1, JSONObject u2) {
        if (u1 != null && u2 != null) {
            if (u1.has(KeyAccountId) && u1.has(KeyCloudAccountDomain) && u2.has(KeyAccountId) && u2.has(KeyCloudAccountDomain)) {
                return u1.optString(KeyAccountId).equals(u2.optString(KeyAccountId)) && u1.optString(KeyCloudAccountDomain).equals(u2.optString(KeyCloudAccountDomain));
            }
        }
        return false;
    }

    public static CloudZUser buildCloudZUser(JSONObject cloudUserApiInfo) {
        try {
            CloudZUser cloudzUser = JsonUtil.unmarshal(cloudUserApiInfo.toString(), CloudZUser.class);
            return cloudzUser;
        } catch (IOException e) {
            log.error("deserializing failed. cloudApiInfo:{} - {}", cloudUserApiInfo, e);
            e.printStackTrace();
        }
        return null;
    }

    private static void addFilteredAccount(JSONArray userArray, JSONObject user) {
        log.debug("user:{}", user);
        if (userArray != null && user != null) {
            for (int i = 0; i < userArray.length(); i++) {
                JSONObject tmp = userArray.getJSONObject(i);
                if (user.has(KeySlApiInfo)) {
                    if (tmp.has(KeySlApiInfo) && equalsSlApiInfo(user.optJSONObject(KeySlApiInfo), tmp.optJSONObject(KeySlApiInfo))) {
                        log.debug("duplicated account. KeySlApiInfo removed. duplicated tmp:{}", tmp);
                        user.remove(KeySlApiInfo);
                    }
                }
                if (user.has(KeyAwsApiInfo)) {
                    if (tmp.has(KeyAwsApiInfo) && equalsAwsApiInfo(user.optJSONObject(KeyAwsApiInfo), tmp.optJSONObject(KeyAwsApiInfo))) {
                        log.debug("duplicated account. KeyAwsApiInfo removed. duplicated tmp:{}", tmp);
                        user.remove(KeyAwsApiInfo);
                    }
                }
                if (user.has(KeyAzureApiInfo)) {
                    if (tmp.has(KeyAzureApiInfo) && equalsAzureApiInfo(user.optJSONObject(KeyAzureApiInfo), tmp.optJSONObject(KeyAzureApiInfo))) {
                        log.debug("duplicated account. KeyAzureApiInfo removed. duplicated tmp:{}", tmp);
                        user.remove(KeyAzureApiInfo);
                    }
                }
            }
            if (user.has(KeySlApiInfo) || user.has(KeyAwsApiInfo) || user.has(KeyAzureApiInfo)) {
                log.debug("filtered account added. user:{}", user);
                userArray.put(user);
            }
        }
    }

    public static JSONObject getMasterUserInfo(String customerId) throws IOException, URISyntaxException {
        RestApiUtil.RestApiResult result = request(String.format("/account/getMasterUserInfo?customerId=%s", customerId), HttpMethod.GET, null);
        return new JSONObject(result.getResponseBody());
    }

    public static JSONArray getMasterUserApiInfoListByEmail(String email) throws IOException, URISyntaxException {
        JSONArray userCloudApiInfoList = getUserApiInfoListByEmail(email);
        return getMasterUserApiInfoList(userCloudApiInfoList);
    }

    public static JSONObject getMasterUserApiInfo(JSONObject masterUserInfo) {
        try {
            if (masterUserInfo != null && masterUserInfo.has(KeyUserInfo)) {
                JSONObject userInfo = masterUserInfo.getJSONObject(KeyUserInfo);
                if (userInfo.has(KeyUserEmail) && userInfo.has(KeyUserId)) {
                    String masterUserEmail = userInfo.optString(KeyUserEmail);
                    String masterUserId = userInfo.optString(KeyUserId);
                    try {
                        JSONArray masterApiInfoList = getUserApiInfoListByEmail(masterUserEmail);
                        for (int a = 0; a < masterApiInfoList.length(); a++) {
                            JSONObject masterApiInfo = masterApiInfoList.getJSONObject(a);
                            String subUserId = masterApiInfo.optString(KeyUserId);
                            if (masterUserId.equals(subUserId)) {
                                if (masterApiInfo.has(KeySlApiInfo) || masterApiInfo.has(KeyAwsApiInfo) || masterApiInfo.has(KeyAzureApiInfo)) {
                                    return masterApiInfo;
                                }
                            }
                        }
                    } catch (IOException | URISyntaxException | JSONException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        } catch (JSONException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static JSONArray getMasterUserApiInfoList(JSONArray userCloudApiInfoList) {
        JSONArray apiAccounts = new JSONArray();
        for (int i = 0; i < userCloudApiInfoList.length(); i++) {
            JSONObject user = userCloudApiInfoList.getJSONObject(i);
            String userType = user.optString(KeyUserType);
            if (UserTypeUser.equals(userType)) {
                if (user.has(KeyCustomerId)) {
                    try {
                        String customerId = user.optString(KeyCustomerId);
                        JSONObject masterUserInfo = getMasterUserInfo(customerId);
                        JSONObject masterUserApiInfo = getMasterUserApiInfo(masterUserInfo);
                        if (masterUserApiInfo != null) {
                            addFilteredAccount(apiAccounts, masterUserApiInfo);
                        }
                    } catch (IOException | URISyntaxException | JSONException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        }
        return apiAccounts;
    }

    public static JSONArray getMasterUserApiInfoList(List<CloudZUser> cloudzUsers) {
        JSONArray apiAccounts = new JSONArray();
        if (cloudzUsers != null) {
            for (CloudZUser user : cloudzUsers) {
                if (user.isUser()) {
                    if (user.getCustomerId() != null) {
                        try {
                            String customerId = user.getCustomerId();
                            JSONObject masterUserInfo = getMasterUserInfo(customerId);
                            JSONObject masterUserApiInfo = getMasterUserApiInfo(masterUserInfo);
                            if (masterUserApiInfo != null) {
                                addFilteredAccount(apiAccounts, masterUserApiInfo);
                            }
                        } catch (IOException | URISyntaxException | JSONException e) {
                            log.error(e.getMessage());
                        }
                    }
                }
            }
        }
        return apiAccounts;
    }

    public static JSONObject getUserCloudApiInfo(String email) throws IOException, URISyntaxException {
        if (email != null) {
            RestApiUtil.RestApiResult result = request(String.format("/account/getUserCloudApiInfoList?userEmail=%s", email), HttpMethod.GET, null);
            return new JSONObject(result.getResponseBody());
        }
        return new JSONObject();
    }

    public static JSONArray getUserApiInfoListByEmail(String email) throws IOException, URISyntaxException {
        JSONObject result = getUserCloudApiInfo(email);
        if (result != null && result.has(KeyCloudApiInfoList)) {
            return result.getJSONArray(KeyCloudApiInfoList);
        }
        return new JSONArray();
    }

    public static JSONArray getAcceptableUserApiInfoListByEmail(String email) throws IOException, URISyntaxException {
        JSONArray apiAccounts = new JSONArray();
        JSONArray userCloudApiInfoList = getUserApiInfoListByEmail(email);
        for (int i = 0; i < userCloudApiInfoList.length(); i++) {
            JSONObject user = userCloudApiInfoList.getJSONObject(i);
            if (user.has(KeySlApiInfo) || user.has(KeyAwsApiInfo) || user.has(KeyAzureApiInfo)) {
                addFilteredAccount(apiAccounts, user);
            }
        }

        if (config.isCloudzUserUseMasterAccount()) {
            //Add master's account
            JSONArray masterUserInfoList = getMasterUserApiInfoList(userCloudApiInfoList);
            if (masterUserInfoList != null && masterUserInfoList.length() > 0) {
                for (int i = 0; i < masterUserInfoList.length(); i++) {
                    JSONObject master = masterUserInfoList.getJSONObject(i);
                    addFilteredAccount(apiAccounts, master);
                }
            }
        }
        return apiAccounts;
    }

    public static List<CloudZUser> getCloudZUserListByEmail(String email, boolean includesMasterAccount) {
        List<CloudZUser> cloudzUsers = new ArrayList<>();
        if (email != null) {
            try {
                JSONArray userCloudApiInfoList;
                if (includesMasterAccount) {
                    userCloudApiInfoList = getAcceptableUserApiInfoListByEmail(email);
                } else {
                    userCloudApiInfoList = getUserApiInfoListByEmail(email);
                }

                if (userCloudApiInfoList != null && userCloudApiInfoList.length() > 0) {
                    for (int i = 0; i < userCloudApiInfoList.length(); i++) {
                        JSONObject cloudApiInfo = userCloudApiInfoList.getJSONObject(i);
                        CloudZUser cloudzUser = buildCloudZUser(cloudApiInfo);
                        if (cloudzUser != null) {
                            cloudzUsers.add(cloudzUser);
                        }
                    }
                }
            } catch (IOException | URISyntaxException e) {
                log.error("Failed to search account of {}. {}", email, e.getMessage());
            }
        }
        return cloudzUsers;
    }

    public static JSONObject getAccountUsers(String supplyCode, String accountId) throws IOException, URISyntaxException {
        String requestApi = String.format("/account/csp/getAccountUserList?accountId=%s&cloudSupplyCode=%s", accountId, supplyCode);
        RestApiUtil.RestApiResult result = request(requestApi, HttpMethod.GET, null);
        return new JSONObject(result.getResponseBody());
    }

    public static JSONObject getAllAccounts(String supplyCode) throws IOException, URISyntaxException {
        String requestApi = String.format("/account/csp/getAllAccountInfoList?cloudSupplyCode=%s", supplyCode);
        RestApiUtil.RestApiResult result = request(requestApi, HttpMethod.GET, null);
        return new JSONObject(result.getResponseBody());
    }

    public static CloudZCspApiInfo getCspApiInfo(String email, String apiId) {
        log.info("email:{}, accountId:{}", email, apiId);
        if (email == null && apiId == null) {
            return null;
        }
        if (isAvailableAccountCache()) {
            log.info("Account cache is ready.");
            checkAndUpdateAccountCache(); //주기적 동기화 비활성화에 따른 갱신.

            //email 사용자의 활성화된 CSP 계정이 없는 경우, CloudZ MCMP에 getAllAccountInfoList에서는 계정 자체가 조회되지 않으므로 개별 getUserCloudApiInfoList로 조회한 정보를 사용해야함.
            if (!accountCache.hasUser(email)) {
                log.info("Not exists user {} in userList of account cache.", email);
                if (!accountCache.hasDependentAccess(email)) {
                    log.info("Not exists user {} in dependent access list of account cache.", email);
                    List<CloudZUser> cloudzUsers = getCloudZUserListByEmail(email, true);
                    if (cloudzUsers == null) {
                        log.info("No Account information for {}.", email);
                        return null;
                    }
                    log.info("Found Account information for {}. Adding dependent access list.", email);
                    accountCache.addDependentAccess(email, cloudzUsers);
                }
                return accountCache.getDependentAccess(email, apiId);
            }
            CloudZCspApiInfo apiInfo = accountCache.getCspApiInfo(email, apiId);
            if (apiInfo == null) {
                log.error("[AccountCache] Not found accessible api info for {}-{}", email, apiId);
                /*if (config.isCloudzUserUseMasterAccount()) {
                    List<CloudZUser> userApiInfoList = accountCache.getCloudZUserApiInfoList(email);
                    log.error("[AccountCache] search accessible api info into master's accounts. {}-{}", email, apiId);
                    if (userApiInfoList != null) {
                        for (CloudZUser user : userApiInfoList) {
                            CloudZUser masterUser = accountCache.getMasterUser(user);
                            if (masterUser != null && masterUser.equalsApiId(apiId)) {
                                log.info("Found master {} api info for account {}", masterUser.getUserEmail(), apiId);
                                return masterUser.getCspApiInfo();
                            }
                        }
                    }
                }*/
            }
            return apiInfo;
        } else {
            //티켓 작성자 email이 사용할 수 있는 모든 account(master의 account 포함)를 조회 후 일치하는 account id의 정보로 티켓 작성하도록.
            //	- 본인 계정을 먼저 조회
            //	- 본인 계정에서 찾지 못한 경우 본인의 master 계정 목록 생성 후 master의 계정 조회.
            List<CloudZUser> cloudzUsers = getCloudZUserListByEmail(email, false);
            if (cloudzUsers != null) {
                for (CloudZUser user : cloudzUsers) {
                    if (user.equalsApiId(apiId)) {
                        log.info("Found account into user accounts. {}-{}", email, apiId);
                        return user.getCspApiInfo();
                    }
                }

                if (config.isCloudzUserUseMasterAccount()) {
                    //check master account
                    JSONArray masterCloudApiInfoList = getMasterUserApiInfoList(cloudzUsers);
                    for (int i = 0; i < masterCloudApiInfoList.length(); i++) {
                        JSONObject masterApiInfo = masterCloudApiInfoList.getJSONObject(i);
                        CloudZUser masterUser = buildCloudZUser(masterApiInfo);
                        if (masterUser != null) {
                            if (masterUser.equalsApiId(apiId)) {
                                log.info("Found master {} api info for account {}", masterUser.getUserEmail(), apiId);
                                return masterUser.getCspApiInfo();
                            }
                        }
                    }
                }
            }
        }
        log.error("Not found accessible api info for {}-{}", email, apiId);
        return null;
    }

    public static CloudZCspApiInfo getCspApiInfoByAccountId(String accountId) {
        log.info("accountId:{}", accountId);
        if (accountId == null) {
            return null;
        }
        if (isAvailableAccountCache()) {
            //checkAndUpdateAccountCache(); //동기화 스케쥴에 의한 주기적인 업데이트만 허용.
            CloudZCspApiInfo awsApiInfo = accountCache.getCspApiInfoByAccountId(accountId);
            if (awsApiInfo == null) {
                log.error("[AccountCache] Not found accessible api info for {}", accountId);
            }
            return awsApiInfo;
        } else {
            try {
                //Search account users.
                JSONObject usersResult = getAccountUsers(CspSupplyCode, accountId);
                if (usersResult.has(KeyUserInfoList)) {
                    JSONArray userInfoList = usersResult.optJSONArray(KeyUserInfoList);
                    //Search master user of account.
                    for (int i = 0; i < userInfoList.length(); i++) {
                        JSONObject userInfo = userInfoList.getJSONObject(i);
                        try {
                            CloudZUser user = JsonUtil.unmarshal(userInfo.toString(), CloudZUser.class);
                            if (user.isMaster()) {
                                //Search ApiInfo by master's email and accountId.
                                return getCspApiInfo(user.getUserEmail(), accountId);
                            }
                        } catch (IOException e) {
                            log.error("deserializing failed. {}", e);
                        }
                    }
                }
            } catch (IOException | URISyntaxException | JSONException e) {
                log.error("Failed to search account of {}. {}", accountId, e);
            }

        }
        return null;
    }

    public static CloudZUser getCloudZMasterUserByAccountId(String accountId) {
        if (accountId == null) {
            return null;
        }
        accountId = TicketUtil.detachIbmAccountPrefix(accountId);

        if (isAvailableAccountCache()) {
            return accountCache.getMasterUserByAccountId(accountId);
        } else {
            try {
                JSONObject usersResult = getAccountUsers(CspSupplyCode, accountId);
                if (usersResult.has(KeyUserInfoList)) {
                    JSONArray userInfoList = usersResult.optJSONArray(KeyUserInfoList);
                    for (int i = 0; i < userInfoList.length(); i++) {
                        JSONObject userInfo = userInfoList.getJSONObject(i);
                        CloudZUser user = JsonUtil.unmarshal(userInfo.toString(), CloudZUser.class);
                        if (user.isMaster()) {
                            return user;
                        }
                    }
                }
            } catch (IOException | URISyntaxException e) {
                log.error("accountId:{}, error:{}", accountId, e);
            }
        }
        return null;
    }

    public static boolean isExistsCspApiInfo(String email, String accountId) {
        if (email != null && accountId != null) {
            if (isAvailableAccountCache()) {
                return accountCache.existsCspApiInfo(email, accountId);
            } else {
                try {
                    JSONArray infoList = getUserApiInfoListByEmail(email);
                    if (infoList != null) {
                        for (int i = 0; i < infoList.length(); i++) {
                            JSONObject info = infoList.getJSONObject(i);
                            if (info != null) {
                                if (info.has(CloudZUser.KeyCspApiInfo)) {
                                    JSONObject apiInfo = info.getJSONObject(CloudZUser.KeyCspApiInfo);
                                    String apiId = apiInfo.optString(CloudZCspApiInfo.KeyApiId);
                                    if (accountId.equals(apiId)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException | URISyntaxException e) {
                    log.error("email:{}, accountId:{}, error:{}", email, accountId, e.getMessage());
                }
            }
        }
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //IBM Only
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static JSONObject getCspApiInfoByUserId(String userId) throws IOException, URISyntaxException {
        RestApiUtil.RestApiResult result = request(String.format("/account/getSLApiInfo?userId=%s", userId), HttpMethod.GET, null);
        return new JSONObject(result.getResponseBody());
    }

    public static boolean isAvailableIbmCustomerCache() {
        return (ibmCustomerCache != null && ibmCustomerCache.isAvailable());
    }

    public static void updateIbmCustomerCache() {
        synchronized (ibmCustomerCacheLocker) {
            long now = System.currentTimeMillis();
            if (ibmCustomerCacheUpdateTime + IbmCustomerCacheUpdateInterval < now) {
                ibmCustomerCacheUpdateTime = now;
                log.info("IBM customer cache refreshing.");
                if (ibmCustomerCache == null) {
                    ibmCustomerCache = new IbmCustomerCache();
                } else {
                    ibmCustomerCache.clear();
                }
                if (config.getReverseSyncAccounts().size() > 0) {
                    for (IbmBrandAccount brand : config.getReverseSyncAccounts()) {
                        ibmCustomerCache.addBrand(brand);
                    }
                    log.info("IBM customer cache updated.");
                }
            } else {
                log.info("The IBM Customer cache was updated {} seconds ago. aborted. updateTime:{}, updateInterval: {}, now: {}", ((now - ibmCustomerCacheUpdateTime) / 1000), ibmCustomerCacheUpdateTime, IbmCustomerCacheUpdateInterval, now);
            }
        }
    }

    /*public void clearIbmCustomerCache() {
        ibmCustomerCache.clear();
        ibmCustomerCache = null;
    }*/
    public static String getIbmCustomerEmail(IbmBrandAccount brandAccount, long ibmUserCustomerId) {
        if (isAvailableIbmCustomerCache()) {
            updateIbmCustomerCache(); //주기적 동기화 비활성화에 따른 갱신.
            IbmCustomer customer = ibmCustomerCache.getIbmCustomer(brandAccount.getBrandId(), String.valueOf(ibmUserCustomerId));
            if (customer != null) {
                return customer.getEmail();
            }
        } else {
            return IbmService.getIbmCustomerEmail(brandAccount.getAccountId(), brandAccount.getApiKey(), ibmUserCustomerId);
        }
        return null;
    }

}

