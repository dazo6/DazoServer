package com.dazo66.util;

import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;

public class FanboxHelper {

    public static HttpRequest getHttpRequest(String url, String fanboxsessid) {
        HttpRequest httpRequest = new HttpGetRequest(url);
        httpRequest.addCookie("FANBOXSESSID", fanboxsessid);
        httpRequest.addHeader("Referer", "https://www.fanbox.cc/");
        httpRequest.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        httpRequest.addHeader("Origin", "https://www.fanbox.cc");
        return httpRequest;
    }


}
