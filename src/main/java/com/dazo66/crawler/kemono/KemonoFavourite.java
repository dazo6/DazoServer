package com.dazo66.crawler.kemono;


import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.Href;
import com.geccocrawler.gecco.annotation.HtmlField;
import com.geccocrawler.gecco.annotation.Text;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

import static com.dazo66.crawler.kemono.KemonoContents.HTTPS_HOST_PATTERN;
@EqualsAndHashCode
@Gecco(matchUrl = HTTPS_HOST_PATTERN + "favorites", pipelines = {
        "consolePipeline", "kemonoFavouritePipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoFavourite implements HtmlBean {

    @Href
    @HtmlField(cssPath = "#page .card-list__items > a, #page .card-list__items > article > a")
    private List<String> artistUrls;

    @Text
    @HtmlField(cssPath = "#page .card-list__items .user-card__name")
    private List<String> artistNames;

    @Href
    @HtmlField(cssPath = "#paginator-bottom>menu>a.next")
    private String nextPage;

}
