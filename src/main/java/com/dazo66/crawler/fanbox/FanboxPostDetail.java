package com.dazo66.crawler.fanbox;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.Request;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/6 0:01
 **/
@Gecco(matchUrl = "https://api.fanbox.cc/post.info?postId={postId}", pipelines = {"fanboxPostDetailPipeline"}, downloader = "ja3ProxyHttpDownloader")
@Slf4j
public class FanboxPostDetail implements JsonBean {

    public static final String IMAGE = "image";
    public static final String ARTICLE = "article";


    public static final String FILE = "file";
    @Getter
    @Setter
    @Request
    private HttpGetRequest request;

    @Getter
    @Setter
    @RequestParameter
    private String postId;

    @Setter
    @JSONPath("$.body")
    private JSONObject body;

    @Getter
    @Setter
    @JSONPath("$.body.imageForShare")
    private String imageForShare;

    @Getter
    @Setter
    @JSONPath("$.body.publishedDatetime")
    private String publishedDatetime;
    @Getter
    @Setter
    @JSONPath("$.body.isRestricted")
    private Boolean isRestricted;


    @Getter
    @Setter
    @JSONPath("$.body.creatorId")
    private String artistId;


    @Getter
    @Setter
    @JSONPath("$.body.title")
    private String title;


    @Getter
    @Setter
    @JSONPath("$.body.type")
    private String type;


    public List<? extends DownloadObject> getDownloadObject() {
        if (body.getJSONObject("body") != null) {
            if (type.equals(IMAGE)) {
                return body.getJSONObject("body").getJSONArray("images").toJavaList(Image.class);
            } else if (type.equals(ARTICLE)) {
                return body.getJSONObject("body").getJSONObject("imageMap").values().stream().map(o -> JSONObject.toJavaObject((JSON) o, Image.class)).collect(Collectors.toList());
            } else if (type.equals(FILE)) {
                return body.getJSONObject("body").getJSONArray("files").toJavaList(File.class);
            } else {
                log.error("unknow fanbox post type: {}, {}", type, JSON.toJSONString(body));
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }


    @Data
    public static class Body {
        private String text;
        private List<Image> images;
        private Map<String, Image> imageMap;
        // getters and setters
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Image extends DownloadObject {
        private int width;
        private int height;
        @JSONField(name = "originalUrl")
        private String url;
        private String thumbnailUrl;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class File extends DownloadObject {
        private String url;
        private String thumbnailUrl;
        private Integer size;
    }

    @Data
    public static abstract class DownloadObject {
        private String id;
        private String extension;

        public abstract String getUrl();


    }

}
