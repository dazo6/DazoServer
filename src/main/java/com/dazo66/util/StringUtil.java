package com.dazo66.util;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/2/25 11:33
 **/
public class StringUtil {

    public static String cleanSym(String s) {
        String replace =
                s.replace("/", "").replace(":", "").replace("*", "").replace("\\", "").replace(
                        "|", "").replace("<", "").replace(">", "").replace("?", "").replace("\"",
                        "");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < replace.length(); i++) {
            char c = replace.charAt(i);
            if (c >= 'A' || (c >= '0' && c <= '9') || c == '-') {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }
}
