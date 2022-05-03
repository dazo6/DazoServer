package com.dazo66.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dazo66.mapper.DateTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.ibatis.type.JdbcType;

import java.util.Date;

/**
 * @author Dazo66
 */
@Accessors(chain = true)
@Data
@TableName("t_fanbox_artist")
@CreateTableSql("CREATE TABLE t_fanbox_artist(id INTEGER PRIMARY KEY, " + "name text, " +
        "artist_id text, " + "gmt_create text, " + "last_update text, " + "enable BOOLEAN NOT " + "NULL CHECK (enable IN (0, 1))" + ");")
public class FanboxArtist {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String artistId;
    private boolean enable = true;
    @TableField(typeHandler = DateTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private Date gmtCreate;
    @TableField(typeHandler = DateTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private Date lastUpdate;

}
