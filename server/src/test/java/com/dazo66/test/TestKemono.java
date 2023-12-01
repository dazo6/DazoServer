package com.dazo66.test;

import com.dazo66.util.IpUtils;
import com.dazo66.util.MultiThreadHttpDownloader;
import com.google.common.collect.Lists;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2022/10/21 15:47
 **/
public class TestKemono {

    @Test
    public void testLogin() throws URISyntaxException, IOException {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(new URI("https://kemono.su/account/login"));
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("username", "dazo66"));
        form.add(new BasicNameValuePair("password", "123456"));
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

    @Test
    public void testMulThreadDownload() throws Throwable {
            RequestConfig clientConfig =
                    RequestConfig.custom().setRedirectsEnabled(true).setConnectTimeout(60000).setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();

            PoolingHttpClientConnectionManager syncConnectionManager =
                    new PoolingHttpClientConnectionManager();

            syncConnectionManager.setMaxTotal(1000);
            syncConnectionManager.setDefaultMaxPerRoute(50);
            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (IpUtils.checkIpPort("127.0.0.1", 1080)) {
            httpClientBuilder.setProxy(new HttpHost("127.0.0.1", 1080));
        }
            BasicHeader cookieHeader = new BasicHeader("cookie", "__ddg1_=Y5Y4b5alo4IfCEkW54hK; session=eyJfcGVybWFuZW50Ijp0cnVlLCJhY2NvdW50X2lkIjoxNDUyMDZ9.Y68YtQ.NFDVcHy8vdD9W5HIikqcnPHgAPo;");
            BasicHeader agentHeader = new BasicHeader("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; " + "Win64; x64) " + "AppleWebKit/537.36 (KHTML, " + "like Gecko) Chrome/100.0.4896.127 " + "Safari/537.36");
            CloseableHttpClient httpClient =
                    httpClientBuilder.setDefaultHeaders(Lists.newArrayList(cookieHeader, agentHeader)).setDefaultRequestConfig(clientConfig).setRedirectStrategy(new DefaultRedirectStrategy()).setConnectionManager(syncConnectionManager).build();
            String url = "https://kemono.su/data/22/1b/221ba4c04c4e328ea32adc5108cc66e4aff90ce107ca69243dd353c6711b77bf.gif?f=2addebe9-6a7f-4149-86f0-a1473b98b5d8.gif";
            new MultiThreadHttpDownloader(httpClient, url, "D:/ls-lR.gif", 5, 5000).get();

    }


}
