package com.dazo66.dashboard;

/**
 * A downloaded image or video prepared for the artist gallery page.
 */
public class ArtistGalleryItem {

    private String name;
    private String type;
    private String folderPath;
    private String posterUrl;
    private long createdAt;

    public ArtistGalleryItem() {
    }

    public ArtistGalleryItem(String name, String type, String folderPath, long createdAt) {
        this.name = name;
        this.type = type;
        this.folderPath = folderPath;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
