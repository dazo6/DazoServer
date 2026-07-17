package com.dazo66.qqbot;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息分发器 — 收集所有 BotMessageHandler Bean，按 accept → convert → handle 流程分发消息。
 * <p>
 * 分发逻辑：
 * <ol>
 *   <li>从 JSON 解析出 BotMessageType</li>
 *   <li>遍历所有 Handler，调用 accept() 判断是否匹配</li>
 *   <li>匹配则调用 convert() 转换消息体，转换成功则调用 handle() 处理</li>
 * </ol>
 * 单个 Handler 的异常不会影响其他 Handler 的执行。
 *
 * @author Dazo66
 */
@Slf4j
@Component
public class BotMessageDispatcher {

    @Autowired(required = false)
    private List<BotMessageHandler> handlers;

    /**
     * 分发一条 NapCat 上报消息到所有匹配的 Handler。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(JSONObject json) {
        log.info("收到消息：{}", json.toJSONString());
        BotMessageType type = BotMessageType.from(json);
        if (type == null) {
            log.warn("无法解析消息类型，跳过: {}", json);
            return;
        }

        if (handlers == null || handlers.isEmpty()) {
            log.debug("没有注册任何 BotMessageHandler，消息被丢弃: type={}", type);
            return;
        }

        for (BotMessageHandler handler : handlers) {
            try {
                if (handler.accept(type)) {
                    Object converted = handler.convert(json);
                    if (converted != null) {
                        handler.handle(converted);
                    }
                }
            } catch (Exception e) {
                log.error("Handler [{}] 处理消息异常: type={}", handler.getClass().getSimpleName(), type, e);
            }
        }
    }
}
