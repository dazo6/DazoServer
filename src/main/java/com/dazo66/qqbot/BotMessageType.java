package com.dazo66.qqbot;

import com.alibaba.fastjson.JSONObject;

/**
 * 消息类型路由标记，封装 post_type + 子类型（message_type / notice_type / request_type / meta_event_type）。
 * 路由 key 格式: "post_type/sub_type"，例如 "message/group"、"notice/friend_add"。
 *
 * @author Dazo66
 */
public class BotMessageType {

    private final String postType;
    private final String subType;
    private final String routeKey;

    private BotMessageType(String postType, String subType) {
        this.postType = postType;
        this.subType = subType;
        this.routeKey = postType + "/" + subType;
    }

    /**
     * 从 NapCat 上报的 JSON 中解析消息类型。
     * 子类型字段优先级: message_type > notice_type > request_type > meta_event_type
     */
    public static BotMessageType from(JSONObject json) {
        String postType = json.getString("post_type");
        if (postType == null) {
            return null;
        }
        String subType = null;
        switch (postType) {
            case "message":
                subType = json.getString("message_type");
                break;
            case "notice":
                subType = json.getString("notice_type");
                break;
            case "request":
                subType = json.getString("request_type");
                break;
            case "meta_event":
                subType = json.getString("meta_event_type");
                break;
        }
        return new BotMessageType(postType, subType != null ? subType : "unknown");
    }

    public String getPostType() {
        return postType;
    }

    public String getSubType() {
        return subType;
    }

    /**
     * 路由 key，格式: "message/group"
     */
    public String getRouteKey() {
        return routeKey;
    }

    @Override
    public String toString() {
        return routeKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BotMessageType)) return false;
        BotMessageType that = (BotMessageType) o;
        return routeKey.equals(that.routeKey);
    }

    @Override
    public int hashCode() {
        return routeKey.hashCode();
    }
}
