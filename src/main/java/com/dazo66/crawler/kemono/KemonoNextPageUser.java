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
@Gecco(matchUrl = HTTPS_HOST_PATTERN + "{type}/user/{artistId}?o={offset}", pipelines = {
        "consolePipeline", "kemonoPostPipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoNextPageUser extends kemonoUser {
    @RequestParameter
    private Integer offset;
}
