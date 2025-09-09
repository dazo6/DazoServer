package com.dazo66.crawler.fanbox.pipeline;

import com.dazo66.crawler.fanbox.FanboxPageinate;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.util.FanboxHelper;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.scheduler.DeriveSchedulerContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:49
 **/
@Service
@Slf4j
public class FanboxPaginatePipeline implements Pipeline<FanboxPageinate> {
    @Value("${fanbox.save.cookie}")
    private String fanboxsessid;
    @Autowired
    private CrawlerRequestService crawlerRequestService;

    @SneakyThrows
    @Override
    public void process(FanboxPageinate bean) {

        for (String item : bean.getUrls()) {
            UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(item).build();
            List<String> maxId = uriComponents.getQueryParams().get("firstId");
            if (maxId != null && !maxId.isEmpty()) {
                List<CrawlerRequest> crawlerRequests = crawlerRequestService.getLambdaQuery()
                        .like(CrawlerRequest::getUrl, "post.info?postId=" + maxId.get(0)).list();
                if (crawlerRequests.isEmpty()) {
                    DeriveSchedulerContext.into(FanboxHelper.getHttpRequest(item, fanboxsessid));
                }
            } else {
                log.error("fanbox 分页 maxId 为空 " + item);
            }
        }
    }
}
