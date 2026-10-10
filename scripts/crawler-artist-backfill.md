# Crawl request artist ownership

The nullable integer t_crawler_request.artist_id stores t_fanbox_artist.id. Startup adds the column and a leading artist_id index when missing. The history API exposes artistId as the database primary key.

Run from the repository root (Java 8 and MySQL Connector/J required):

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/backfill-crawler-artists.ps1 -Mode Preview
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/backfill-crawler-artists.ps1 -Mode Apply
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/backfill-crawler-artists.ps1 -Mode Verify
~~~

Default configuration is D:/DazoServer/application.properties; override with -ConfigPath. Credentials are read inside Java and never passed on the command line. Preview does not change the database. Apply adds missing schema and fills NULL values in batches of 500 with guarded updates, leaving existing non-NULL ownership intact. Each applied run writes inverse SQL under target/crawler-artist-backups (override -BackupDirectory). Keep this rollback artifact until verification succeeds. Repeating Apply is safe.

Kemono relative, configured-origin and legacy kemono URLs are matched by platform, external user ID and KEMONO domain. Fanbox list/paginate/creator URLs are matched by creatorId and FANBOX domain. Unrecognized URLs, ambiguous mappings, missing artists and Fanbox post.info URLs containing only postId remain NULL. No external requests are made. Live Fanbox list and detail processing supply the known artist primary key because post.info URLs cannot supply it.
