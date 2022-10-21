package com.dazo66.test;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2022/10/21 15:47
 **/
public class TestLogin {

    @Test
    public void testLogin() throws URISyntaxException, IOException {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(new URI("https://kemono.party/account/login"));
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("username", "dazo66"));
        form.add(new BasicNameValuePair("password", "sun2520."));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
        httpPost.setEntity(entity);
        CloseableHttpResponse execute = client.execute(httpPost);
        if (execute.getStatusLine().getStatusCode() == 302 && execute.getHeaders("Location")[0].getValue().contains("/artists?logged_in=yes")) {
            Header[] headers = execute.getHeaders("set-cookie");
            StringBuilder cookieBuilder = new StringBuilder();
            if (headers.length > 0) {
                for (Header header : headers) {
                    String value = header.getValue();
                    cookieBuilder.append(value, 0, value.indexOf(";") + 1);
                    cookieBuilder.append(" ");
                }
            }
            System.out.println("cookie: " + cookieBuilder);
        } else {
            System.out.println("登录失败~");
        }



    }


}
