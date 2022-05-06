package com.dazo66.service;

import com.dazo66.crawler.FanboxNextPageUser;
import com.dazo66.crawler.FanboxUser;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.entity.FanboxArtist;
import com.geccocrawler.gecco.pipeline.Pipeline;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;


/**
 * @author Dazo66
 */
@Service
@Slf4j
public class FanboxPostPipeline implements Pipeline<FanboxUser> {

	@Autowired
	private CrawlerRequestService crawlerRequestService;

	@Autowired
	private FanboxArtistService fanboxArtistService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void process(FanboxUser bean) {
		if (bean instanceof FanboxNextPageUser) {
			log.info("开始爬取作者：{} 偏移量：{}", bean.getUser(), ((FanboxNextPageUser) bean).getOffset());
		} else {
			log.info("开始爬取作者：{} 偏移量：{}", bean.getUser(), 0);
		}
		String nextPage = bean.getNextPage();
		if (!StringUtils.isEmpty(nextPage)) {
			if (crawlerRequestService.check(nextPage)) {
				crawlerRequestService.add(nextPage);
			}
		}
		Object[] posts = bean.getPosts();
		for (Object post : posts) {
			String url = post.toString();
			if (crawlerRequestService.check(url)) {
				fanboxArtistService.updateByArtistId(new FanboxArtist().setArtistId(bean.getUser()).setLastUpdate(new Date()));
				crawlerRequestService.add(url);
			}
		}
		crawlerRequestService.updateByUrl(new CrawlerRequest().setUrl(bean.getRequest().getUrl()).setIsDone(true));
	}
}
