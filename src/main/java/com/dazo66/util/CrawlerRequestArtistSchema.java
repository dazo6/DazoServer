package com.dazo66.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Additive and repeatable migration; existing request data is preserved. */
public final class CrawlerRequestArtistSchema {
    private CrawlerRequestArtistSchema() {}
    public static void ensureTimelineIndex(Connection connection) throws SQLException {
        boolean hasIndex;
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("SHOW INDEX FROM t_crawler_request WHERE Column_name IN ('gmt_create', 'artist_id')")) {
            java.util.Set<String> timeIndexes = new java.util.HashSet<>();
            java.util.Set<String> artistIndexes = new java.util.HashSet<>();
            while (indexes.next()) {
                if ("gmt_create".equals(indexes.getString("Column_name")) && indexes.getInt("Seq_in_index") == 1)
                    timeIndexes.add(indexes.getString("Key_name"));
                if ("artist_id".equals(indexes.getString("Column_name")) && indexes.getInt("Seq_in_index") == 2)
                    artistIndexes.add(indexes.getString("Key_name"));
            }
            timeIndexes.retainAll(artistIndexes);
            hasIndex = !timeIndexes.isEmpty();
        }
        if (!hasIndex) try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX idx_crawler_request_time_artist ON t_crawler_request (gmt_create, artist_id)");
        }
    }

    public static void ensure(Connection connection) throws SQLException {
        boolean hasColumn;
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("SHOW COLUMNS FROM t_crawler_request LIKE 'artist_id'")) {
            hasColumn = columns.next();
        }
        if (!hasColumn) try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE t_crawler_request ADD COLUMN artist_id INT NULL COMMENT 't_fanbox_artist.id' AFTER url");
        }
        boolean hasIndex;
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("SHOW INDEX FROM t_crawler_request WHERE Column_name = 'artist_id' AND Seq_in_index = 1")) {
            hasIndex = indexes.next();
        }
        if (!hasIndex) try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX idx_crawler_request_artist_id ON t_crawler_request (artist_id)");
        }
    }
}
