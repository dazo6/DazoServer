package com.dazo66.crawler.kemono;

import com.geccocrawler.gecco.annotation.*;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;

import static com.dazo66.crawler.kemono.KemonoContents.HTTPS_HOST_PATTERN;

/**
 * @author Dazo66
 */
@Gecco(matchUrl = HTTPS_HOST_PATTERN + "{type}/user/{user}/post/{post}", pipelines = {
        "consolePipeline", "kemonoImagePipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoPost implements HtmlBean {

    @Request
    private HttpGetRequest request;

    @RequestParameter
    private String type;
    @RequestParameter("user")
    private String artistId;
    @RequestParameter("post")
    private String post;
    @Text
    @HtmlField(cssPath = ".post__user-name")
    private String artistName;
    @Text
    @HtmlField(cssPath = "#page > header .post__info > h1.post__title > span:nth-child(1)")
    private String title;
    @Text
    @HtmlField(cssPath = "#page > header .post__info > div.post__published")
    private String time;
    @Href
    @HtmlField(cssPath = ".fileThumb")
    private Object[] images;
    @Href
    @HtmlField(cssPath = ".post__attachment-link")
    private Object[] downloads;
    @Text
    @HtmlField(cssPath = ".post__attachment-link")
    private Object[] downloadsName;
    private static final long serialVersionUID = -7127412585200687225L;


}
