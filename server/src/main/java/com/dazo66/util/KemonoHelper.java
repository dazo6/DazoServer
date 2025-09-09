package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.net.HttpCookie;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dazo66.crawler.kemono.KemonoContents.KEMONO_HOST;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2022/10/21 16:25
 **/
@Slf4j
public class KemonoHelper {


    public static Map<String, String> loginAndGetCookie(String username, String password) {
        try {
            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost(new URI(KEMONO_HOST + "api/v1/authentication/login"));
            httpPost.addHeader("Content-Type", "application/json");
            Map<String, String> map = new HashMap<>();
            map.put("username", username);
            map.put("password", password);
            StringEntity entity = new StringEntity(JsonUtils.object2JsonString(map), "utf-8");
            httpPost.setEntity(entity);
            CloseableHttpResponse execute = client.execute(httpPost);
            if (execute.getStatusLine().getStatusCode() == 200) {
                Header[] headers = execute.getHeaders("set-cookie");
                Map<String, String> cookieMap = new HashMap<>();
                for (Header header : headers) {
                    List<HttpCookie> cookies = HttpCookie.parse(header.toString());
                    for (HttpCookie cookie : cookies) {
                        cookieMap.put(cookie.getName(), cookie.getValue());
                    }
                }
                return cookieMap;
            } else {
                log.error("get kemono.su cookie fail {}", execute.getStatusLine().getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("get kemono.su cookie fail:", e);
            return null;
        }

    }

}
