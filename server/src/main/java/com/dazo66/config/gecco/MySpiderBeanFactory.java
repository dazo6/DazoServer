package com.dazo66.config.gecco;

import com.geccocrawler.gecco.pipeline.PipelineFactory;
import com.geccocrawler.gecco.spider.SpiderBeanFactory;
import com.geccocrawler.gecco.spider.render.RenderFactory;

/**
 * @author dazo
 */
public class MySpiderBeanFactory extends SpiderBeanFactory {

    private volatile RenderFactory renderFactory;

    public MySpiderBeanFactory(String classPath, PipelineFactory pipelineFactory) {
        super(classPath, pipelineFactory);
    }


    @Override
    public RenderFactory getRenderFactory() {
        if (renderFactory == null) {
            synchronized (this) {
                if (renderFactory == null) {
                    renderFactory = new MyRenderFactory(getReflections());
                }
            }
        }
        return renderFactory;
    }
}
