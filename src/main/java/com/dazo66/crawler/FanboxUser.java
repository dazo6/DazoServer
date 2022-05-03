package com.dazo66.crawler;

import com.geccocrawler.gecco.annotation.*;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;

@Gecco(matchUrl = "https://kemono.party/fanbox/user/{user}", pipelines = {"consolePipeline",
        "fanboxPostPipeline"})
@Data
/**
 * @author Dazo66
 */ public class FanboxUser implements HtmlBean {

    @Request
    private HttpGetRequest request;

    @RequestParameter
    private String user;

    @Href
    @HtmlField(cssPath = "#paginator-top>menu>li:last-child>a")
    private String nextPage;

    @Href
    @HtmlField(cssPath = ".post-card__link > .fancy-link")
    private Object[] posts;

}
