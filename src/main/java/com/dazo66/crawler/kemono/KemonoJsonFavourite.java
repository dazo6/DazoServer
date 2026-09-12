package com.dazo66.crawler.kemono;


import com.alibaba.fastjson.annotation.JSONField;
import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.annotation.JSONPath;
import com.geccocrawler.gecco.spider.JsonBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

import static com.dazo66.crawler.kemono.KemonoContents.HTTPS_HOST_PATTERN;

@EqualsAndHashCode
@Gecco(matchUrl = HTTPS_HOST_PATTERN + "api/v1/account/favorites?type=artist", pipelines = {
        "consolePipeline", "kemonoJsonFavouritePipeline"}, downloader = "kemonoDownloader")
@Data
public class KemonoJsonFavourite implements JsonBean {


    @JSONPath("$")
    private List<FanboxCreator> artists;

/*    @JSONField()
    public List<FanboxCreator> getArtists() {
        return JsonUtils.string2Object(JsonUtils.object2JsonString(artistsJson), new TypeReference<List<FanboxCreator>>(){});
    }*/

    @Data
    public static class FanboxCreator {
        private String id;

        private String name;

        private String service;

        @JSONField(name = "indexed", format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
        private LocalDateTime indexed;

        @JSONField(name = "updated", format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
        private LocalDateTime updated;

        @JSONField(name = "public_id")
        private String publicId;

        @JSONField(name = "relation_id")
        private Long relationId;

        @JSONField(name = "faved_seq")
        private Long favedSeq;

        @JSONField(name = "last_imported", format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
        private LocalDateTime lastImported;
    }

}
