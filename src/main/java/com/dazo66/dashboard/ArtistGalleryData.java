package com.dazo66.dashboard;

import java.util.List;

/**
 * A folder path and its downloaded media returned by one gallery query.
 */
public class ArtistGalleryData {

    private String folderPath;
    private List<ArtistGalleryItem> images;

    public ArtistGalleryData() {
    }

    public ArtistGalleryData(String folderPath, List<ArtistGalleryItem> images) {
        this.folderPath = folderPath;
        this.images = images;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public List<ArtistGalleryItem> getImages() {
        return images;
    }

    public void setImages(List<ArtistGalleryItem> images) {
        this.images = images;
    }
}
