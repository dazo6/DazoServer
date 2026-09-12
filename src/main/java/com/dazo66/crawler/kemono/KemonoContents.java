package com.dazo66.crawler.kemono;

public class KemonoContents {

    /**
     * Gecco annotations require compile-time constants. The host is therefore a
     * path variable while the actual host is selected by {@code kemono.base-url}.
     */
    public static final String HTTPS_HOST_PATTERN = "https://{kemonoHost}/";

    /**
     * Kept only for older manual test utilities. Runtime code must use
     * {@link com.dazo66.config.KemonoProperties}.
     */
    @Deprecated
    public static final String KEMONO_HOST = "https://pawchive.pw/";

}
