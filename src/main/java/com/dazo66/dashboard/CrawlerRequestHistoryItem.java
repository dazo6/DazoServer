package com.dazo66.dashboard;

import java.util.Date;

/**
 * Crawl history prepared for display. Relative crawler URLs are expanded by
 * the backend before reaching the browser.
 */
public class CrawlerRequestHistoryItem {

    private Integer id;
    private Integer artistId;

    public Integer getArtistId() { return artistId; }
    public void setArtistId(Integer artistId) { this.artistId = artistId; }
    private String url;
    private Date gmtCreate;
    private Boolean isDone;
    private Integer errorCount;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Date getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(Date gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    public Boolean getIsDone() {
        return isDone;
    }

    public void setIsDone(Boolean done) {
        isDone = done;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }
}
