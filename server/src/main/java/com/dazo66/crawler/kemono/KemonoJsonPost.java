package com.dazo66.crawler.kemono;

import com.alibaba.fastjson.JSONObject;
import com.dazo66.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.annotation.Request;
import com.geccocrawler.gecco.annotation.RequestParameter;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Data;

import java.util.List;

/**
 * @author Dazo66
 */
@Gecco(matchUrl = "https://kemono.su/api/v1/{type}/user/{user}/post/{postId}", pipelines = {
        "consolePipeline", "kemonoImageJsonPipeline"}, downloader = "myHttpDownloader")
@Data
public class KemonoJsonPost implements JsonBean {

    @Request
    private HttpGetRequest request;
    @RequestParameter("type")
    private String type;
    @RequestParameter("user")
    private String artistId;
    @RequestParameter("postId")
    private String postId;

    @JsonProperty("post")
    @JSONPath("$.post")
    private JSONObject postJsonObject;

    @JSONPath("$.attachments")
    @JsonProperty("attachments")
    private List<JSONObject> attachments;

    @JSONPath("$.previews")
    @JsonProperty("previews")
    private List<JSONObject> previews;

    @JSONPath("$.videos")
    @JsonProperty("videos")
    private List<JSONObject> videos;

    @JSONPath("$.props")
    @JsonProperty("props")
    private JSONObject propsJsonObject;

    public Post getPost() {
        return JsonUtils.jsonObject2Bean(postJsonObject, Post.class);
    }

    public Props getProps() {
        return JsonUtils.jsonObject2Bean(propsJsonObject, Props.class);
    }

    public List<Attachment> getAttachments() {
        return JsonUtils.jsonObjectArray2Bean(attachments, Attachment.class);
    }

    public List<Preview> getPreviews() {
        return JsonUtils.jsonObjectArray2Bean(previews, Preview.class);
    }

    public List<Video> getVideos() {
        return JsonUtils.jsonObjectArray2Bean(videos, Video.class);
    }

    @Data
    public static class Post {
        @JsonProperty("id")
        private String id;

        @JsonProperty("user")
        private String user;

        @JsonProperty("service")
        private String service;

        @JsonProperty("title")
        private String title;

        @JsonProperty("content")
        private String content;

        @JsonProperty("shared_file")
        private Boolean sharedFile;

        @JsonProperty("added")
        private String added;

        @JsonProperty("published")
        private String published;

        @JsonProperty("edited")
        private String edited;

        @JsonProperty("file")
        private FileItem file;

        @JsonProperty("attachments")
        private List<AttachmentItem> attachments;


        @JsonProperty("captions")
        private Object captions;

        @JsonProperty("tags")
        private List<String> tags;

        @JsonProperty("next")
        private String next;

        @JsonProperty("prev")
        private String prev;
    }

    @Data
    public static class FileItem {
        @JsonProperty("name")
        private String name;

        @JsonProperty("path")
        private String path;
    }

    @Data
    public static class AttachmentItem {
        @JsonProperty("name")
        private String name;

        @JsonProperty("path")
        private String path;
    }

    @Data
    public static class Attachment {
        @JsonProperty("server")
        private String server;

        @JsonProperty("name")
        private String name;

        @JsonProperty("extension")
        private String extension;

        @JsonProperty("name_extension")
        private String nameExtension;

        @JsonProperty("stem")
        private String stem;

        @JsonProperty("path")
        private String path;
    }

    @Data
    public static class Preview {
        @JsonProperty("type")
        private String type;

        @JsonProperty("server")
        private String server;

        @JsonProperty("name")
        private String name;

        @JsonProperty("path")
        private String path;
    }

    @Data
    public static class Video {
        @JsonProperty("index")
        private Integer index;

        @JsonProperty("path")
        private String path;

        @JsonProperty("name")
        private String name;

        @JsonProperty("extension")
        private String extension;

        @JsonProperty("name_extension")
        private String nameExtension;

        @JsonProperty("server")
        private String server;
    }

    @Data
    public static class Props {
        @JsonProperty("flagged")
        private Integer flagged;

        @JsonProperty("revisions")
        private List<List<Object>> revisions; // 根据实际需求可定义具体类型

        // 若需要结构化处理revisions中的嵌套Post：
        // @Data
        // public static class Revision {
        //     private Integer index;
        //     private Post post;
        // }
    }

}
