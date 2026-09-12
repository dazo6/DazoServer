-- Run this migration on databases that already contain the icon column.
ALTER TABLE t_fanbox_artist
    ADD COLUMN banner varchar(1024) NULL AFTER icon;
