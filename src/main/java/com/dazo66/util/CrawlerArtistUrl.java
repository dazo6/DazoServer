package com.dazo66.util;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** URL-only identity extraction shared by live writes and the offline backfill. */
public final class CrawlerArtistUrl {
    private static final Pattern USER = Pattern.compile("^/(?:api/v1/)?([a-zA-Z0-9_-]+)/user/([^/]+)(?:/?|/posts/?|/post/[^/]+/?)$");
    private CrawlerArtistUrl() {}

    public static Identity parse(String url, String configuredBaseUrl) {
        if (url == null || url.trim().isEmpty()) return null;
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (uri.isAbsolute() && (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))) return null;
            if (uri.getRawAuthority() != null && host == null) return null;
            if (host != null && "api.fanbox.cc".equalsIgnoreCase(host)) {
                if (!Arrays.asList("/post.listCreator", "/post.paginateCreator", "/creator.get").contains(uri.getPath())) return null;
                String creator = null;
                if (uri.getRawQuery() != null) for (String parameter : uri.getRawQuery().split("&")) {
                    String[] parts = parameter.split("=", 2);
                    if ("creatorId".equals(decode(parts[0]))) {
                        if (creator != null || parts.length != 2) return null;
                        creator = decode(parts[1]);
                    }
                }
                return valid(creator) ? new Identity("FANBOX", null, creator) : null;
            }
            if (host != null) {
                String configuredHost = configuredBaseUrl == null ? null : URI.create(configuredBaseUrl).getHost();
                if (!host.equalsIgnoreCase(configuredHost == null ? "" : configuredHost)
                        && !Arrays.asList("kemono.su", "kemono.cr", "kemono.party", "pawchive.pw").contains(host.toLowerCase(Locale.ROOT))) return null;
            } else if (uri.isAbsolute() || uri.getRawAuthority() != null) return null;
            Matcher matcher = USER.matcher(uri.getRawPath() == null ? "" : uri.getRawPath());
            if (!matcher.matches()) return null;
            String id = decode(matcher.group(2));
            return valid(id) ? new Identity("KEMONO", matcher.group(1).toLowerCase(Locale.ROOT), id) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException exception) { throw new IllegalStateException(exception); }
    }
    private static boolean valid(String id) { return id != null && id.matches("[a-zA-Z0-9_.-]+"); }

    public static final class Identity {
        public final String domain;
        public final String type;
        public final String externalId;
        public Identity(String domain, String type, String externalId) {
            this.domain = domain; this.type = type; this.externalId = externalId;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Identity)) return false;
            Identity value = (Identity) other;
            return Objects.equals(domain, value.domain) && Objects.equals(type, value.type)
                    && Objects.equals(externalId, value.externalId);
        }
        @Override public int hashCode() { return Objects.hash(domain, type, externalId); }
    }
}
