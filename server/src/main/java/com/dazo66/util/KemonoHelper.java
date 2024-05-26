package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2022/10/21 16:25
 **/
@Slf4j
public class KemonoHelper {


    public static Map<String, String> loginAndGetCookie(String username, String password) {
        try {
            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost(new URI("https://kemono.su/account/login"));
            List<NameValuePair> form = new ArrayList<>();
            form.add(new BasicNameValuePair("username", username));
            form.add(new BasicNameValuePair("password", password));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
            httpPost.setEntity(entity);
            CloseableHttpResponse execute = client.execute(httpPost);
            if (execute.getStatusLine().getStatusCode() == 302
                    && execute.getHeaders("Location")[0].getValue().contains("/artists?logged_in=yes")) {
                Header[] headers = execute.getHeaders("set-cookie");
                Map<String, String> cookieMap = new HashMap<>();
                if (headers.length > 0) {
                    for (Header header : headers) {
                        String value = header.getValue();
                        List<String> ddg1 = Arrays.asList(value.split("; ")).stream().filter(s -> s.contains("__ddg1")).collect(Collectors.toList());
                        if (!ddg1.isEmpty()) {
                            cookieMap.put("__ddg1", ddg1.get(0).split("=")[1]);
                        }
                    }
                }
                return cookieMap;
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("get kemono.su cookie fail:", e);
            return null;
        }

    }

}
