package com.dazo66.crawler.kemono;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.dazo66.crawler.kemono.KemonoContents.KEMONO_HOST;
/**
 * @author Dazo66
 */
@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = KEMONO_HOST + "{type}/user/{artistId}?o={offset}", pipelines = {
        "consolePipeline", "kemonoPostPipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoNextPageUser extends kemonoUser {
    @RequestParameter
    private Integer offset;
}
