package com.dazo66.config.gecco;

import com.geccocrawler.gecco.pipeline.PipelineFactory;
import com.geccocrawler.gecco.spider.SpiderBean;
import com.geccocrawler.gecco.spider.SpiderBeanContext;
import com.geccocrawler.gecco.spider.SpiderBeanFactory;
import com.geccocrawler.gecco.spider.render.RenderFactory;
import com.geccocrawler.gecco.spider.render.RenderType;
import com.geccocrawler.gecco.spider.render.json.JsonRender;

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

    @Override
    public SpiderBeanContext getContext(Class<? extends SpiderBean> spider) {
        SpiderBeanContext context = super.getContext(spider);
        RenderFactory renderFactory = getRenderFactory();
        if (context.getRender() instanceof JsonRender) {
            context.setRender(renderFactory.getRender(RenderType.JSON));
        }
        return context;
    }
}
