package com.landray.skillhub.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.infrastructure.persistence.po.InstallBundleRow;

/**
 * {@code install_bundle} 的读写查询。SQL 在 {@code resources/mapper/InstallBundleMapper.xml}。
 *
 * <p>这是安装链路<b>自己的</b> Mapper,不与 {@code SkillMapper} 共用——归属不同任务边界,详见任务交接说明。
 */
@Mapper
public interface InstallBundleMapper {

    /** {@code parameterType=map}:insert 需要用 {@code useGeneratedKeys} 把生成的 id 写回参数,record 没有 setter。 */
    void insert(Map<String, Object> params);

    InstallBundleRow selectByToken(@Param("token") String token);

    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
