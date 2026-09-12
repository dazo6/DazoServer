package com.dazo66.qqbot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dazo66.entity.CreateTableSql;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * QQBot 媒体文件表，关联 t_qqbot_media_record。
 *
 * @author Dazo66
 */
@Accessors(chain = true)
@Data
@TableName("t_qqbot_media_file")
@CreateTableSql("CREATE TABLE t_qqbot_media_file (\n" +
        "    id         INT AUTO_INCREMENT PRIMARY KEY,\n" +
        "    record_id  INT           NOT NULL COMMENT '关联 t_qqbot_media_record.id',\n" +
        "    file_path  VARCHAR(1024) NOT NULL COMMENT '文件绝对路径',\n" +
        "    file_name  VARCHAR(512)  NOT NULL COMMENT '文件名',\n" +
        "    INDEX idx_record_id (record_id),\n" +
        "    CONSTRAINT fk_media_record FOREIGN KEY (record_id) REFERENCES t_qqbot_media_record(id) ON DELETE CASCADE\n" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='QQBot媒体文件'")
public class MediaFile {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 关联 t_qqbot_media_record.id
     */
    private Integer recordId;

    /**
     * 文件绝对路径
     */
    private String filePath;

    /**
     * 文件名（不含路径）
     */
    private String fileName;
}
