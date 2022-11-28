package com.dazo66.crawler;


import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = "https://kemono.party/favorites?o={offset}", pipelines = {
        "consolePipeline", "fanboxFavouritePipeline"}, downloader = "myHttpDownloader")
@Data
public class FanboxFavouriteNextPage extends FanboxFavourite {

    @RequestParameter
    private Integer offset;

}
