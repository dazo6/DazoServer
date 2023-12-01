package com.dazo66.crawler.fanbox;

import com.alibaba.fastjson.JSON;
import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:42
 **/
@EqualsAndHashCode
@Gecco(matchUrl = "https://api.fanbox.cc/post.listCreator?creatorId={userName}&limit=5", pipelines = {
        "consolePipeline", "fanboxPostListPipeline"}, downloader = "myHttpDownloader")
@Data
public class FanboxPostList implements JsonBean {

    @JSONPath("$.body.items")
    private List<FanboxPost> items;

    @JSONPath("$.body.nextUrl")
    private String nextUrl;

    @RequestParameter
    private String userName;

    @RequestParameter
    private String userId;

}
