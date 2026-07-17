package com.dazo66.qqbot.handler;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dazo66.qqbot.BotMessageHandler;
import com.dazo66.qqbot.BotMessageType;
import com.dazo66.qqbot.entity.MediaRecord;
import com.dazo66.qqbot.mapper.MediaRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 消息撤回 Handler —— 监听撤回通知，将对应媒体记录标记为 RECALLED。
 *
 * @author Dazo66
 */
@Slf4j
@Component
public class MessageRecallHandler implements BotMessageHandler<String> {

    @Autowired
    private MediaRecordMapper mediaRecordMapper;

    @Override
    public boolean accept(BotMessageType type) {
        return "notice/group_recall".equals(type.toString())
                || "notice/friend_recall".equals(type.toString());
    }

    @Override
    public String convert(JSONObject json) {
        // 提取被撤回消息的 message_id
        Long msgIdLong = json.getLong("message_id");
        if (msgIdLong == null) {
            return null;
        }
        return String.valueOf(msgIdLong);
    }

    @Override
    public void handle(String messageId) {
        log.info("消息 {} 监听到撤回", messageId);
        int updated = mediaRecordMapper.update(null,
                new LambdaUpdateWrapper<MediaRecord>()
                        .eq(MediaRecord::getMessageId, messageId)
                        .eq(MediaRecord::getStatus, MediaRecord.STATUS_NORMAL)
                        .set(MediaRecord::getStatus, MediaRecord.STATUS_RECALLED)
                        .set(MediaRecord::getUpdateTime, new Date()));

        if (updated > 0) {
            log.info("消息 {} 已标记为撤回", messageId);
        }
    }
}
