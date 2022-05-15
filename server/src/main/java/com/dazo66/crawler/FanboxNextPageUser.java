package com.dazo66.crawler;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Dazo66
 */
@EqualsAndHashCode(callSuper = true)
@Gecco(matchUrl = "https://kemono.party/{type}/user/{artistId}?o={offset}", pipelines = {
        "consolePipeline", "fanboxPostPipeline"})
@Data
public class FanboxNextPageUser extends FanboxUser {
    @RequestParameter
    private Integer offset;
}
