package com.dazo66.crawler.fanbox;

import lombok.Data;

import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:55
 **/
@Data
public class FanboxPost {


        private String id;
        private String title;
        private int feeRequired;
        private String publishedDatetime;
        private String updatedDatetime;
        private List<String> tags;
        private boolean isLiked;
        private int likeCount;
        private int commentCount;
        private boolean isRestricted;
        private User user;
        private String creatorId;
        private boolean hasAdultContent;
        private Cover cover;
        private String excerpt;
        @Data
        public static class User {
            private String userId;
            private String name;
            private String iconUrl;
        }
        @Data
        public static class Cover {
            private String type;
            private String url;

        }

}
