package com.dazo66.crawler.kemono;

import com.alibaba.fastjson.JSONObject;
import com.dazo66.util.JsonUtils;
import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.Request;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


/**
 * @author Dazo66
 */
@Gecco(matchUrl = "https://kemono.su/api/v1/{type}/user/{artistId}/posts-legacy", pipelines = {"consolePipeline",
        "kemonoPostJsonPipeline"}, downloader = "kemonoDownloader")
@Setter
public class KemonoJsonUser implements JsonBean {

    @Request
    @Getter
    private HttpGetRequest request;
    @Getter
    @RequestParameter
    private String artistId;
    @Getter
    @RequestParameter
    private String type;

    @JSONPath("$.results")
    private List<JSONObject> results;

    public List<Result> getResults() {
        return JsonUtils.jsonObjectArray2Bean(results, Result.class);
    }

    @Data
    public static class Result {
        private String id;
        private String service;
        private String user;
    }


}
