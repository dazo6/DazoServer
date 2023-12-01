package com.dazo66.crawler.kemono;


import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = "https://kemono.su/favorites?o={offset}", pipelines = {
        "consolePipeline", "kemonoFavouritePipeline"}, downloader = "myHttpDownloader")
@Data
public class KemonoFavouriteNextPage extends KemonoFavourite {

    @RequestParameter
    private Integer offset;

}
