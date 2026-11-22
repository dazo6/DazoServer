package com.dazo66.android;

import java.nio.charset.StandardCharsets;

/** React already serialized the JSON. Preserve arrays as well as objects on the wire. */
final class JsonRequestBody {
    static byte[] encode(String json) {
        return json == null || json.isEmpty() ? null : json.getBytes(StandardCharsets.UTF_8);
    }
}
