package com.dazo66.qqbot;

import com.alibaba.fastjson.JSONObject;

/**
 * QQBot 消息处理器接口。
 * <p>
 * 处理流程: accept(BotMessageType) → convert(JSONObject) → handle(T)
 * 只有当 accept 返回 true 时才会执行后续的转换和处理。
 *
 * @param <T> 转换后的消息实体类型，由 convert 方法决定
 * @author Dazo66
 */
public interface BotMessageHandler<T> {

    /**
     * 判断是否接受并处理该类型的消息。
     *
     * @param type 从上报 JSON 中解析出的消息类型
     * @return true 表示该 Handler 愿意处理此消息
     */
    boolean accept(BotMessageType type);

    /**
     * 将原始 JSON 消息转换为 Handler 需要的强类型 POJO。
     * 如果转换失败（例如消息体不完整），可返回 null，Dispatcher 将跳过该 Handler。
     *
     * @param message NapCat 上报的原始 JSON
     * @return 转换后的实体对象，或 null 表示转换失败
     */
    T convert(JSONObject message);

    /**
     * 处理已转换的消息。此方法在 convert 返回非 null 值后调用。
     *
     * @param message 由 convert 转换后的强类型消息对象
     */
    void handle(T message);
}
