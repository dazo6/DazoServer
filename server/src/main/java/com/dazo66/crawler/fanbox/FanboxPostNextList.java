package com.dazo66.crawler.fanbox;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.RequestParameter;
import lombok.Getter;
import lombok.Setter;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:42
 **/
@Gecco(matchUrl = "https://api.fanbox.cc/post.listCreator?creatorId={userName}&maxPublishedDatetime={maxPublishedDatetime}&maxId={maxId}&limit={limit}", pipelines = {
        "consolePipeline", "fanboxPostListPipeline"}, downloader = "myHttpDownloader")
public class FanboxPostNextList extends FanboxPostList {
    @Getter
    @Setter
    @RequestParameter
    private String maxId;
    @Getter
    @Setter
    @RequestParameter
    private String maxPublishedDatetime;
}
