package com.dazo66.util;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CrawlerArtistUrlTest {
    private final String base = "https://pawchive.pw/";
    @Test void extractsKemonoIdentityAcrossSupportedUrls() {
        for (String url : new String[]{"/fanbox/user/42/post/9", "/api/v1/fanbox/user/42/posts?o=50",
                "https://kemono.su/api/v1/fanbox/user/42/post/9", "https://kemono.cr/fanbox/user/42?o=50",
                "https://pawchive.pw/fanbox/user/42", "/fanbox/user/%34%32/post/9"}) {
            assertThat(CrawlerArtistUrl.parse(url, base)).isEqualTo(new CrawlerArtistUrl.Identity("KEMONO", "fanbox", "42"));
        }
        assertThat(CrawlerArtistUrl.parse("https://mirror.example/patreon/user/42", "https://mirror.example/"))
                .isEqualTo(new CrawlerArtistUrl.Identity("KEMONO", "patreon", "42"));
    }
    @Test void extractsFanboxCreatorButNeverGuessesFromPostId() {
        assertThat(CrawlerArtistUrl.parse("https://api.fanbox.cc/post.listCreator?limit=10&creatorId=artist-name", base))
                .isEqualTo(new CrawlerArtistUrl.Identity("FANBOX", null, "artist-name"));
        assertThat(CrawlerArtistUrl.parse("https://api.fanbox.cc/post.paginateCreator?creatorId=artist", base))
                .isEqualTo(new CrawlerArtistUrl.Identity("FANBOX", null, "artist"));
        assertThat(CrawlerArtistUrl.parse("https://api.fanbox.cc/post.info?postId=42", base)).isNull();
    }
    @Test void rejectsUnrelatedMalformedAndAmbiguousUrls() {
        for (String url : new String[]{null, "", "not a URL", "https://example.org/fanbox/user/42/post/9",
                "/fanbox/user/42x/invalid/9", "/fanbox/user/42%2F99/post/9", "file:/fanbox/user/42",
                "https://api.fanbox.cc/post.listCreator?creatorId=a&creatorId=b", "/favorites", "/data/image.png"}) {
            assertThat(CrawlerArtistUrl.parse(url, base)).as("url %s", url).isNull();
        }
    }
}
