package com.dazo66.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.spider.SpiderBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author Dazo66
 */
@Service("consolePipeline")
@Slf4j
public class ConsolePipeline implements Pipeline<SpiderBean> {
    @Override
    public void process(SpiderBean bean) {
        log.info(JSON.toJSONString(bean, SerializerFeature.PrettyFormat));
    }
}