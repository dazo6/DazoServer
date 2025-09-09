package com.dazo66.crawler.kemono;


import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.dazo66.crawler.kemono.KemonoContents.KEMONO_HOST;
@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = KEMONO_HOST + "favorites?o={offset}", pipelines = {
        "consolePipeline", "kemonoFavouritePipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoFavouriteNextPage extends KemonoFavourite {

    @RequestParameter
    private Integer offset;

}
