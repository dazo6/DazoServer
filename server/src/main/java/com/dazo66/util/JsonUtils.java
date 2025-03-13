package com.dazo66.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JsonUtils {

    public static <T> T jsonObject2Bean(JSONObject json, Class<T> clazz) {
        if (json == null || clazz == null) {
            return null;
        }
        return json.toJavaObject(clazz);
    }

    public static <T> List<T> jsonObjectArray2Bean(List<JSONObject> jsons, Class<T> clazz) {
        if (jsons == null || clazz == null) {
            return Collections.emptyList();
        }
        return jsons.stream().map(json -> jsonObject2Bean(json, clazz)).collect(Collectors.toList());
    }

}
