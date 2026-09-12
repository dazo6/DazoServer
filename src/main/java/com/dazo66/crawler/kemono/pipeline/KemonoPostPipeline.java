package com.dazo66.crawler.kemono.pipeline;

import com.dazo66.config.KemonoProperties;
import com.dazo66.crawler.kemono.KemonoNextPageUser;
import com.dazo66.crawler.kemono.kemonoUser;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.geccocrawler.gecco.pipeline.Pipeline;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * @author Dazo66
 */
@Service
@Slf4j
public class KemonoPostPipeline implements Pipeline<kemonoUser> {

    @Autowired
    private CrawlerRequestService crawlerRequestService;

    @Autowired
    private KemonoArtistService kemonoArtistService;
    @Autowired
    private KemonoProperties kemonoProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(kemonoUser bean) {
        if (bean instanceof KemonoNextPageUser) {
            log.info("开始爬取作者：{} 偏移量：{}", bean.getArtistId(),
                    ((KemonoNextPageUser) bean).getOffset());
        } else {
            log.info("开始爬取作者：{} 偏移量：{}", bean.getArtistId(), 0);
        }
        String nextPage = StringUtils.isEmpty(bean.getNextPage()) ? null
                : kemonoProperties.relativePath(bean.getNextPage());
        if (!StringUtils.isEmpty(nextPage)) {
            if (crawlerRequestService.check(nextPage)) {
                crawlerRequestService.add(nextPage);
            }
        }
        Object[] posts = bean.getPosts() == null ? new Object[0] : bean.getPosts();
        boolean hasUpdate = false;
        for (Object post : posts) {
            String url = kemonoProperties.relativePath(post.toString());
            if (crawlerRequestService.check(url)) {
                hasUpdate = true;
                crawlerRequestService.add(url);
            }
        }
        if (hasUpdate && !StringUtils.isEmpty(nextPage)) {
            crawlerRequestService.updateByUrl(new CrawlerRequest().setUrl(nextPage).setIsDone(false));
        }
        crawlerRequestService.updateByUrl(new CrawlerRequest()
                .setUrl(kemonoProperties.relativePath(bean.getRequest().getUrl())).setIsDone(true));
    }
}
