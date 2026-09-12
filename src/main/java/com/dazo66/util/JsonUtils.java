package com.dazo66.util;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.Jdk8DateCodec;
import com.alibaba.fastjson.serializer.SerializeConfig;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JsonUtils {

    static {
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        SerializeConfig.getGlobalInstance().put(java.time.LocalDateTime.class, new Jdk8DateCodec());

    }

    public static <T> T jsonObject2Bean(JSONObject json, Class<T> clazz) {
        if (json == null || clazz == null) {
            return null;
        }
        return json.toJavaObject(clazz);
    }

    public static String object2JsonString(Object o) {
        if (o == null) {
            return null;
        }
        return JSONObject.toJSONString(o);
    }

    public static <T> T string2Object(String string, Class<T> clazz) {
        if (string == null || clazz == null) {
            return null;
        }
        return JSONObject.parseObject(string, clazz);
    }

    public static <T> T string2Object(String string, TypeReference<T> clazz) {
        if (string == null || clazz == null) {
            return null;
        }
        return JSONObject.parseObject(string, clazz);
    }



    public static <T> List<T> jsonObjectArray2Bean(List<JSONObject> jsons, Class<T> clazz) {
        if (jsons == null || clazz == null) {
            return Collections.emptyList();
        }
        return jsons.stream().map(json -> jsonObject2Bean(json, clazz)).collect(Collectors.toList());
    }

}
