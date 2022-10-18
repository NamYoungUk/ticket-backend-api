package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.data.model.AppConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.AuthSchemeBase;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RestApiUtil {
    @Data
    public static class RestApiResult {
        int status;
        Header[] headers;
        String responseBody;
        HttpResponse response;

        public RestApiResult(int status, Header[] headers, String responseBody, HttpResponse response) {
            this.status = status;
            this.headers = headers;
            this.responseBody = responseBody;
            this.response = response;
        }

        public boolean isOK() {
            return (status >= 200 && status < 300);
        }
    }

    private static SSLConnectionSocketFactory buildSSLFactory() {
        //https://stackoverflow.com/questions/19517538/ignoring-ssl-certificate-in-apache-httpclient-4-3
        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(builder.build(), buildHostnameVerifier());
            return socketFactory;
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static HostnameVerifier buildHostnameVerifier() {
        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        return hostnameVerifier;
    }

    private static RequestBuilder getBuilder(HttpMethod method) {
        switch (method) {
            case HEAD:
                return RequestBuilder.head();
            case PATCH:
                return RequestBuilder.patch();
            case OPTIONS:
                return RequestBuilder.options();
            case TRACE:
                return RequestBuilder.trace();
            case POST:
                return RequestBuilder.post();
            case PUT:
                return RequestBuilder.put();
            case DELETE:
                return RequestBuilder.delete();
            case GET:
            default:
                return RequestBuilder.get();
        }
    }

    private static RestApiResult handleResponse(String targetUrl, HttpResponse response) throws IOException {
        HttpEntity resBody = response.getEntity();
        InputStream is = resBody.getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, AppConstants.CharsetUTF8));
        String line;
        StringBuilder sb = new StringBuilder();
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            Util.ignoreException(e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                Util.ignoreException(e);
            }
        }
        int responseStatus = response.getStatusLine().getStatusCode();
        String responseBody = sb.toString();
        log.debug("Response received for targetUrl: {}. status: {}", targetUrl, responseStatus);
        if (responseStatus >= 200 && responseStatus < 300) {
            return new RestApiResult(responseStatus, response.getAllHeaders(), responseBody, response);
        } else {
            return new RestApiResult(responseStatus, response.getAllHeaders(), responseBody, response);
        }
        //throw new IOException(String.format("API call failed, url:%s, code:%d, body:%s", targetUrl, responseStatus, responseBody));
    }

    public static RestApiResult request(String targetUrl, HttpMethod method, List<Header> headers, HttpEntity body, UsernamePasswordCredentials credentials) throws IOException, URISyntaxException {
        AppConfig config = AppConfig.getInstance();
        int timeout = config.getHttpRequestTimeout();
        return request(targetUrl, method, headers, body, credentials, timeout);
    }

    public static RestApiResult request(String targetUrl, HttpMethod method, List<Header> headers, HttpEntity body, UsernamePasswordCredentials credentials, int timeoutMillis) throws IOException, URISyntaxException {
        final URL url = new URL(targetUrl);
        final String urlHost = url.getHost();
        final int urlPort = url.getPort();
        final String urlProtocol = url.getProtocol();
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        RequestBuilder reqBuilder = getBuilder(method);
        RequestConfig.Builder confBuilder = RequestConfig.custom();
        HttpClientContext clientContext = null;
        SSLConnectionSocketFactory sslFactory = buildSSLFactory();
        if (sslFactory != null) {
            clientBuilder.setSSLSocketFactory(sslFactory);
        }

        reqBuilder.setUri(url.toURI());
        if (headers != null) {
            for (Header header : headers) {
                reqBuilder.setHeader(header);
            }
        }

        if (body != null) {
            reqBuilder.setEntity(body);
        }

        if (credentials != null) {
            List<String> authPrefs = new ArrayList<>();
            authPrefs.add(AuthSchemes.BASIC);
            confBuilder.setTargetPreferredAuthSchemes(authPrefs);

            CredentialsProvider credProvider = new BasicCredentialsProvider();
            credProvider.setCredentials(new AuthScope(urlHost, urlPort, AuthScope.ANY_REALM), credentials);
            clientBuilder.setDefaultCredentialsProvider(credProvider);

            clientContext = HttpClientContext.create();
            AuthCache authCache = new BasicAuthCache();
            AuthSchemeBase authScheme = new BasicScheme();
            authCache.put(new HttpHost(urlHost, urlPort, urlProtocol), authScheme);
            clientContext.setAuthCache(authCache);
        }

        AppConfig config = AppConfig.getInstance();
        if (config.isHttpRequestTimeoutEnabled()) {
            log.info("http request timeout applied. {} milli seconds.", timeoutMillis);
            confBuilder.setConnectionRequestTimeout(timeoutMillis);
            confBuilder.setConnectTimeout(timeoutMillis);
            confBuilder.setSocketTimeout(timeoutMillis);
        }

        RequestConfig reqConfig = confBuilder.build();
        reqBuilder.setConfig(reqConfig);
        clientBuilder.setDefaultRequestConfig(reqConfig);

        HttpClient httpClient = clientBuilder.build();
        HttpUriRequest request = reqBuilder.build();
        HttpResponse response;
        log.debug("http requesting... targetUrl: {}", targetUrl);
        if (clientContext != null) {
            response = httpClient.execute(request, clientContext);
        } else {
            response = httpClient.execute(request);
        }
        return handleResponse(targetUrl, response);
    }

    private static List<Header> buildHeaderByAuthorization(String authorization) {
        List<Header> headers = null;
        if (authorization != null) {
            headers = new ArrayList<>();
            headers.add(new BasicHeader(HttpHeaders.AUTHORIZATION, authorization));
        }
        return headers;
    }

    public static RestApiResult get(String targetUrl) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.GET, null, null, null);
    }

    public static RestApiResult get(String targetUrl, String authorization) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.GET, buildHeaderByAuthorization(authorization), null, null);
    }

    public static RestApiResult get(String targetUrl, int timeoutMillis) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.GET, null, null, null, timeoutMillis);
    }

    public static RestApiResult get(String targetUrl, String authorization, int timeoutMillis) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.GET, buildHeaderByAuthorization(authorization), null, null, timeoutMillis);
    }

    public static RestApiResult get(String targetUrl, UsernamePasswordCredentials credentials) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.GET, null, null, credentials);
    }

    public static RestApiResult get(String targetUrl, UsernamePasswordCredentials credentials, int timeoutMillis) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.GET, null, null, credentials, timeoutMillis);
    }

    public static RestApiResult post(String targetUrl, HttpEntity body) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.POST, null, body, null);
    }

    public static RestApiResult post(String targetUrl, String authorization, HttpEntity body) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.POST, buildHeaderByAuthorization(authorization), body, null);
    }

    public static RestApiResult post(String targetUrl, HttpEntity body, int timeoutMillis) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.POST, null, body, null, timeoutMillis);
    }

    public static RestApiResult post(String targetUrl, String authorization, HttpEntity body, int timeoutMillis) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.POST, buildHeaderByAuthorization(authorization), body, null, timeoutMillis);
    }

    public static RestApiResult post(String targetUrl, HttpEntity body, UsernamePasswordCredentials credentials) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.POST, null, body, credentials);
    }

    public static RestApiResult post(String targetUrl, HttpEntity body, UsernamePasswordCredentials credentials, int timeoutMillis) throws IOException, URISyntaxException {
        return request(targetUrl, HttpMethod.POST, null, body, credentials, timeoutMillis);
    }

    public static RestTemplate buildTemplate(UsernamePasswordCredentials credentials) {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);
        HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider).build();
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setHttpClient(client);
        return new RestTemplate(clientHttpRequestFactory);
    }
}