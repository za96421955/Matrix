package com.matrix.local.dal;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * 自定义 MyBatis TypeHandler：java.util.Date <-> Long (Unix 毫秒时间戳)
 * <p>
 * 解决 SQLite 中 TEXT 列存储时间戳时因格式不兼容导致的
 * {@code java.sql.SQLException: Error parsing time stamp} 问题。
 * <p>
 * 写入时将 Date 转为 Long（毫秒时间戳），
 * 读取时将 Long 转回 Date，
 * 数据库列类型使用 INTEGER 而不是 TEXT。
 */
@MappedTypes(Date.class)
public class DateToLongTypeHandler extends BaseTypeHandler<Date> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType)
            throws SQLException {
        // Date -> Long (毫秒时间戳)
        ps.setLong(i, parameter.getTime());
    }

    @Override
    public Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return new Date(value);
    }

    @Override
    public Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long value = rs.getLong(columnIndex);
        if (rs.wasNull()) {
            return null;
        }
        return new Date(value);
    }

    @Override
    public Date getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long value = cs.getLong(columnIndex);
        if (cs.wasNull()) {
            return null;
        }
        return new Date(value);
    }

}
