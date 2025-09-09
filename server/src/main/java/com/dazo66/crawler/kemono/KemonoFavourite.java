package com.dazo66.crawler.kemono;


import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.Href;
import com.geccocrawler.gecco.annotation.HtmlField;
import com.geccocrawler.gecco.annotation.Text;
import com.geccocrawler.gecco.spider.HtmlBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

import static com.dazo66.crawler.kemono.KemonoContents.KEMONO_HOST;
@EqualsAndHashCode
@Gecco(matchUrl = KEMONO_HOST + "favorites", pipelines = {
        "consolePipeline", "kemonoFavouritePipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoFavourite implements HtmlBean {

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
