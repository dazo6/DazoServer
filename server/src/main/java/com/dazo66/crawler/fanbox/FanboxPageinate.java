package com.dazo66.crawler.fanbox;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:42
 **/
@Gecco(matchUrl = "https://api.fanbox.cc/post.paginateCreator?creatorId={userName}", pipelines = {
        "consolePipeline", "fanboxPaginatePipeline"}, downloader = "myHttpDownloader")
public class FanboxPageinate implements JsonBean {
    @Setter
    @Getter
    @JSONPath("$.body")
    private List<String> urls;

    @Getter
    @Setter
    @RequestParameter
    private String userName;
}
