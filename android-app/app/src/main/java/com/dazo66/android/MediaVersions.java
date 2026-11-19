package com.dazo66.android;

import java.util.LinkedHashMap;

/** LRU access timestamps must not invalidate the decoder's identity for unchanged files. */
final class MediaVersions {
    private final LinkedHashMap<String, Long> versions = new LinkedHashMap<>(32, 0.75f, true);
    private long generation = System.currentTimeMillis();
    synchronized long version(String key, boolean replaced) {
        Long value = replaced ? null : versions.get(key);
        if (value == null) {
            value = ++generation;
            versions.put(key, value);
            while (versions.size() > 256) versions.remove(versions.keySet().iterator().next());
        }
        return value;
    }
    synchronized void clear() { versions.clear(); }
}
