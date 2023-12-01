package com.dazo66.crawler.fanbox;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Data;

import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/6 0:01
 **/
@Gecco(matchUrl = "https://api.fanbox.cc/post.info?postId={postId}", pipelines = {
        "consolePipeline"}, downloader = "myHttpDownloader")
@Data
public class FanboxPostDetail implements JsonBean {

    @RequestParameter
    private String postId;

    @JSONPath("$.body.body")
    private Body body;

    @JSONPath("$.body.publishedDatetime")
    private String publishedDatetime;

    @Data
    public static class Body {
        private String text;
        private List<Image> images;

        // getters and setters
    }

    @Data
    public static class Image {
        private String id;
        private String extension;
        private int width;
        private int height;
        private String originalUrl;
        private String thumbnailUrl;

    }

}
