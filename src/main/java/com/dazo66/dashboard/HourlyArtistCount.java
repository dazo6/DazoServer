package com.dazo66.dashboard;

import lombok.Data;

/** A grouped crawl-record count for one artist in an hour. */
@Data
public class HourlyArtistCount {
    private Integer artistId;
    private String name;
    private String icon;
    private long count;

    public HourlyArtistCount() {}
    public HourlyArtistCount(Integer artistId, String name, String icon, long count) {
        this.artistId = artistId;
        this.name = name;
        this.icon = icon;
        this.count = count;
    }
}
