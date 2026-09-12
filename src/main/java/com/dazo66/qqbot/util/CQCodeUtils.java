package com.dazo66.qqbot.util;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * CQ 码解析工具，将 OneBot v11 的 CQ 码字符串拆分为结构化列表。
 * <p>
 * 输入: "[CQ:image,file=xxx.png,url=https://...] 看这个图"
 * 输出: [{CQ=image, file=xxx.png, url=https://..., raw=...}, {CQ=text, value=看这个图, raw=看这个图}]
 * <p>
 * 参考自 CommunismBot 项目 CQDecoder。
 *
 * @author Dazo66
 */
public class CQCodeUtils {

    /**
     * 解析 CQ 码消息字符串，返回每个 segment 的键值对列表。
     * text segment 包含 key: CQ="text", value=文本内容
     */
    public static List<Map<String, String>> decode(String data) {
        List<Map<String, String>> output = new ArrayList<>();
        List<String> list = splitMessage(data);
        for (String element : list) {
            output.add(parseElement(element));
        }
        return output;
    }

    private static String decodeText(String text) {
        text = text.replace("&amp;", "&");
        text = text.replace("&#91;", "[");
        text = text.replace("&#93;", "]");
        return text;
    }

    private static String decodeValue(String text) {
        text = decodeText(text);
        text = text.replace("&#44;", ",");
        return text;
    }

    private static List<String> splitMessage(String cqCodeMessage) {
        List<String> list = new ArrayList<>();
        int i = 0;
        while (true) {
            int i1 = cqCodeMessage.indexOf("[", i);
            if (i1 != -1) {
                int i2 = cqCodeMessage.indexOf("]", i1);
                if (i2 != -1) {
                    String left = cqCodeMessage.substring(i, i1);
                    if (!StringUtils.isEmpty(left)) {
                        list.add(left);
                    }
                    list.add(cqCodeMessage.substring(i1, i2 + 1));
                    i = i2 + 1;
                } else {
                    list.add(cqCodeMessage.substring(i));
                    return list;
                }
            }
            if (i1 == -1) {
                if (cqCodeMessage.length() - 1 > i) {
                    list.add(cqCodeMessage.substring(i));
                }
                break;
            }
        }
        if (list.isEmpty()) {
            return Collections.singletonList(cqCodeMessage);
        }
        return list;
    }

    private static Map<String, String> parseElement(String element) {
        Map<String, String> map = new HashMap<>();
        final String raw = element.trim();
        if (element.startsWith("[CQ:")) {
            element = element.substring(0, element.length() - 1); // 去掉末尾 ]
            String[] split = element.split(",");
            String type = split[0].substring(split[0].indexOf(":") + 1);
            map.put("CQ", type);
            for (int i = 1; i < split.length; i++) {
                int i1 = split[i].indexOf("=");
                map.put(split[i].substring(0, i1), decodeValue(split[i].substring(i1 + 1)));
            }
            map.put("raw", raw);
        } else {
            map.put("CQ", "text");
            String value = decodeText(raw);
            map.put("value", value);
            map.put("raw", raw);
        }
        return map;
    }
}
