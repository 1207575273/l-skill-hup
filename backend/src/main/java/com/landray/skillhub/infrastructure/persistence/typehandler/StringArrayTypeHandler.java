package com.landray.skillhub.infrastructure.persistence.typehandler;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * PostgreSQL {@code TEXT[]} <-> {@code List<String>},用于 {@code skill_version.allowed_tools}。
 *
 * <p>MyBatis 不认识 PostgreSQL 的数组类型,必须手写走 {@code java.sql.Array}(业务建模文档 6.3)。本类只在
 * {@code SkillMapper.xml} 里 {@code selectDetailById} 的 resultMap 中对 {@code allowed_tools} 列显式声明使用,不做
 * 全局类型注册——本任务范围内只有这一处需要它,没有第二个调用方就不值得引入全局配置。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringArrayTypeHandler extends BaseTypeHandler<List<String>> {

    private static final String POSTGRES_TEXT_ARRAY_TYPE = "text";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int columnIndex, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        Array sqlArray = ps.getConnection().createArrayOf(POSTGRES_TEXT_ARRAY_TYPE, parameter.toArray());
        ps.setArray(columnIndex, sqlArray);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
    }

    private List<String> toList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return Collections.emptyList();
        }
        Object[] elements = (Object[]) sqlArray.getArray();
        List<String> result = new ArrayList<>(elements.length);
        for (Object element : elements) {
            result.add((String) element);
        }
        return result;
    }
}
