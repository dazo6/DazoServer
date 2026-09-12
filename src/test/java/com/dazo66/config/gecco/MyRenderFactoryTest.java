package com.dazo66.config.gecco;

import com.dazo66.crawler.fanbox.FanboxPageinate;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.response.HttpResponse;
import com.geccocrawler.gecco.spider.render.Render;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    private FanboxPageinate render(String json) {
        Render render = new MyRenderFactory(new Reflections("com.dazo66.crawler.fanbox"))
                .createJsonRender();
        HttpGetRequest request = new HttpGetRequest(
                "https://api.fanbox.cc/post.paginateCreator?creatorId=test");
        request.addParameter("userName", "test");
        return (FanboxPageinate) render.inject(
                FanboxPageinate.class, request, HttpResponse.createSimple(json));
    }
}
