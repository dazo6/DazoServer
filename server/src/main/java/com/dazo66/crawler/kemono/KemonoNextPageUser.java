package com.dazo66.crawler.kemono;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Dazo66
 */
@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = "https://kemono.su/{type}/user/{artistId}?o={offset}", pipelines = {
        "consolePipeline", "kemonoPostPipeline"}, downloader = "myHttpDownloader")
@Data
public class KemonoNextPageUser extends kemonoUser {
    @RequestParameter
    private Integer offset;
}
