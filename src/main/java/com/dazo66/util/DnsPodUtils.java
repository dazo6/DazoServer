package com.dazo66.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DnsPodUtils {

    public static Boolean dnspodUpdate(String domain, String subDomain, String loginToken, String recordType, String value) {
        Map<String, String> body = new HashMap<>();
        body.put("domain", domain);
        body.put("sub_domain", subDomain);
        body.put("login_token", loginToken);
        String post = HttpUtils.doPostForForm("https://dnsapi.cn", "/Record.List", Collections.emptyMap(), Collections.emptyMap(), body);
        log.error(post);
        JSONObject object = JSON.parseObject(post);
        JSONArray records = object.getJSONArray("records");
        JSONObject record = records.getJSONObject(0);
        Map<String, String> body1 = new HashMap<>();
        body1.put("domain", domain);
        body1.put("record_id", record.getString("id"));
        body1.put("sub_domain", subDomain);
        body1.put("record_type", recordType);
        body1.put("record_line", "默认");
        body1.put("value", value);
        body1.put("login_token", loginToken);
        post = HttpUtils.doPostForForm("https://dnsapi.cn", "/Record.Modify", Collections.emptyMap(), Collections.emptyMap(), body1);
        JSONObject ret = JSON.parseObject(post);
        return ret.getJSONObject("status").getInteger("code") == 1;
    }


}
