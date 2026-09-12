package com.dazo66.dashboard;

/**
 * Request used by the management page when an artist is created from a URL.
 */
public class ArtistCreateRequest {

    private String url;
    private String name;
    private Boolean compression;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getCompression() {
        return compression;
    }

    public void setCompression(Boolean compression) {
        this.compression = compression;
    }
}
