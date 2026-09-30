package com.dazo66.crawler.fanbox;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:42
 **/
@Gecco(matchUrl = "https://api.fanbox.cc/post.paginateCreator?creatorId={userName}", pipelines = {
        "consolePipeline", "fanboxPaginatePipeline"}, downloader = "ja3ProxyHttpDownloader")
public class FanboxPageinate implements JsonBean {
    @JSONPath("$.body")
    private List<Object> pages;

    public List<String> getUrls() {
        if (pages == null) return Collections.emptyList();
        List<String> urls = new ArrayList<>();
        for (Object page : pages) {
            addPageUrls(page, urls);
        }
        return urls;
    }

    public void setPages(List<Object> pages) {
        this.pages = pages;
    }

    private void addPageUrls(Object value, List<String> urls) {
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.startsWith("http://") || text.startsWith("https://")) {
                urls.add(text);
            } else if (text.startsWith("{") || text.startsWith("[")) {
                try {
                    addPageUrls(JSON.parse(text), urls);
                } catch (RuntimeException ignored) {
                    // A malformed page entry must not be passed to the URL scheduler.
                }
            }
        } else if (value instanceof JSONObject) {
            addPageUrls(((JSONObject) value).get("pageUrls"), urls);
        } else if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                addPageUrls(item, urls);
            }
        }
    }

    @Getter
    @Setter
    @RequestParameter
    private String userName;
}
