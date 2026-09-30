package com.dazo66.config.gecco;

import com.dazo66.crawler.fanbox.FanboxPageinate;
import com.dazo66.crawler.fanbox.FanboxPostDetail;
import com.dazo66.crawler.fanbox.pipeline.FanboxPostDetailPipeline;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.response.HttpResponse;
import com.geccocrawler.gecco.spider.render.Render;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MyRenderFactoryTest {

    @Test
    void rendersJsonStringArrayIntoListField() {
        String first = "https://api.fanbox.cc/post.listCreator?creatorId=test&maxId=2&limit=10";
        String second = "https://api.fanbox.cc/post.listCreator?creatorId=test&maxId=1&limit=10";

        FanboxPageinate bean = render("{\"body\":[\"" + first + "\",\"" + second + "\"]}");

        assertNotNull(bean);
        assertEquals(2, bean.getUrls().size());
        assertEquals(first, bean.getUrls().get(0));
        assertEquals(second, bean.getUrls().get(1));
    }

    @Test
    void rendersEmptyJsonArrayIntoEmptyList() {
        FanboxPageinate bean = render("{\"body\":[]}");

        assertNotNull(bean);
        assertEquals(Collections.emptyList(), bean.getUrls());
    }

    @Test
    void rendersPaginateCreatorPageUrlObjects() {
        String url = "https://api.fanbox.cc/post.listCreator?creatorId=test&firstId=42&limit=10";
        FanboxPageinate bean = render("{\"body\":[{\"pageUrls\":[\"" + url + "\"]}]}");

        assertEquals(Collections.singletonList(url), bean.getUrls());
        assertEquals(Collections.singletonList(url),
                render("{\"body\":{\"pageUrls\":[\"" + url + "\"]}}").getUrls());
    }

    @Test
    void rendersNewAndLegacyFanboxPostDetails() {
        String post = "{\"creatorId\":\"artist\",\"title\":\"Test\","
                + "\"type\":\"image\",\"isRestricted\":false,"
                + "\"publishedDatetime\":\"2026-09-29T12:00:00+09:00\","
                + "\"imageForShare\":\"https://example.test/share.jpg\","
                + "\"body\":{\"images\":[{\"id\":\"one\",\"extension\":\"jpg\","
                + "\"originalUrl\":\"https://example.test/one.jpg\"}]}}";

        for (String json : new String[]{"{\"body\":{\"post\":" + post + "}}",
                "{\"body\":" + post + "}"}) {
            FanboxPostDetail bean = renderDetail(json);
            assertNotNull(bean);
            assertEquals("artist", bean.getArtistId());
            assertEquals("Test", bean.getTitle());
            assertEquals("image", bean.getType());
            assertEquals(Boolean.FALSE, bean.getIsRestricted());
            assertEquals("2026-09-29T12:00:00+09:00", bean.getPublishedDatetime());
            assertEquals("https://example.test/share.jpg", bean.getImageForShare());
            assertEquals(1, bean.getDownloadObject().size());
            assertEquals("https://example.test/one.jpg", bean.getDownloadObject().get(0).getUrl());
        }
    }

    @Test
    void skipsRestrictedNewShapeWithoutNullPointerException() {
        FanboxPostDetail bean = renderDetail("{\"body\":{\"post\":{"
                + "\"creatorId\":\"artist\",\"type\":\"image\",\"isRestricted\":true}}}");

        assertEquals(Boolean.TRUE, bean.getIsRestricted());
        assertDoesNotThrow(() -> new FanboxPostDetailPipeline().process(bean));
    }

    private FanboxPageinate render(String json) {
        Render render = new MyRenderFactory(new Reflections("com.dazo66.crawler.fanbox"))
                .createJsonRender();
        HttpGetRequest request = new HttpGetRequest(
                "https://api.fanbox.cc/post.paginateCreator?creatorId=test");
        request.addParameter("userName", "test");
        return (FanboxPageinate) render.inject(
                FanboxPageinate.class, request, HttpResponse.createSimple(json));
    }

    private FanboxPostDetail renderDetail(String json) {
        Render render = new MyRenderFactory(new Reflections("com.dazo66.crawler.fanbox"))
                .createJsonRender();
        HttpGetRequest request = new HttpGetRequest("https://api.fanbox.cc/post.info?postId=42");
        request.addParameter("postId", "42");
        return (FanboxPostDetail) render.inject(
                FanboxPostDetail.class, request, HttpResponse.createSimple(json));
    }
}
