package com.dazo66.util;

import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;

public class FanboxHelper {

    public static HttpRequest getHttpRequest(String url, String fanboxsessid) {
        HttpRequest httpRequest = new HttpGetRequest(url);
        httpRequest.addCookie("FANBOXSESSID", fanboxsessid);
        httpRequest.addHeader("Referer", "https://www.fanbox.cc/");
        httpRequest.addHeader("Origin", "https://www.fanbox.cc");
        return httpRequest;
    }


}
