package com.dazo66.crawler;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.Href;
import com.geccocrawler.gecco.annotation.HtmlField;
import com.geccocrawler.gecco.annotation.Request;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;

@Gecco(matchUrl = "https://kemono.party/{type}/user/{artistId}", pipelines = {"consolePipeline",
        "fanboxPostPipeline"}, downloader = "myHttpDownloader")
@Data
/**
 * @author Dazo66
 */ public class FanboxUser implements HtmlBean {

    @Request
    private HttpGetRequest request;

    @RequestParameter
    private String artistId;

    @RequestParameter
    private String type;

    @Href
    @HtmlField(cssPath = "#paginator-bottom>menu>a.next")
    private String nextPage;

    @Href
    @HtmlField(cssPath = "div.card-list__items>article>a")
    private Object[] posts;

}
