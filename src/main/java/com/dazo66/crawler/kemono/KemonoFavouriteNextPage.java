package com.dazo66.crawler.kemono;


import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.dazo66.crawler.kemono.KemonoContents.HTTPS_HOST_PATTERN;
@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = HTTPS_HOST_PATTERN + "favorites?o={offset}", pipelines = {
        "consolePipeline", "kemonoFavouritePipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoFavouriteNextPage extends KemonoFavourite {

    @RequestParameter
    private Integer offset;

}
