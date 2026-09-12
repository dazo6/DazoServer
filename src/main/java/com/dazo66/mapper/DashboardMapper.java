package com.dazo66.mapper;

import com.dazo66.dashboard.ArtistUpdate;
import com.dazo66.dashboard.HourlyCount;
import com.dazo66.dashboard.StatusCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("SELECT DATE_FORMAT(gmt_create, '%Y-%m-%d %H:00') AS hour, COUNT(*) AS count "
            + "FROM t_crawler_request "
            + "WHERE url NOT LIKE '%?o=%' "
            + "AND gmt_create >= DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), INTERVAL 149 HOUR) "
            + "GROUP BY DATE_FORMAT(gmt_create, '%Y-%m-%d %H:00') "
            + "ORDER BY hour")
    List<HourlyCount> selectHourlyCounts();

    @Select("SELECT IF(is_done = 1, 1, 0) AS done, COUNT(*) AS count "
            + "FROM t_crawler_request WHERE url NOT LIKE '%?o=%' GROUP BY is_done")
    List<StatusCount> selectStatusCounts();

    @Select("SELECT CONCAT(name, ' (', artist_id, ')') AS name, icon, banner, "
            + "DATE_FORMAT(last_update, '%Y-%m-%d %H:%i') AS last "
            + "FROM t_fanbox_artist WHERE enable = 1 ORDER BY last_update DESC")
    List<ArtistUpdate> selectArtistUpdates();
}
