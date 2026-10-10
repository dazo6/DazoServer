package com.dazo66.dashboard;

import lombok.Getter;
import java.util.List;

@Getter
public class HourlyArtistDetails {
    private final String hour;
    private final long total;
    private final long unknownCount;
    private final List<HourlyArtistCount> artists;

    public HourlyArtistDetails(String hour, long total, long unknownCount, List<HourlyArtistCount> artists) {
        this.hour = hour;
        this.total = total;
        this.unknownCount = unknownCount;
        this.artists = artists;
    }
}
