import com.dazo66.util.CrawlerArtistUrl;
import com.dazo66.util.CrawlerRequestArtistSchema;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Offline URL-only backfill. Run through backfill-crawler-artists.ps1. */
public final class CrawlerRequestArtistMigration {
    private static final int BATCH_SIZE = 500;
    public static void main(String[] args) throws Exception {
        Properties configuration = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get(args[0]))) { configuration.load(input); }
        String mode = args[1];
        boolean apply = "Apply".equals(mode);
        String baseUrl = configuration.getProperty("kemono.base-url", "https://pawchive.pw/");
        Properties credentials = new Properties();
        credentials.setProperty("user", configuration.getProperty("spring.datasource.username"));
        credentials.setProperty("password", configuration.getProperty("spring.datasource.password"));
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = DriverManager.getConnection(configuration.getProperty("spring.datasource.url"), credentials)) {
            if (apply) CrawlerRequestArtistSchema.ensure(connection);
            boolean hasColumn;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SHOW COLUMNS FROM t_crawler_request LIKE 'artist_id'")) { hasColumn = result.next(); }
            Map<CrawlerArtistUrl.Identity, Integer> artists = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT id, artist_id, type, domain FROM t_fanbox_artist")) {
                while (result.next()) {
                    String domain = result.getString("domain");
                    if (domain == null) continue;
                    domain = domain.toUpperCase(Locale.ROOT);
                    String type = "KEMONO".equals(domain) ? result.getString("type") : null;
                    if (type != null) type = type.toLowerCase(Locale.ROOT);
                    CrawlerArtistUrl.Identity key = new CrawlerArtistUrl.Identity(domain, type, result.getString("artist_id"));
                    // Ambiguous identities must remain NULL instead of choosing an arbitrary artist.
                    if (artists.containsKey(key)) artists.put(key, null); else artists.put(key, result.getInt("id"));
                }
            }
            long upperId;
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(id),0) FROM t_crawler_request")) {
                result.next(); upperId = result.getLong(1);
            }
            if ("Verify".equals(mode)) {
                if (!hasColumn) throw new IllegalStateException("artist_id column missing");
                verify(connection);
                return;
            }
            Path backup = null;
            if (apply) {
                backup = Paths.get(args[2], "crawler-artists-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new java.util.Date()) + ".rollback.sql");
                Files.createDirectories(backup.getParent());
            }
            try (BufferedWriter rollback = apply ? Files.newBufferedWriter(backup, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW) : null) {
                if (apply) { rollback.write("-- Restore only links changed by this run, retaining later ownership changes.\n"); rollback.flush(); }
                long cursor = 0, visited = 0, matched = 0, updated = 0;
                while (cursor < upperId) {
                    List<Row> rows = new ArrayList<>();
                    String query = "SELECT id,url FROM t_crawler_request WHERE id > ? AND id <= ?"
                            + (hasColumn ? " AND artist_id IS NULL" : "") + " ORDER BY id LIMIT " + BATCH_SIZE;
                    try (PreparedStatement statement = connection.prepareStatement(query)) {
                        statement.setLong(1, cursor); statement.setLong(2, upperId);
                        try (ResultSet result = statement.executeQuery()) {
                            while (result.next()) rows.add(new Row(result.getLong("id"), result.getString("url")));
                        }
                    }
                    if (rows.isEmpty()) break;
                    cursor = rows.get(rows.size() - 1).id;
                    visited += rows.size();
                    List<Row> matches = new ArrayList<>();
                    for (Row row : rows) {
                        row.artistId = artists.get(CrawlerArtistUrl.parse(row.url, baseUrl));
                        if (row.artistId != null) matches.add(row);
                    }
                    matched += matches.size();
                    if (!apply || matches.isEmpty()) continue;
                    connection.setAutoCommit(false);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE t_crawler_request SET artist_id=? WHERE id=? AND artist_id IS NULL AND url=?")) {
                        for (Row row : matches) {
                            // Save the inverse before committing the change. IDs are numeric; no URL or credentials in SQL.
                            rollback.write("UPDATE t_crawler_request SET artist_id=NULL WHERE id=" + row.id + " AND artist_id=" + row.artistId + ";\n");
                            statement.setInt(1, row.artistId); statement.setLong(2, row.id); statement.setString(3, row.url);
                            statement.addBatch();
                        }
                        rollback.flush();
                        int[] counts = statement.executeBatch();
                        connection.commit();
                        for (int count : counts) if (count > 0 || count == Statement.SUCCESS_NO_INFO) updated++;
                    } catch (Exception exception) {
                        connection.rollback(); throw exception;
                    } finally { connection.setAutoCommit(true); }
                }
                System.out.println("mode=" + mode + ", examined=" + visited + ", inferred=" + matched
                        + ", leftUnresolved=" + (visited - matched) + ", updated=" + updated);
                if (apply) { System.out.println("rollback=" + backup.toAbsolutePath()); verify(connection); }
            }
        }
    }
    private static void verify(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) total, COUNT(artist_id) linked FROM t_crawler_request")) {
            result.next(); System.out.println("total=" + result.getLong("total") + ", linked=" + result.getLong("linked")
                    + ", null=" + (result.getLong("total") - result.getLong("linked")));
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) FROM t_crawler_request r LEFT JOIN t_fanbox_artist a ON a.id=r.artist_id WHERE r.artist_id IS NOT NULL AND a.id IS NULL")) {
            result.next(); if (result.getLong(1) != 0) throw new IllegalStateException("Orphaned artist references found");
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SHOW INDEX FROM t_crawler_request WHERE Column_name='artist_id' AND Seq_in_index=1")) {
            if (!result.next()) throw new IllegalStateException("artist_id index missing");
            System.out.println("artist_id index and references verified");
        }
    }
    private static final class Row {
        final long id; final String url; Integer artistId;
        Row(long id, String url) { this.id = id; this.url = url; }
    }
}
