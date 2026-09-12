package com.dazo66.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Runtime selection of the Kemono-compatible crawler and its origin.
 */
@Component
@ConfigurationProperties(prefix = "kemono")
public class KemonoProperties {

    public enum Mode {
        HTML,
        JSON
    }

    private Mode mode = Mode.HTML;
    private String baseUrl = "https://pawchive.pw/";

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.HTML : mode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("kemono.base-url must not be blank");
        }
        String normalized = baseUrl.trim();
        URI uri = URI.create(normalized);
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("kemono.base-url must be an absolute HTTP(S) URL");
        }
        this.baseUrl = normalized.endsWith("/") ? normalized : normalized + "/";
    }

    public boolean isHtmlMode() {
        return mode == Mode.HTML;
    }

    public String favoritesUrl() {
        return absoluteUrl(isHtmlMode() ? "/favorites" : "/api/v1/account/favorites?type=artist");
    }

    public String artistPath(String service, String artistId) {
        return "/" + service + "/user/" + artistId;
    }

    public String artistPagePath(String service, String artistId, int offset) {
        return artistPath(service, artistId) + "?o=" + offset;
    }

    public String postPath(String service, String artistId, String postId) {
        return "/" + service + "/user/" + artistId + "/post/" + postId;
    }

    public String absoluteUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) {
            return pathOrUrl;
        }
        URI value = URI.create(pathOrUrl.trim());
        if (value.isAbsolute()) {
            return value.toString();
        }
        String requestValue = isHtmlMode() ? normalizeRelative(pathOrUrl) : jsonRequestPath(pathOrUrl);
        String relative = requestValue.startsWith("/") ? requestValue.substring(1) : requestValue;
        return URI.create(baseUrl).resolve(relative).toString();
    }

    /**
     * Builds a public site URL without the JSON-mode API transformation. This
     * is used for browser-facing assets such as artist icons.
     */
    public String siteUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) {
            return pathOrUrl;
        }
        URI value = URI.create(pathOrUrl.trim());
        if (value.isAbsolute()) {
            return value.toString();
        }
        String relative = normalizeRelative(pathOrUrl);
        return URI.create(baseUrl).resolve(relative.substring(1)).toString();
    }

    /**
     * Converts configured-origin URLs to the database representation. Relative
     * links from HTML are normalized to start with '/'.
     */
    public String relativePath(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) {
            return pathOrUrl;
        }
        URI value = URI.create(pathOrUrl.trim());
        if (!value.isAbsolute()) {
            return canonicalPath(value.toString());
        }
        URI configured = URI.create(baseUrl);
        if (!sameOrigin(configured, value)) {
            throw new IllegalArgumentException("URL is outside kemono.base-url: " + pathOrUrl);
        }
        String result = value.getRawPath();
        if (value.getRawQuery() != null) {
            result += "?" + value.getRawQuery();
        }
        return canonicalPath(result);
    }

    public boolean isConfiguredOrigin(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI value = URI.create(url);
            return value.isAbsolute() && sameOrigin(URI.create(baseUrl), value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean sameOrigin(URI left, URI right) {
        int leftPort = left.getPort() == -1 ? defaultPort(left.getScheme()) : left.getPort();
        int rightPort = right.getPort() == -1 ? defaultPort(right.getScheme()) : right.getPort();
        return left.getHost() != null && right.getHost() != null
                && left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && leftPort == rightPort;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private String normalizeRelative(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String canonicalPath(String pathAndQuery) {
        URI value = URI.create(normalizeRelative(pathAndQuery));
        String path = value.getRawPath();
        if (path.startsWith("/api/v1/account/favorites")) {
            return "/favorites";
        }
        if (path.startsWith("/api/v1/")) {
            path = path.substring("/api/v1".length());
        }
        if (path.endsWith("/posts")) {
            path = path.substring(0, path.length() - "/posts".length());
        }
        return value.getRawQuery() == null ? path : path + "?" + value.getRawQuery();
    }

    private String jsonRequestPath(String canonicalPath) {
        URI value = URI.create(normalizeRelative(canonicalPath));
        String path = value.getRawPath();
        if (path.startsWith("/api/v1/")) {
            return canonicalPath;
        }
        if ("/favorites".equals(path)) {
            return "/api/v1/account/favorites?type=artist";
        }
        String requestPath = "/api/v1" + path;
        if (!path.contains("/post/")) {
            requestPath += "/posts";
        }
        return value.getRawQuery() == null ? requestPath : requestPath + "?" + value.getRawQuery();
    }
}
