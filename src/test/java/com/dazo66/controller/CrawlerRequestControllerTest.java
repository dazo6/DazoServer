package com.dazo66.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.config.KemonoProperties;
import com.dazo66.dashboard.CrawlerRequestHistoryItem;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.util.ResultEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlerRequestControllerTest {

    @Test
    void expandsRelativeKemonoUrlsAndKeepsAbsoluteUrls() {
        CrawlerRequestService crawlerRequestService = mock(CrawlerRequestService.class);
        KemonoProperties properties = new KemonoProperties();
        properties.setBaseUrl("https://pawchive.pw/");
        Page<CrawlerRequest> source = Page.of(1, 20, 2);
        source.setRecords(Arrays.asList(
                new CrawlerRequest().setId(1).setUrl("/fanbox/user/42/post/9").setIsDone(true),
                new CrawlerRequest().setId(2).setUrl("https://api.fanbox.cc/post.info?postId=10")
                        .setIsDone(false)
        ));
        when(crawlerRequestService.getPage(any(Page.class), any(Wrapper.class))).thenReturn(source);
        CrawlerRequestController controller = new CrawlerRequestController(crawlerRequestService, properties);

        ResultEntity<Page<CrawlerRequestHistoryItem>> response =
                controller.getHistory(1, 20, null, null, null, null);

        assertThat(response.getData().getRecords())
                .extracting(CrawlerRequestHistoryItem::getUrl)
                .containsExactly(
                        "https://pawchive.pw/fanbox/user/42/post/9",
                        "https://api.fanbox.cc/post.info?postId=10"
                );
    }
}
