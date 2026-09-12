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
@CreateTableSql("create table t_fanbox_artist\n" +
        "(\n" +
        "    id          int auto_increment\n" +
        "        primary key,\n" +
        "    name        varchar(256)            not null,\n" +
        "    icon        varchar(1024)           null,\n" +
        "    banner      varchar(1024)           null,\n" +
        "    artist_id   varchar(256)            not null,\n" +
        "    gmt_create  timestamp               not null,\n" +
        "    last_update text                    null,\n" +
        "    enable      int                     not null,\n" +
        "    type        varchar(255) default '' not null,\n" +
        "    compression tinyint                 null comment '是否压缩 1或者null表示压缩 0表示不压缩',\n" +
        "    constraint uk_artist_id\n" +
        "        unique (artist_id)\n" +
        ");\n" +
        "\n" +
        "\n" +
        " ")
public class Artist {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String icon;
    private String banner;
    private String artistId;
    private String type;
    private Boolean enable;
    private Date gmtCreate;
    private Date lastUpdate;
    private Boolean compression;
    private ArtistDomainEnum domain;

}
