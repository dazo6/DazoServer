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
@Data
@TableName("t_crawler_request")
@Accessors(chain = true)
@CreateTableSql("CREATE TABLE t_crawler_request(id INTEGER PRIMARY KEY, url text, gmt_create " +
        "text, is_done BOOLEAN NOT NULL CHECK (is_done IN (0, 1)));")
public class CrawlerRequest {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String url;
    @TableField(typeHandler = DateTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private Date gmtCreate;
    private boolean isDone = false;

}
