package com.dazo66.crawler.fanbox;

import com.alibaba.fastjson.JSONObject;
import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:42
 **/
@EqualsAndHashCode
@Gecco(matchUrl = "https://api.fanbox.cc/post.listCreator?creatorId={userName}&limit={limit}", pipelines = {
        "fanboxPostListPipeline"}, downloader = "myHttpDownloader")
public class FanboxPostList implements JsonBean {

    @Setter
    @JSONPath("$.body.items")
    private List<JSONObject> items;
    @Getter
    @Setter
    @RequestParameter
    private Integer limit;

    @Getter
    @Setter
    @JSONPath("$.body.nextUrl")
    private String nextUrl;
    @Getter
    @Setter
    @RequestParameter
    private String userName;
    @Getter
    @Setter
    @RequestParameter
    private String userId;

    public List<FanboxPost> getItems() {
        if (items == null) return Collections.emptyList();
        return items.stream().map(jsonObject -> jsonObject.toJavaObject(FanboxPost.class)).collect(Collectors.toList());
    }

}
