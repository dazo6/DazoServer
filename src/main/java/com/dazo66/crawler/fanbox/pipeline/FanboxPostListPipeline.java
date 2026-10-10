package com.dazo66.crawler.fanbox.pipeline;

import com.dazo66.crawler.fanbox.FanboxPost;
import com.dazo66.crawler.fanbox.FanboxPostList;
import com.dazo66.service.CrawlerRequestService;
import com.geccocrawler.gecco.pipeline.Pipeline;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:49
 **/
@Service
@Slf4j
public class FanboxPostListPipeline implements Pipeline<FanboxPostList> {

    @Autowired
    private CrawlerRequestService crawlerRequestService;
    @Autowired
    private com.dazo66.service.KemonoArtistService artistService;
    @Value("${fanbox.save.cookie}")
    private String fanboxsessid;

    @SneakyThrows
    @Override
    public void process(FanboxPostList bean) {

        boolean isUpdate = false;
        for (FanboxPost item : bean.getItems()) {
            String url = "https://api.fanbox.cc/post.info?postId=" + item.getId();
            String creatorId = org.apache.commons.lang3.StringUtils.isNotBlank(item.getCreatorId())
                    ? item.getCreatorId() : bean.getUserName();
            com.dazo66.entity.Artist artist = org.apache.commons.lang3.StringUtils.isBlank(creatorId)
                    ? null : artistService.getArtistId(creatorId, com.dazo66.entity.ArtistDomainEnum.FANBOX);
            if (crawlerRequestService.check(url)) {
                crawlerRequestService.add(url, artist == null ? null : artist.getId());
                isUpdate = true;
                log.info("add url {}", url);
            } else if (artist != null) {
                crawlerRequestService.updateByUrl(new com.dazo66.entity.CrawlerRequest()
                        .setUrl(url).setArtistId(artist.getId()));
            }
        }
    }
}
