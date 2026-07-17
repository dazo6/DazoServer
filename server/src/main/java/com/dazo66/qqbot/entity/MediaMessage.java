package com.dazo66.qqbot.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

/**
 * convert 阶段产出的消息体 —— 携带发送人信息及媒体 URL 列表，尚未下载。
 * handle 阶段负责下载文件并入库。
 *
 * @author Dazo66
 */
@Accessors(chain = true)
@Data
public class MediaMessage {

    /**
     * 0私聊 1群聊
     */
    private boolean isGroupChat;

    /**
     * QQ消息ID
     */
    private String messageId;

    /**
     * 群聊ID，私聊时为null
     */
    private String groupId;

    /**
     * 发送人QQ号
     */
    private String senderId;

    /**
     * 发送人昵称
     */
    private String senderName;

    /**
     * 消息发送时间
     */
    private Date messageTime;

    /**
     * 待下载的媒体 segment 列表
     */
    private List<MediaSegment> mediaSegments;
}
