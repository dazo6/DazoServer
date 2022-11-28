package com.dazo66.crawler;


import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.Href;
import com.geccocrawler.gecco.annotation.HtmlField;
import com.geccocrawler.gecco.annotation.Text;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode
@Gecco(matchUrl = "https://kemono.party/favorites", pipelines = {
        "consolePipeline", "fanboxFavouritePipeline"}, downloader = "myHttpDownloader")
@Data
public class FanboxFavourite implements HtmlBean {

    @Href
    @HtmlField(cssPath = "#page > div.card-list.card-list--phone > div.card-list__items > a")
    private List<String> artistUrls;

    @Text
    @HtmlField(cssPath = "#page > div.card-list.card-list--phone > div.card-list__items > a > div.user-card__info > div.user-card__name")
    private List<String> artistNames;

    @Href
    @HtmlField(cssPath = "#paginator-bottom>menu>a.next")
    private String nextPage;

}
