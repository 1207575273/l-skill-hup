package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.domain.model.UserIdentity;

/** {@code user_identity} 表的 MyBatis Mapper。 */
public interface UserIdentityMapper {

    /** 见 {@link com.landray.skillhub.infrastructure.persistence.AppUserRepositoryImpl} 顶部注释,同样的不可变记录约束。 */
    int insert(Map<String, Object> params);

    int update(UserIdentity identity);

    UserIdentity selectById(@Param("id") long id);

    UserIdentity selectByProviderAndExternalId(@Param("provider") String provider, @Param("externalId") String externalId);

    List<UserIdentity> selectByUserId(@Param("userId") long userId);

    List<UserIdentity> selectByUserIds(@Param("userIds") Collection<Long> userIds);
}
