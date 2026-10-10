package com.dazo66.service;
import com.dazo66.config.KemonoProperties;
import com.dazo66.entity.Artist;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.mapper.FanboxArtistMapper;
import com.dazo66.repository.CrawlerRequestRepository;
import com.dazo66.service.impl.CrawlerRequestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CrawlerRequestArtistTest {
    private CrawlerRequestServiceImpl service;
    private FanboxArtistMapper artists;
    private CrawlerRequestRepository requests;
    @BeforeEach void setup() {
        service = new CrawlerRequestServiceImpl(); artists = mock(FanboxArtistMapper.class);
        requests = mock(CrawlerRequestRepository.class);
        ReflectionTestUtils.setField(service, "artistMapper", artists);
        ReflectionTestUtils.setField(service, "crawlerRequestRepository", requests);
        ReflectionTestUtils.setField(service, "kemonoProperties", new KemonoProperties());
    }
    @Test void addUsesArtistPrimaryKeyAndDomainAndPlatform() {
        when(artists.selectList(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Artist> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("artist_id", "domain", "type");
            assertThat(query.getParamNameValuePairs().values()).contains("42", "fanbox", com.dazo66.entity.ArtistDomainEnum.KEMONO);
            return Collections.singletonList(new Artist().setId(901).setArtistId("42"));
        });
        CrawlerRequest request = service.add("/fanbox/user/42/post/9");
        assertThat(request.getArtistId()).isEqualTo(901);
        assertThat(request.getIsDone()).isFalse();
        verify(requests).saveOrUpdate(request);
    }
    @Test void explicitFanboxOwnershipDoesNotNeedUrlInference() {
        assertThat(service.add("https://api.fanbox.cc/post.info?postId=9", 901).getArtistId()).isEqualTo(901);
        verifyNoInteractions(artists);
    }
    @Test void unknownOrAmbiguousOwnershipStaysNull() {
        assertThat(service.add("https://api.fanbox.cc/post.info?postId=42").getArtistId()).isNull();
        when(artists.selectList(any())).thenReturn(Collections.emptyList());
        assertThat(service.add("/patreon/user/42/post/9").getArtistId()).isNull();
        when(artists.selectList(any())).thenReturn(Arrays.asList(new Artist().setId(1), new Artist().setId(2)));
        assertThat(service.add("/patreon/user/42/post/9").getArtistId()).isNull();
    }
    @Test void updateAttachesOwnershipWithoutClearingExplicitId() {
        when(artists.selectList(any())).thenReturn(Collections.singletonList(new Artist().setId(901)));
        CrawlerRequest request = new CrawlerRequest().setUrl("/fanbox/user/42/post/9").setIsDone(true);
        service.updateByUrl(request);
        assertThat(request.getArtistId()).isEqualTo(901);
        CrawlerRequest explicit = new CrawlerRequest().setUrl("https://api.fanbox.cc/post.info?postId=9").setArtistId(902);
        service.updateByUrl(explicit);
        assertThat(explicit.getArtistId()).isEqualTo(902);
        verify(artists, times(1)).selectList(any());
    }
}
