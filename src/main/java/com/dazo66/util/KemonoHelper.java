package com.dazo66.util;

import com.dazo66.config.KemonoProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.net.HttpCookie;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2022/10/21 16:25
 **/
@Slf4j
public class KemonoHelper {


    public static Map<String, String> loginAndGetCookie(String baseUrl, KemonoProperties.Mode mode,
                                                        String username, String password) {
        try (CloseableHttpClient client = HttpClientBuilder
                .create()
                .setConnectionTimeToLive(180, TimeUnit.SECONDS)
                .build()) {
            String endpoint = mode == KemonoProperties.Mode.HTML
                    ? baseUrl + "account/login"
                    : baseUrl + "api/v1/authentication/login";
            HttpPost httpPost = new HttpPost(new URI(endpoint));
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(180 * 1000)
                    .setSocketTimeout(180 * 1000)
                    .setConnectionRequestTimeout(180 * 1000)
                    .build();
            httpPost.setConfig(requestConfig);
            if (mode == KemonoProperties.Mode.HTML) {
                httpPost.setEntity(new UrlEncodedFormEntity(java.util.Arrays.asList(
                        new BasicNameValuePair("username", username),
                        new BasicNameValuePair("password", password),
                        new BasicNameValuePair("location", "/favorites")
                ), "UTF-8"));
            } else {
                httpPost.addHeader("Content-Type", "application/json");
                Map<String, String> map = new HashMap<>();
                map.put("username", username);
                map.put("password", password);
                StringEntity entity = new StringEntity(JsonUtils.object2JsonString(map), "utf-8");
                httpPost.setEntity(entity);
            }
            try (CloseableHttpResponse execute = client.execute(httpPost)) {
                int status = execute.getStatusLine().getStatusCode();
                if (status == 200 || status == 302 || status == 303) {
                    Header[] headers = execute.getHeaders("set-cookie");
                    Map<String, String> cookieMap = new HashMap<>();
                    for (Header header : headers) {
                        List<HttpCookie> cookies = HttpCookie.parse(header.getValue());
                        for (HttpCookie cookie : cookies) {
                            cookieMap.put(cookie.getName(), cookie.getValue());
                        }
                    }
                    return cookieMap;
                }
                log.error("get kemono-compatible cookie fail {}, endpoint={}", status, endpoint);
                return null;
            }
        } catch (Exception e) {
            log.error("get kemono-compatible cookie fail:", e);
            return null;
        }

    }

}
