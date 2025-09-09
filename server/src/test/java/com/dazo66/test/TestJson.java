package com.dazo66.test;

import com.dazo66.crawler.kemono.KemonoJsonFavourite;
import com.dazo66.util.JsonUtils;
import org.junit.jupiter.api.Test;

public class TestJson {

    @Test
    public void test() {
        String string = "{\"indexed\":\"2021-11-05T23:30:39.656824\",\"service\":\"fanbox\",\"faved_seq\":3170622,\"name\":\"x123456123\",\"id\":\"10049150\",\"updated\":\"2022-08-20T17:46:26.209705\",\"public_id\":\"x123456123\",\"last_imported\":\"2024-02-18T06:04:15.254906\"}";
        JsonUtils.string2Object(string, KemonoJsonFavourite.FanboxCreator.class);
    }

}
