package com.dazo66.crawler.kemono;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.dazo66.crawler.kemono.KemonoContents.HTTPS_HOST_PATTERN;
/**
 * @author Dazo66
 */
@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = HTTPS_HOST_PATTERN + "api/v1/{type}/user/{artistId}/posts?o={offset}", pipelines = {
        "consolePipeline", "kemonoPostJsonPipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoNextPageJsonUser extends KemonoJsonUser {
    @RequestParameter
    private Integer offset;
}
