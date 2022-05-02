package com.dazo66.mapper;

import lombok.SneakyThrows;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * @author Dazo66
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(Date.class)
public class DateTypeHandler extends BaseTypeHandler<Date> {


    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Date parameter,
                                    JdbcType jdbcType) throws SQLException {
        ps.setString(i, Long.toString(parameter.getTime()));
    }

    @SneakyThrows
    @Override
    public Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long date = rs.getLong(columnName);
        return new Date(date);
    }

    @SneakyThrows
    @Override
    public Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long date = rs.getLong(columnIndex);
        return new Date(date);
    }

    @SneakyThrows
    @Override
    public Date getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long date = cs.getLong(columnIndex);
        return new Date(date);
    }
}
