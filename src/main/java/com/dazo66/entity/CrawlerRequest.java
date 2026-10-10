package com.dazo66.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * @author Dazo66
 */
@Data
@TableName("t_crawler_request")
@Accessors(chain = true)
@CreateTableSql("CREATE TABLE `t_crawler_request`  (\n" + "  `id` int NOT NULL AUTO_INCREMENT,\n" + "  `url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,\n" + "  `gmt_create` timestamp NULL DEFAULT NULL,\n" + "  `is_done` tinyint(1) NOT NULL,\n" + "  `artist_id` int NULL DEFAULT NULL,\n" + "  KEY `idx_crawler_request_artist_id` (`artist_id`),\n" + "  PRIMARY KEY (`id`) USING BTREE\n" + ") ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;\n")
public class CrawlerRequest {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String url;
    /** Primary key of t_fanbox_artist, not its external artist_id. */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private Integer artistId;
    private Date gmtCreate;
    private Boolean isDone;
    private Integer errorCount;
}
