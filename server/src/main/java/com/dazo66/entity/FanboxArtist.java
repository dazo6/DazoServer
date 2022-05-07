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
@Accessors(chain = true)
@Data
@TableName("t_fanbox_artist")
@CreateTableSql("CREATE TABLE `t_fanbox_artist`  (\n" + "  `id` int NOT NULL AUTO_INCREMENT,\n" + "  `name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,\n" + "  `type` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,\n" + "  `artist_id` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,\n" + "  `gmt_create` timestamp NULL DEFAULT NULL,\n" + "  `last_update` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,\n" + "  `enable` int NOT NULL,\n" + "  PRIMARY KEY (`id`) USING BTREE,\n" + "  UNIQUE INDEX `uk_artist_id`(`artist_id` ASC) USING BTREE\n" + ") ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;")
public class FanboxArtist {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String artistId;
    private String type;
    private Boolean enable;
    private Date gmtCreate;
    private Date lastUpdate;

}
