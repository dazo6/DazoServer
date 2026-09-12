package com.dazo66.test;

import com.dazo66.util.StringUtil;
import com.geccocrawler.gecco.utils.UrlMatcher;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/6/3 0:29
 **/
public class StringUtilTest {

    @Test
    public void testParseStringTemplate() {
        String template = "{name}yxxx{address}";
        String str = "bbbbyxxxabcd";
        Map<String, String> map = UrlMatcher.match(str, template);
        assertEquals(map.size(), 2);
        assertEquals(map.get("name"), "bbbb");
        assertEquals(map.get("address"), "abcd");
    }

}
