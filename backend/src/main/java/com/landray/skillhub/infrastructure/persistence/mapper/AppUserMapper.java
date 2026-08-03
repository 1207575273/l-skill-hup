package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.domain.model.AppUser;

/**
 * {@code app_user} 表的 MyBatis Mapper。带条件/join 的语句都在 {@code AppUserMapper.xml} 里,
 * 这里不写任何 SQL 字符串(backend/CLAUDE.md 第三节)。
 */
public interface AppUserMapper {

    /** 见 {@link com.landray.skillhub.infrastructure.persistence.AppUserRepositoryImpl} 顶部注释,不可变记录约束。 */
    int insert(Map<String, Object> params);

    int update(AppUser user);

    AppUser selectById(@Param("id") long id);

    List<AppUser> selectByIds(@Param("ids") Collection<Long> ids);

    /**
     * @param provider 过滤条件,{@code null} 表示不过滤(见 AppUserMapper.xml 的 {@code <if>})
     */
    List<AppUser> selectPage(
            @Param("provider") String provider, @Param("offset") int offset, @Param("limit") int limit);

    /** 与 {@link #selectPage} 使用相同的过滤条件,结构必须同构,否则总数与实际返回的页对不上(业务建模文档 6.3)。 */
    long countAll(@Param("provider") String provider);
}
