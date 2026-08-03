package com.landray.skillhub.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.domain.model.UserSession;

/** {@code user_session} 表的 MyBatis Mapper。 */
public interface UserSessionMapper {

    /** 见 {@link com.landray.skillhub.infrastructure.persistence.AppUserRepositoryImpl} 顶部注释,同样的不可变记录约束。 */
    int insert(Map<String, Object> params);

    UserSession selectByTokenHash(@Param("tokenHash") String tokenHash);

    int revokeById(@Param("id") long id, @Param("revokedAt") Instant revokedAt);

    int revokeAllActiveByUserId(@Param("userId") long userId, @Param("revokedAt") Instant revokedAt);
}
