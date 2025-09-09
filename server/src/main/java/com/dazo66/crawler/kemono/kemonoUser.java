package com.dazo66.crawler.kemono;

import com.geccocrawler.gecco.annotation.*;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;

import static com.dazo66.crawler.kemono.KemonoContents.KEMONO_HOST;

@Gecco(matchUrl = KEMONO_HOST + "{type}/user/{artistId}", pipelines = {"consolePipeline",
        "kemonoPostPipeline"}, downloader = "kemonoDownloader")
@Data
/**
 * @author Dazo66
 */ public class kemonoUser implements HtmlBean {

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
