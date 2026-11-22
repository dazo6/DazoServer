package com.dazo66.android;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class JsonRequestBodyTest {
    @Test public void preservesDeletionArrayAndEscapedUnicodeFilenames() {
        String json="[\"screenshot_20261118_123540_com.huawei.hmos.health.jpg\",\"中文 \\\"名字\\\".png\"]";
        assertEquals(json,new String(JsonRequestBody.encode(json),StandardCharsets.UTF_8));
        assertEquals("[]",new String(JsonRequestBody.encode("[]"),StandardCharsets.UTF_8));
    }
    @Test public void keepsObjectRequestsAndOmitsAbsentBody() {
        String json="{\"name\":\"画师\",\"enabled\":true}";
        assertEquals(json,new String(JsonRequestBody.encode(json),StandardCharsets.UTF_8));
        assertNull(JsonRequestBody.encode(null));assertNull(JsonRequestBody.encode(""));
    }
}
