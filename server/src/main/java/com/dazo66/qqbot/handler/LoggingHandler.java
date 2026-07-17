package com.dazo66.qqbot.handler;

import com.alibaba.fastjson.JSONObject;
import com.dazo66.qqbot.BotMessageHandler;
import com.dazo66.qqbot.BotMessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 示例 Handler —— 接收所有类型的消息并记录日志。
 * 可用于调试阶段验证 NapCat 上报是否正常到达。
 *
 * @author Dazo66
 */
@Slf4j
@Component
public class LoggingHandler implements BotMessageHandler<String> {

    @Override
    public boolean accept(BotMessageType type) {
        // 接收所有消息类型，包括 meta_event 心跳
        return true;
    }

    @Override
    public String convert(JSONObject message) {
        BotMessageType type = BotMessageType.from(message);
        String selfId = message.getString("self_id");
        String time = message.getString("time");
        String msgId = message.getString("message_id");

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type).append("]");
        sb.append(" self=").append(selfId);

        if ("message".equals(type.getPostType())) {
            String userId = message.getString("user_id");
            String rawMsg = message.getString("raw_message");
            sb.append(" user=").append(userId);
            sb.append(" msgId=").append(msgId);
            sb.append(" raw=").append(rawMsg);
        } else if ("notice".equals(type.getPostType())) {
            String userId = message.getString("user_id");
            String groupId = message.getString("group_id");
            sb.append(" user=").append(userId);
            sb.append(" group=").append(groupId);
        }

        return sb.toString();
    }

    @Override
    public void handle(String logEntry) {
        log.info("QQBot 事件: {}", logEntry);
    }
}
