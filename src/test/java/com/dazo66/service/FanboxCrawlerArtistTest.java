package com.dazo66.service;
import com.alibaba.fastjson.JSONObject;
import com.dazo66.crawler.fanbox.FanboxPostList;
import com.dazo66.crawler.fanbox.FanboxPostDetail;
import com.dazo66.crawler.fanbox.pipeline.FanboxPostListPipeline;
import com.dazo66.crawler.fanbox.pipeline.FanboxPostDetailPipeline;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.entity.CrawlerRequest;
import com.geccocrawler.gecco.request.HttpGetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class FanboxCrawlerArtistTest {
    @Test void listRecordsKnownArtistForBothNewAndExistingPostUrls() {
        CrawlerRequestService requests = mock(CrawlerRequestService.class);
        KemonoArtistService artists = mock(KemonoArtistService.class);
        FanboxPostListPipeline pipeline = new FanboxPostListPipeline();
        ReflectionTestUtils.setField(pipeline, "crawlerRequestService", requests);
        ReflectionTestUtils.setField(pipeline, "artistService", artists);
        when(artists.getArtistId("creator", ArtistDomainEnum.FANBOX)).thenReturn(new Artist().setId(901));
        JSONObject item = new JSONObject(); item.put("id", "9"); item.put("creatorId", "creator");
        FanboxPostList bean = new FanboxPostList(); bean.setItems(Collections.singletonList(item));
        String url = "https://api.fanbox.cc/post.info?postId=9";
        when(requests.check(url)).thenReturn(true);
        pipeline.process(bean);
        verify(requests).add(url, 901);
        when(requests.check(url)).thenReturn(false);
        pipeline.process(bean);
        org.mockito.ArgumentCaptor<CrawlerRequest> captor = org.mockito.ArgumentCaptor.forClass(CrawlerRequest.class);
        verify(requests).updateByUrl(captor.capture());
        assertThat(captor.getValue().getArtistId()).isEqualTo(901);
        assertThat(captor.getValue().getIsDone()).isNull();
    }
    @Test void unknownListCreatorRemainsNull() {
        CrawlerRequestService requests = mock(CrawlerRequestService.class);
        FanboxPostListPipeline pipeline = new FanboxPostListPipeline();
        ReflectionTestUtils.setField(pipeline, "crawlerRequestService", requests);
        ReflectionTestUtils.setField(pipeline, "artistService", mock(KemonoArtistService.class));
        JSONObject item = new JSONObject(); item.put("id", "9");
        FanboxPostList bean = new FanboxPostList(); bean.setItems(Collections.singletonList(item));
        when(requests.check(any())).thenReturn(true);
        pipeline.process(bean);
        verify(requests).add("https://api.fanbox.cc/post.info?postId=9", null);
    }
    @Test void restrictedDetailRecordsOwnershipWithoutDownloading() {
        CrawlerRequestService requests = mock(CrawlerRequestService.class);
        KemonoArtistService artists = mock(KemonoArtistService.class);
        FanboxPostDetailPipeline pipeline = new FanboxPostDetailPipeline();
        ReflectionTestUtils.setField(pipeline, "crawlerRequestService", requests);
        ReflectionTestUtils.setField(pipeline, "kemonoArtistService", artists);
        when(artists.getArtistId("creator", ArtistDomainEnum.FANBOX)).thenReturn(new Artist().setId(901));
        FanboxPostDetail bean = mock(FanboxPostDetail.class);
        when(bean.hasPost()).thenReturn(true); when(bean.getIsRestricted()).thenReturn(true);
        when(bean.getArtistId()).thenReturn("creator"); when(bean.getType()).thenReturn("image");
        when(bean.getTitle()).thenReturn("Title");
        when(bean.getRequest()).thenReturn(new HttpGetRequest("https://api.fanbox.cc/post.info?postId=9"));
        pipeline.process(bean);
        org.mockito.ArgumentCaptor<CrawlerRequest> captor = org.mockito.ArgumentCaptor.forClass(CrawlerRequest.class);
        verify(requests).updateByUrl(captor.capture());
        assertThat(captor.getValue().getArtistId()).isEqualTo(901);
        assertThat(captor.getValue().getIsDone()).isNull();
        verify(bean, never()).getDownloadObject();
    }
}
