package com.dazo66.crawler.kemono;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.utils.UrlMatcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PawchiveHtmlCompatibilityTest {

    @Test
    void configuredHostPatternMatchesCurrentPawchiveRoutes() {
        String postPattern = KemonoPost.class.getAnnotation(Gecco.class).matchUrl()[0];
        String userPattern = kemonoUser.class.getAnnotation(Gecco.class).matchUrl()[0];
        String nextPattern = KemonoNextPageUser.class.getAnnotation(Gecco.class).matchUrl()[0];

        assertNotNull(UrlMatcher.match(
                "https://pawchive.pw/fanbox/user/1521209/post/12260215", postPattern));
        assertNotNull(UrlMatcher.match(
                "https://pawchive.pw/fanbox/user/14429315", userPattern));
        assertNotNull(UrlMatcher.match(
                "https://pawchive.pw/fanbox/user/14429315?o=50", nextPattern));
    }

    @Test
    void currentPostMarkupStillExposesMetadataFilesAndAttachments() {
        Document page = Jsoup.parse("<section id='page'><header class='post__header'>" +
                "<div class='post__user'><a class='post__user-name'>saburoku18</a></div>" +
                "<div class='post__info'><h1 class='post__title'><span>title</span>" +
                "<span>(Pixiv Fanbox)</span></h1><div class='post__published'>" +
                "Published: 2026-07-17 20:00:00</div></div></header>" +
                "<a class='post__attachment-link' href='https://file.pawchive.pw/data/archive.zip'>" +
                "Download archive.zip</a><a class='fileThumb' " +
                "href='https://file.pawchive.pw/data/image.png'><img></a></section>");

        assertEquals("saburoku18", page.select(".post__user-name").text());
        assertEquals("title", page.select(
                "#page > header .post__info > h1.post__title > span:nth-child(1)").text());
        assertEquals(1, page.select(".fileThumb").size());
        assertEquals(1, page.select(".post__attachment-link").size());
    }

    @Test
    void currentCreatorAndFavoriteCardsRemainSelectable() {
        Document creator = Jsoup.parse("<section id='page'><div class='card-list__items'>" +
                "<article class='post-card'><a href='/fanbox/user/14429315/post/12258753'>post</a></article>" +
                "</div><div id='paginator-bottom'><menu>" +
                "<a class='next' href='/fanbox/user/14429315?o=50'>next</a>" +
                "</menu></div></section>");
        Document favorites = Jsoup.parse("<section id='page'><div class='card-list__items'>" +
                "<a class='user-card' href='/fanbox/user/14429315'>" +
                "<div class='user-card__info'><div class='user-card__name'>creator</div></div></a>" +
                "</div></section>");

        assertEquals(1, creator.select("div.card-list__items > article > a").size());
        assertEquals("/fanbox/user/14429315?o=50",
                creator.select("#paginator-bottom > menu > a.next").attr("href"));
        assertEquals(1, favorites.select(
                "#page .card-list__items > a, #page .card-list__items > article > a").size());
        assertEquals("creator", favorites.select(
                "#page .card-list__items .user-card__name").text());
    }
}
