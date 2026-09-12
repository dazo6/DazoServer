package com.dazo66.qqbot.controller;

import com.alibaba.fastjson.JSONObject;
import com.dazo66.qqbot.BotMessageDispatcher;
import com.dazo66.util.ResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * NapCat HTTP POST 上报入口。
 * NapCat 通过 OneBot v11 协议的 HTTP 上报方式将事件 JSON POST 到此端点。
 *
 * @author Dazo66
 */
@Slf4j
@RestController
@RequestMapping("/bot")
public class BotController {

    @Autowired
    private BotMessageDispatcher dispatcher;

    /**
     * 接收 NapCat 上报事件。NapCat 配置中的 post_url 应指向此端点。
     * 响应快速返回 200，消息分发在 Dispatcher 中同步完成。
     */
    @PostMapping("/napcat")
    public ResultEntity<String> onEvent(@RequestBody JSONObject body) {
        log.debug("收到 NapCat 上报: post_type={}", body.getString("post_type"));
        dispatcher.dispatch(body);
        return ResultEntity.successWithoutData();
    }


    @GetMapping("/hello")
    public ResultEntity<String> hello() {
        return ResultEntity.successWithData("world");
    }
}
