package com.dazo66.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONPath;
import org.junit.jupiter.api.Test;

public class JSONEvalTest {

    @Test
    public void test() {
        String json = "{}";
        System.out.println(JSONPath.eval(JSON.parse(json), "$.error"));
    }

}
