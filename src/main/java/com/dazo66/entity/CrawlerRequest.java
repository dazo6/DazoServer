package com.dazo66.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dazo66.mapper.DateTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.util.Date;

/**
 * @author Dazo66
 */
@Data
@TableName("t_crawler_request")
@CreateTableSql("CREATE TABLE t_crawler_request(id int primary key, url text, gmt_create text);")
public class CrawlerRequest {

    @TableId
    private Integer id;
    private String url;
    @TableField(typeHandler = DateTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private Date gmtCreate;

}
