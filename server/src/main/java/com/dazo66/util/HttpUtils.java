package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.UnsupportedCharsetException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class HttpUtils {


    private static final int SUCCESS = 200;

    /**
     * get
     *
     * @param host    请求域名
     * @param path    请求路径
     * @param headers 请求头
     * @param querys  请求参数 /aa?queryKey2=queryValue2&queryKey1=queryValue1
     * @return
     * @throws Exception
     */
    public static String doGet(String host, String path,
                               Map<String, String> headers,
                               Map<String, String> querys) {
        HttpClient httpClient = wrapClient(host);
        String resultString = "";
        try {
            HttpGet request = new HttpGet(buildUrl(host, path, querys));
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == SUCCESS) {
                resultString = EntityUtils.toString(response.getEntity());
            }
        } catch (IOException e) {
            log.error("调用接口出错:" + host + path, e);
        }

        return resultString;
    }

    /**
     * post form
     *
     * @param host    请求域名
     * @param path    请求路径
     * @param headers 请求头
     * @param querys  请求参数 /aa?queryKey2=queryValue2&queryKey1=queryValue1
     * @param bodys   form 表单
     * @return
     * @throws Exception
     */
    public static String doPostForForm(String host, String path,
                                       Map<String, String> headers,
                                       Map<String, String> querys,
                                       Map<String, String> bodys) {
        HttpClient httpClient = wrapClient(host);
        String resultString = "";

        try {
            HttpPost request = new HttpPost(buildUrl(host, path, querys));
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }

            if (bodys != null) {
                List<NameValuePair> nameValuePairList = new ArrayList<NameValuePair>();

                for (String key : bodys.keySet()) {
                    nameValuePairList.add(new BasicNameValuePair(key, bodys.get(key)));
                }
                UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(nameValuePairList, "utf-8");
                formEntity.setContentType("application/x-www-form-urlencoded; charset=UTF-8");
                request.setEntity(formEntity);
            }

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == SUCCESS) {
                resultString = EntityUtils.toString(response.getEntity());
            }
        } catch (IOException e) {
            log.error("调用接口出错:" + host + path, e);
        }

        return resultString;

    }

    /**
     * Post String
     *
     * @param host    请求域名
     * @param path    请求路径
     * @param headers 请求头
     * @param querys  请求参数 /aa?queryKey2=queryValue2&queryKey1=queryValue1
     * @param body    请求体，String字符串
     * @return HttpResponse
     * @throws Exception
     */
    public static String doPostForString(String host, String path,
                                         Map<String, String> headers,
                                         Map<String, String> querys,
                                         String body) {
        HttpClient httpClient = wrapClient(host);
        String resultString = "";

        try {
            HttpPost request = new HttpPost(buildUrl(host, path, querys));
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }

            if (StringUtils.isNoneBlank(body)) {
                StringEntity stringEntity = new StringEntity(body, "utf-8");
                stringEntity.setContentEncoding("UTF-8");
                stringEntity.setContentType("application/json");
                request.setEntity(stringEntity);
            }

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == SUCCESS) {
                resultString = EntityUtils.toString(response.getEntity());
            }
        } catch (UnsupportedCharsetException | ParseException | IOException e) {
            log.error("调用接口出错:" + host + path, e);
        }
        return resultString;
    }

    /**
     * Post stream
     *
     * @param host
     * @param path
     * @param headers
     * @param querys
     * @param body
     * @return
     * @throws Exception
     */
    public static String doPostForStream(String host, String path,
                                         Map<String, String> headers,
                                         Map<String, String> querys,
                                         byte[] body) {
        HttpClient httpClient = wrapClient(host);
        String resultString = "";

        try {
            HttpPost request = new HttpPost(buildUrl(host, path, querys));
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }

            if (body != null) {
                request.setEntity(new ByteArrayEntity(body));
            }

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == SUCCESS) {
                resultString = EntityUtils.toString(response.getEntity());
            }
        } catch (IOException | ParseException e) {
            log.error("调用接口出错:" + host + path, e);
        }
        return resultString;
    }

    /**
     * Put String
     *
     * @param host
     * @param path
     * @param headers
     * @param querys
     * @param body
     * @return
     * @throws Exception
     */
    public static String doPutForString(String host, String path,
                                        Map<String, String> headers,
                                        Map<String, String> querys,
                                        String body) {
        HttpClient httpClient = wrapClient(host);
        String resultString = "";

        try {
            HttpPut request = new HttpPut(buildUrl(host, path, querys));
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }

            if (StringUtils.isNotBlank(body)) {
                request.setEntity(new StringEntity(body, "utf-8"));
            }

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == SUCCESS) {
                resultString = EntityUtils.toString(response.getEntity());
            }
        } catch (UnsupportedCharsetException | IOException | ParseException e) {
            log.error("调用接口出错:" + host + path, e);
        }
        return resultString;
    }

    /**
     * Put stream
     *
     * @param host
     * @param path
     * @param headers
     * @param querys
     * @param body
     * @return
     * @throws Exception
     */
    public static String doPutForStream(String host, String path,
                                        Map<String, String> headers,
                                        Map<String, String> querys,
                                        byte[] body) {
        HttpClient httpClient = wrapClient(host);
        String resultString = "";


        try {
            HttpPut request = new HttpPut(buildUrl(host, path, querys));
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }

            if (body != null) {
                request.setEntity(new ByteArrayEntity(body));
            }

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == SUCCESS) {
                resultString = EntityUtils.toString(response.getEntity());
            }
        } catch (IOException | ParseException e) {
            log.error("调用接口出错:" + host + path, e);
        }
        return resultString;
    }

    /**
     * Delete
     *
     * @param host
     * @param path
     * @param headers
     * @param querys
     * @return
     * @throws Exception
     */
    public static String doDelete(String host, String path,
                                  Map<String, String> headers,
                                  Map<String, String> querys) {
        HttpClient httpClient = wrapClient(host);
        String resultString = "";

        try {
            HttpDelete request = new HttpDelete(buildUrl(host, path, querys));
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == SUCCESS) {
                resultString = EntityUtils.toString(response.getEntity());
            }
        } catch (IOException | ParseException e) {
            log.error("调用接口出错:" + host + path, e);
        }
        return resultString;
    }

    private static String buildUrl(String host, String path, Map<String, String> querys) throws UnsupportedEncodingException {
        StringBuilder sbUrl = new StringBuilder();
        sbUrl.append(host);
        if (!StringUtils.isBlank(path)) {
            sbUrl.append(path);
        }
        if (null != querys) {
            StringBuilder sbQuery = new StringBuilder();
            for (Map.Entry<String, String> query : querys.entrySet()) {
                if (0 < sbQuery.length()) {
                    sbQuery.append("&");
                }
                if (StringUtils.isBlank(query.getKey()) && !StringUtils.isBlank(query.getValue())) {
                    sbQuery.append(query.getValue());
                }
                if (!StringUtils.isBlank(query.getKey())) {
                    sbQuery.append(query.getKey());
                    if (!StringUtils.isBlank(query.getValue())) {
                        sbQuery.append("=");
                        sbQuery.append(URLEncoder.encode(query.getValue(), "utf-8"));
                    }
                }
            }
            if (0 < sbQuery.length()) {
                sbUrl.append("?").append(sbQuery);
            }
        }

        return sbUrl.toString();
    }

    private static HttpClient wrapClient(String host) {
        HttpClient httpClient = new DefaultHttpClient();
        if (host.startsWith("https://")) {
            sslClient(httpClient);
        }

        return httpClient;
    }

    private static void sslClient(HttpClient httpClient) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] xcs, String str) {

                }

                public void checkServerTrusted(X509Certificate[] xcs, String str) {

                }
            };
            ctx.init(null, new TrustManager[]{tm}, null);
            SSLSocketFactory ssf = new SSLSocketFactory(ctx);
            ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            ClientConnectionManager ccm = httpClient.getConnectionManager();
            SchemeRegistry registry = ccm.getSchemeRegistry();
            registry.register(new Scheme("https", 443, ssf));
        } catch (KeyManagementException ex) {
            throw new RuntimeException(ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("headKey1", "headValue1");
        headerMap.put("headKey2", "headValue2");

        HashMap<String, String> queryMap = new HashMap<>();
        queryMap.put("queryKey1", "queryValue1");
        queryMap.put("queryKey2", "queryValue2");

        HashMap<String, String> bodyMap = new HashMap<>();
        bodyMap.put("bodyMapKey1", "bodyValue1");
        bodyMap.put("bodyMapKey2", "bodyValue2");
//		HttpResponse get = doGet("http://www.baidu.com", "/aa", "get", headerMap, queryMap);
//		System.out.println(get);
//		HttpResponse formPost = doPost("http://www.baidu.com", "/aa", "get", headerMap, queryMap, bodyMap);
//		System.out.println(formPost);
        String s = doPostForString("http://www.baidu.com", "/aa", headerMap, queryMap, "jsonString");

        System.out.println(s);
    }
}
