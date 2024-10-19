package com.dazo66.crawler.fanbox.pipeline;

import com.dazo66.crawler.fanbox.FanboxPageinate;
import com.dazo66.util.FanboxHelper;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.scheduler.DeriveSchedulerContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:49
 **/
@Service
@Slf4j
public class FanboxPaginatePipeline implements Pipeline<FanboxPageinate> {
    @Value("${fanbox.save.cookie}")
    private String fanboxsessid;

    @SneakyThrows
    @Override
    public void process(FanboxPageinate bean) {

        for (String item : bean.getUrls()) {
            DeriveSchedulerContext.into(FanboxHelper.getHttpRequest(item, fanboxsessid));
        }
    }
}
