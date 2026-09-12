package com.dazo66.test;

import com.dazo66.service.ConsolePipeline;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.pipeline.PipelineFactory;
import com.geccocrawler.gecco.spider.SpiderBean;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 21:40
 **/
public class TestPipelineFactory implements PipelineFactory {
    @Override
    public Pipeline<? extends SpiderBean> getPipeline(String name) {
        return new ConsolePipeline();
    }
}
