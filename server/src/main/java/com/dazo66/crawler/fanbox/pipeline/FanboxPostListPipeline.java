package com.dazo66.crawler.fanbox.pipeline;

import com.dazo66.crawler.fanbox.FanboxPost;
import com.dazo66.crawler.fanbox.FanboxPostList;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.util.FanboxHelper;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.scheduler.DeriveSchedulerContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:49
 **/
@Service
@Slf4j
public class FanboxPostListPipeline implements Pipeline<FanboxPostList> {

    @Autowired
    private CrawlerRequestService crawlerRequestService;
    @Value("${fanbox.save.cookie}")
    private String fanboxsessid;

    @SneakyThrows
    @Override
    public void process(FanboxPostList bean) {

        boolean isUpdate = false;
        for (FanboxPost item : bean.getItems()) {
            String url = "https://api.fanbox.cc/post.info?postId=" + item.getId();
            if (crawlerRequestService.check(url)) {
                crawlerRequestService.add(url);
                isUpdate = true;
                log.info("add url {}", url);
            }
        }
        if (!StringUtils.isEmpty(bean.getNextUrl())) {
            URIBuilder uriBuilder = new URIBuilder(bean.getNextUrl());
            Map<String, String> queryMap = uriBuilder.getQueryParams().stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
            String maxId = queryMap.get("maxId");
            if (maxId != null && isUpdate) {
                // 下一页存在更新 需要加到队列里去
                DeriveSchedulerContext.into(FanboxHelper.getHttpRequest(bean.getNextUrl(), fanboxsessid));
            }
        }
    }
}
