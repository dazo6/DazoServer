package com.dazo66.crawler;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.Href;
import com.geccocrawler.gecco.annotation.HtmlField;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;

@Gecco(matchUrl = "https://kemono.party/fanbox/user/{user}", pipelines = {"consolePipeline",
        "fanboxPostPipeline"})
@Data
/**
 * @author Dazo66
 */ public class FanboxUser implements HtmlBean {

    @RequestParameter
    private String user;

    @Href
    @HtmlField(cssPath = "#paginator-top>menu>li:last-child>a")
    private String nextPage;

    @Href
    @HtmlField(cssPath = ".post-card__link > .fancy-link")
    private Object[] posts;

}
