package com.landray.skillhub.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.landray.skillhub.domain.auth.PasswordHasher;

/**
 * {@link PasswordHasher} 的实现,委托给 {@code spring-security-crypto} 的 {@link BCryptPasswordEncoder}。
 *
 * <p>只引这一个 jar 就为了拿到工业级 BCrypt(业务建模文档 3.1),把 {@code org.springframework.security..}
 * 类型挡在 infrastructure 一侧,domain/application 只认 {@link PasswordHasher} 接口。
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return delegate.matches(rawPassword, hashedPassword);
    }
}
