package com.dazo66.service;

import com.dazo66.crawler.FanboxUser;
import com.dazo66.entity.FanboxArtist;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.scheduler.DeriveSchedulerContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;


/**
 * @author Dazo66
 */
@Service
public class FanboxPostPipeline implements Pipeline<FanboxUser> {

	@Autowired
	private CrawlerRequestService crawlerRequestService;

	@Autowired
	private FanboxArtistService fanboxArtistService;

	@Override
	public void process(FanboxUser bean) {
		String nextPage = bean.getNextPage();
		if (!StringUtils.isEmpty(nextPage)) {
			if (crawlerRequestService.check(nextPage)) {
				DeriveSchedulerContext.into(new HttpGetRequest(nextPage));
				crawlerRequestService.add(nextPage);
			}
		}
		Object[] posts = bean.getPosts();
		for (Object post : posts) {
			String url = post.toString();
			if (crawlerRequestService.check(url)) {
				DeriveSchedulerContext.into(new HttpGetRequest(url));
				fanboxArtistService.updateByArtistId(new FanboxArtist().setArtistId(bean.getUser()).setLastUpdate(new Date()));
				crawlerRequestService.add(url);
			}
		}
	}
}
