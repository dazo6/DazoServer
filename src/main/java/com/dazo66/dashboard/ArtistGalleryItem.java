package com.dazo66.dashboard;

/**
 * A downloaded image prepared for the artist gallery page.
 */
public class ArtistGalleryItem {

    private String name;
    private String url;
    private String thumbnailUrl;
    private long createdAt;

    public ArtistGalleryItem() {
    }

    public ArtistGalleryItem(String name, String url, String thumbnailUrl, long createdAt) {
        this.name = name;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
