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
        JSONObject object = parseSuccessfulResponse(post, "Record.List");
        if (object == null) return false;
        JSONArray records = object.getJSONArray("records");
        if (records == null || records.isEmpty()) {
            log.warn("DNSPod Record.List 未返回记录");
            return false;
        }
        JSONObject record = records.getJSONObject(0);
        if (record == null || record.getString("id") == null) {
            log.warn("DNSPod Record.List 未返回记录 ID");
            return false;
        }
        Map<String, String> body1 = new HashMap<>();
        body1.put("domain", domain);
        body1.put("record_id", record.getString("id"));
        body1.put("sub_domain", subDomain);
        body1.put("record_type", recordType);
        body1.put("record_line", "默认");
        body1.put("value", value);
        body1.put("login_token", loginToken);
        post = HttpUtils.doPostForForm("https://dnsapi.cn", "/Record.Modify", Collections.emptyMap(), Collections.emptyMap(), body1);
        return parseSuccessfulResponse(post, "Record.Modify") != null;
    }

    static JSONObject parseSuccessfulResponse(String response, String operation) {
        try {
            JSONObject object = JSON.parseObject(response);
            JSONObject status = object == null ? null : object.getJSONObject("status");
            Integer code = status == null ? null : status.getInteger("code");
            if (Integer.valueOf(1).equals(code)) return object;
            log.warn("DNSPod {} 返回失败状态: code={}", operation, code);
        } catch (RuntimeException exception) {
            log.warn("DNSPod {} 响应格式无效", operation, exception);
        }
        return null;
    }

}
