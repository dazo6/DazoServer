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

    private JSONObject post() {
        return body == null ? null : body.getJSONObject("post") != null
                ? body.getJSONObject("post") : body;
    }

    public boolean hasPost() {
        return post() != null;
    }

    public String getImageForShare() {
        JSONObject post = post();
        if (post == null) return null;
        String image = post.getString("imageForShare");
        return image == null ? post.getString("coverImageUrl") : image;
    }

    public String getPublishedDatetime() {
        JSONObject post = post();
        return post == null ? null : post.getString("publishedDatetime");
    }

    public Boolean getIsRestricted() {
        JSONObject post = post();
        return post == null ? null : post.getBoolean("isRestricted");
    }

    public String getArtistId() {
        JSONObject post = post();
        return post == null ? null : post.getString("creatorId");
    }

    public String getTitle() {
        JSONObject post = post();
        return post == null ? null : post.getString("title");
    }

    public String getType() {
        JSONObject post = post();
        return post == null ? null : post.getString("type");
    }


    public List<? extends DownloadObject> getDownloadObject() {
        JSONObject post = post();
        JSONObject content = post == null ? null : post.getJSONObject("body");
        if (content == null) return Collections.emptyList();
        String type = getType();
        if (IMAGE.equals(type)) {
            return content.getJSONArray("images") == null ? Collections.emptyList()
                    : content.getJSONArray("images").toJavaList(Image.class);
        } else if (ARTICLE.equals(type)) {
            JSONObject imageMap = content.getJSONObject("imageMap");
            return imageMap == null ? Collections.emptyList() : imageMap.values().stream()
                    .map(o -> JSONObject.toJavaObject((JSON) o, Image.class)).collect(Collectors.toList());
        } else if (FILE.equals(type)) {
            return content.getJSONArray("files") == null ? Collections.emptyList()
                    : content.getJSONArray("files").toJavaList(File.class);
        }
        log.warn("未知的 Fanbox 作品类型: postId={}, type={}", postId, type);
        return Collections.emptyList();
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
