package com.dazo66.dashboard;

import java.util.List;

/**
 * Complete, fixed data model consumed by the built-in crawl dashboard.
 */
public class DashboardData {

    private List<HourlyCount> hourlyCounts;
    private List<StatusCount> statusCounts;
    private List<ArtistUpdate> artistUpdates;

    public DashboardData() {
    }

    public DashboardData(List<HourlyCount> hourlyCounts, List<StatusCount> statusCounts,
                         List<ArtistUpdate> artistUpdates) {
        this.hourlyCounts = hourlyCounts;
        this.statusCounts = statusCounts;
        this.artistUpdates = artistUpdates;
    }

    public List<HourlyCount> getHourlyCounts() {
        return hourlyCounts;
    }

    public void setHourlyCounts(List<HourlyCount> hourlyCounts) {
        this.hourlyCounts = hourlyCounts;
    }

    public List<StatusCount> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(List<StatusCount> statusCounts) {
        this.statusCounts = statusCounts;
    }

    public List<ArtistUpdate> getArtistUpdates() {
        return artistUpdates;
    }

    public void setArtistUpdates(List<ArtistUpdate> artistUpdates) {
        this.artistUpdates = artistUpdates;
    }
}
