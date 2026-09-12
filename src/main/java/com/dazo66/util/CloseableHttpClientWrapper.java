package com.dazo66.util;

import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2022/10/21 16:40
 **/
@Getter
@Setter
public class CloseableHttpClientWrapper extends CloseableHttpClient {

    private volatile CloseableHttpClient client;

    public CloseableHttpClientWrapper(CloseableHttpClient client) {
        this.client = client;
    }

    @Override
    protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
        return client.execute(target, request, context);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    public HttpParams getParams() {
        return client.getParams();
    }

    @Override
    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context) throws IOException, ClientProtocolException {
        return client.execute(target, request, responseHandler, context);
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return client.getConnectionManager();
    }
}
