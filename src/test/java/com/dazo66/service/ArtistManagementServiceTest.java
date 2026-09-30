package com.dazo66.service;

import com.dazo66.config.KemonoProperties;
import com.dazo66.dashboard.ArtistCreateRequest;
import com.dazo66.dashboard.ArtistManagementItem;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistManagementServiceTest {

    private ArtistManagementService service;

    @BeforeEach
    void setUp() {
        KemonoProperties properties = new KemonoProperties();
        properties.setBaseUrl("https://pawchive.pw/");
        service = new ArtistManagementService();
        ReflectionTestUtils.setField(service, "kemonoProperties", properties);
        ReflectionTestUtils.setField(service, "downloadPath", "downloads");
    }

    @Test
    void parsesFanboxSubdomainAndBuildsPublicLinkOnServer() {
        ArtistCreateRequest request = request("https://takk.fanbox.cc/posts/123", null, false);

        Artist artist = service.parse(request);
        artist.setIcon("artist-7-icon-0123456789abcdef0123456789abcdef.jpg");
        ArtistManagementItem item = service.toItem(artist);

        assertThat(artist.getArtistId()).isEqualTo("takk");
        assertThat(artist.getDomain()).isEqualTo(ArtistDomainEnum.FANBOX);
        assertThat(artist.getType()).isEqualTo("fanbox");
        assertThat(artist.getCompression()).isFalse();
        assertThat(item.getName()).isEqualTo("takk");
        assertThat(item.getIcon()).isEqualTo(
                "/api/fanbox/artist-image/artist-7-icon-0123456789abcdef0123456789abcdef.jpg");
        assertThat(item.getUrl()).isEqualTo("https://www.fanbox.cc/@takk");
        assertThat(item.getFolderPath()).isEqualTo(
                java.nio.file.Paths.get("downloads", "takk").toAbsolutePath().normalize().toString());
    }

    @Test
    void parsesFanboxAtUrlAndKeepsOptionalName() {
        Artist artist = service.parse(request("https://www.fanbox.cc/@takk/posts/123", "Takk 老师", true));

        assertThat(artist.getArtistId()).isEqualTo("takk");
        assertThat(artist.getName()).isEqualTo("Takk 老师");
        assertThat(artist.getDomain()).isEqualTo(ArtistDomainEnum.FANBOX);
    }

    @Test
    void parsesConfiguredKemonoUrlAndAddsDisplayPrefix() {
        Artist artist = service.parse(request(
                "https://pawchive.pw/fanbox/user/14429315?o=50", "Some Artist", null));
        ArtistManagementItem item = service.toItem(artist);

        assertThat(artist.getArtistId()).isEqualTo("14429315");
        assertThat(artist.getType()).isEqualTo("fanbox");
        assertThat(artist.getDomain()).isEqualTo(ArtistDomainEnum.KEMONO);
        assertThat(artist.getIcon()).isNull();
        assertThat(artist.getBanner()).isNull();
        assertThat(artist.getCompression()).isTrue();
        assertThat(item.getName()).isEqualTo("Some Artist");
        assertThat(item.getType()).isEqualTo("kemono-fanbox");
        assertThat(item.getUrl()).isEqualTo("https://pawchive.pw/fanbox/user/14429315");
    }

    @Test
    void treatsLegacyNumericRowsAsKemonoRows() {
        ArtistManagementItem item = service.toItem(new Artist()
                .setArtistId("42")
                .setName("Legacy")
                .setType("fanbox"));

        assertThat(item.getName()).isEqualTo("Legacy");
        assertThat(item.getType()).isEqualTo("kemono-fanbox");
        assertThat(item.getUrl()).isEqualTo("https://pawchive.pw/fanbox/user/42");
        assertThat(item.getCompression()).isTrue();
    }

    @Test
    void rejectsUnrelatedUrls() {
        assertThatThrownBy(() -> service.parse(request("https://example.com/artist/1", null, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持");
    }

    private ArtistCreateRequest request(String url, String name, Boolean compression) {
        ArtistCreateRequest request = new ArtistCreateRequest();
        request.setUrl(url);
        request.setName(name);
        request.setCompression(compression);
        return request;
    }
}
