ALTER TABLE t_fanbox_artist
    ADD COLUMN icon varchar(1024) NULL AFTER name;

ALTER TABLE t_fanbox_artist
    ADD COLUMN banner varchar(1024) NULL AFTER icon;
