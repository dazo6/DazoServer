package com.dazo66.crawler.kemono;

import com.dazo66.config.KemonoProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KemonoPropertiesTest {

    @Test
    void htmlModeBuildsPawchiveRoutesAndRelativeDatabaseValues() {
        KemonoProperties properties = new KemonoProperties();
        properties.setMode(KemonoProperties.Mode.HTML);
        properties.setBaseUrl("https://pawchive.pw");

        assertEquals("https://pawchive.pw/", properties.getBaseUrl());
        assertEquals("https://pawchive.pw/favorites", properties.favoritesUrl());
        assertEquals("/fanbox/user/14429315", properties.artistPath("fanbox", "14429315"));
        assertEquals("/fanbox/user/14429315?o=50",
                properties.artistPagePath("fanbox", "14429315", 50));
        assertEquals("/fanbox/user/1521209/post/12260215",
                properties.postPath("fanbox", "1521209", "12260215"));
        assertEquals("/fanbox/user/14429315?o=50",
                properties.relativePath("https://pawchive.pw/fanbox/user/14429315?o=50"));
        assertEquals("https://pawchive.pw/fanbox/user/14429315?o=50",
                properties.absoluteUrl("/fanbox/user/14429315?o=50"));
    }

    @Test
    void jsonModeKeepsLegacyApiRouteShape() {
        KemonoProperties properties = new KemonoProperties();
        properties.setMode(KemonoProperties.Mode.JSON);
        properties.setBaseUrl("https://kemono.cr/");

        assertEquals("https://kemono.cr/api/v1/account/favorites?type=artist", properties.favoritesUrl());
        assertEquals("/fanbox/user/14429315",
                properties.artistPath("fanbox", "14429315"));
        assertEquals("https://kemono.cr/api/v1/fanbox/user/14429315/posts",
                properties.absoluteUrl(properties.artistPath("fanbox", "14429315")));
        assertEquals("/fanbox/user/1521209/post/12260215",
                properties.postPath("fanbox", "1521209", "12260215"));
        assertEquals("https://kemono.cr/api/v1/fanbox/user/1521209/post/12260215",
                properties.absoluteUrl(properties.postPath("fanbox", "1521209", "12260215")));
        assertEquals("/fanbox/user/14429315?o=50",
                properties.relativePath("https://kemono.cr/api/v1/fanbox/user/14429315/posts?o=50"));
    }

    @Test
    void refusesToPersistAUrlFromAnotherOrigin() {
        KemonoProperties properties = new KemonoProperties();
        properties.setBaseUrl("https://pawchive.pw/");

        assertThrows(IllegalArgumentException.class,
                () -> properties.relativePath("https://example.com/fanbox/user/1"));
    }
}
