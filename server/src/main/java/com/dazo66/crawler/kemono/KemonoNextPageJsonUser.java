package com.dazo66.crawler.kemono;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Dazo66
 */
@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = "https://kemono.su/api/v1/{type}/user/{artistId}/posts-legacy?o={offset}", pipelines = {
        "consolePipeline", "kemonoPostJsonPipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoNextPageJsonUser extends KemonoJsonUser {
    @RequestParameter
    private Integer offset;
}
