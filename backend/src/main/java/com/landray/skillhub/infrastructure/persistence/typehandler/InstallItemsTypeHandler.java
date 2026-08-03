package com.landray.skillhub.infrastructure.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import com.landray.skillhub.domain.model.InstallItem;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL {@code JSONB} <-> {@code List<InstallItem>},用于 {@code install_bundle.items}。
 *
 * <p>MyBatis 不认识 PostgreSQL 的 JSONB 类型,必须手写(业务建模文档 6.3)。序列化用 Jackson 3
 * ({@code tools.jackson.*}),不是 Jackson 2——与全项目的 Jackson 处理引擎选型一致(ArchUnit 规则
 * {@code mustNotUseJacksonTwoProcessingEngine} 会拦下 {@code com.fasterxml.jackson.databind..}）。
 *
 * <p><b>为什么这里自己 {@code new} 一个 {@code ObjectMapper} 而不是注入 Spring 管理的那个</b>:MyBatis 通过
 * {@code type-handlers-package} 扫描到的 TypeHandler 由 MyBatis 自己反射实例化(要求无参构造器),不经过 Spring
 * 容器,因此拿不到 Spring 自动配置的 {@code ObjectMapper} bean。这里的用途足够窄(只序列化/反序列化
 * {@code List<InstallItem>} 这一种形状),自建一个默认配置的实例即可,不需要与 Web 层的 JSON 配置(命名策略、
 * 日期格式等)保持一致。
 *
 * <p>写入用 {@code Types.OTHER} 而不是引入 {@code org.postgresql.util.PGobject}——本项目的
 * {@code postgresql} 驱动依赖是 {@code runtime} scope(见 pom.xml),编译期引用其内部类会直接编译失败。
 * pgjdbc 对 {@code setObject(idx, json, Types.OTHER)} 会把参数标记为"类型未知",交给 PostgreSQL
 * 按目标列(jsonb)做隐式赋值转换,是驱动无关、编译期安全的写法。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class InstallItemsTypeHandler extends BaseTypeHandler<List<InstallItem>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int columnIndex, List<InstallItem> parameter, JdbcType jdbcType)
            throws SQLException {
        String json = OBJECT_MAPPER.writeValueAsString(parameter);
        ps.setObject(columnIndex, json, Types.OTHER);
    }

    @Override
    public List<InstallItem> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<InstallItem> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<InstallItem> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<InstallItem> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return OBJECT_MAPPER.readValue(json, new TypeReference<List<InstallItem>>() {});
    }
}
