package com.dazo66.dashboard;

import java.util.List;

/**
 * Artist metadata and downloaded images returned by one gallery query.
 */
public class ArtistGalleryData {

    private ArtistManagementItem artist;
    private List<ArtistGalleryItem> images;

    public ArtistGalleryData() {
    }

    public ArtistGalleryData(ArtistManagementItem artist, List<ArtistGalleryItem> images) {
        this.artist = artist;
        this.images = images;
    }

    public ArtistManagementItem getArtist() {
        return artist;
    }

    public void setArtist(ArtistManagementItem artist) {
        this.artist = artist;
    }

    public List<ArtistGalleryItem> getImages() {
        return images;
    }

    public void setImages(List<ArtistGalleryItem> images) {
        this.images = images;
    }
}
