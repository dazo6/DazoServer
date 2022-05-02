package com.dazo66.crawler;

import com.geccocrawler.gecco.annotation.*;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;

/**
 * @author Dazo66
 */
@Gecco(matchUrl = "https://kemono.party/fanbox/user/{user}/post/{post}", pipelines = {
        "consolePipeline", "fanboxImagePipeline"})
@Data
public class FanboxPost implements HtmlBean {

    @RequestParameter("user")
    private String artistId;
    @RequestParameter("post")
    private String post;
    @Text
    @HtmlField(cssPath = ".post__user-name")
    private String artistName;
    @HtmlField(cssPath = "#page>header>div.post__info>h1>span:nth-child(1)")
    private String title;
    @HtmlField(cssPath = "#page>header>div.post__info>div.post__published>time")
    private String time;
    @Href
    @HtmlField(cssPath = ".fileThumb")
    private Object[] images;
    private static final long serialVersionUID = -7127412585200687225L;


}
