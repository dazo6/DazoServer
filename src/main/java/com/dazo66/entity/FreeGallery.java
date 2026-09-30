package com.dazo66.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Accessors(chain = true)
@Data
@TableName("t_free_gallery")
@CreateTableSql("CREATE TABLE t_free_gallery (\n" +
        "    id int NOT NULL AUTO_INCREMENT,\n" +
        "    folder_path varchar(2048) NOT NULL,\n" +
        "    name varchar(256) NOT NULL,\n" +
        "    gmt_create timestamp NOT NULL,\n" +
        "    gmt_modified timestamp NOT NULL,\n" +
        "    PRIMARY KEY (id)\n" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")
public class FreeGallery {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String folderPath;
    private String name;
    private Date gmtCreate;
    private Date gmtModified;
}
