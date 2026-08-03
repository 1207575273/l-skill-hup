package com.landray.skillhub.infrastructure.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * {@code java.time.Instant} <-> PostgreSQL {@code TIMESTAMPTZ} 的手写转换。
 *
 * <p>不依赖 MyBatis 内建的 {@code InstantTypeHandler}——pgjdbc 对 JDBC 4.2 {@code setObject}/{@code getObject}
 * 明确推荐、有把握兼容的落地类型是 {@code OffsetDateTime}(见 pgjdbc 官方文档),{@code Instant} 不在其中,直接依赖
 * 内建实现是在赌驱动行为。这里显式转一道 {@code OffsetDateTime}(统一按 UTC 写入,读出后再转回
 * {@code Instant}),把不确定性锁死在这一个类里。
 *
 * <p>业务建模文档 6.3 已经点名"纯 MyBatis 不认识 PostgreSQL 的数组与 JSONB 类型,需要手写 TypeHandler"——这里把
 * 同样的谨慎延伸到时间类型,理由同源:与其依赖未经本项目验证的内建行为,不如自己控制转换的两端。
 *
 * <p>通过 {@code mybatis.type-handlers-package} 配置(application-dev.yml / application-prod.yml)自动扫描注册,
 * 不需要在每个 Mapper XML 里逐列声明 {@code javaType}/{@code jdbcType}。
 */
@MappedTypes(Instant.class)
@MappedJdbcTypes(JdbcType.TIMESTAMP_WITH_TIMEZONE)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, OffsetDateTime.ofInstant(parameter, ZoneOffset.UTC));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime value = rs.getObject(columnName, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        OffsetDateTime value = rs.getObject(columnIndex, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        OffsetDateTime value = cs.getObject(columnIndex, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
