package com.dazo66.crawler.kemono.pipeline;

import com.dazo66.config.KemonoProperties;
import com.dazo66.crawler.kemono.KemonoJsonUser;
import com.dazo66.crawler.kemono.KemonoNextPageJsonUser;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.geccocrawler.gecco.pipeline.Pipeline;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Dazo66
 */
@Service
@Slf4j
public class KemonoPostJsonPipeline implements Pipeline<KemonoJsonUser> {

    @Autowired
    private CrawlerRequestService crawlerRequestService;

    @Autowired
    private KemonoArtistService kemonoArtistService;
    @Autowired
    private KemonoProperties kemonoProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(KemonoJsonUser bean) {
        String nextPage;
        if (bean instanceof KemonoNextPageJsonUser) {
            log.info("开始爬取作者：{} 偏移量：{}", bean.getArtistId(), ((KemonoNextPageJsonUser) bean).getOffset());
            Integer offset = ((KemonoNextPageJsonUser) bean).getOffset();
            nextPage = kemonoProperties.artistPagePath(bean.getType(), bean.getArtistId(), offset + 50);
        } else {
            log.info("开始爬取作者：{} 偏移量：{}", bean.getArtistId(), 0);
            nextPage = kemonoProperties.artistPagePath(bean.getType(), bean.getArtistId(), 50);
        }

        if (!StringUtils.isEmpty(nextPage)) {
            if (crawlerRequestService.check(nextPage)) {
                crawlerRequestService.add(nextPage);
            }
        }
        List<KemonoJsonUser.Result> results = bean.getResults();
        boolean hasUpdate = false;
        for (KemonoJsonUser.Result post : results) {
            String url = kemonoProperties.postPath(post.getService(), post.getUser(), post.getId());
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
