package com.dazo66.qqbot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dazo66.entity.CreateTableSql;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * QQBot 媒体消息记录表。
 * status: NORMAL（正常）→ RECALLED（已撤回）→ DELETED（文件已清理）
 *
 * @author Dazo66
 */
@Accessors(chain = true)
@Data
@TableName("t_qqbot_media_record")
@CreateTableSql("CREATE TABLE t_qqbot_media_record (\n" +
        "    id            INT AUTO_INCREMENT PRIMARY KEY,\n" +
        "    is_group_chat TINYINT      NOT NULL COMMENT '0私聊 1群聊',\n" +
        "    message_id    VARCHAR(64)  NOT NULL COMMENT 'QQ消息ID',\n" +
        "    group_id      VARCHAR(64)  NULL     COMMENT '群聊ID，私聊为空',\n" +
        "    sender_id     VARCHAR(64)  NOT NULL COMMENT '发送人QQ号',\n" +
        "    sender_name   VARCHAR(256) NOT NULL COMMENT '发送人昵称',\n" +
        "    message_time  DATETIME     NOT NULL COMMENT '消息发送时间',\n" +
        "    status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/RECALLED/DELETED',\n" +
        "    create_time   DATETIME     NOT NULL COMMENT '记录创建时间',\n" +
        "    update_time   DATETIME     NULL     COMMENT '更新时间',\n" +
        "    INDEX idx_message_id (message_id),\n" +
        "    INDEX idx_status (status)\n" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='QQBot媒体消息记录'")
public class MediaRecord {

    // ---- 状态常量 ----
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_RECALLED = "RECALLED";
    public static final String STATUS_DELETED = "DELETED";

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 0私聊 1群聊
     */
    private Boolean isGroupChat;

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
     * NORMAL / RECALLED / DELETED
     */
    private String status;

    private Date createTime;
    private Date updateTime;

}
