package com.landray.skillhub.domain.auth;

/**
 * 密码哈希与校验的抽象。接口在 domain,实现在 infrastructure,与仓储接口同构
 * (业务建模文档 3.1:"认证抽象放在 domain,实现放在 infrastructure")。
 *
 * <p>存在的具体理由:BCrypt 是"自己写一定写错"的典型,但 {@code spring-security-crypto} 的
 * {@code PasswordEncoder} 是 {@code org.springframework.security..} 类型,domain 不能直接依赖
 * ——引入它会被 ArchUnit 的 domain 纯净性规则拦下来。这道接口把该框架类型挡在 infrastructure
 * 一侧(见 {@code infrastructure.security.BCryptPasswordHasher}),domain/application 只认这个接口。
 */
public interface PasswordHasher {

    /** 对明文密码算哈希,落库存这个结果,绝不存明文。 */
    String hash(String rawPassword);

    /** 校验明文密码是否与已存的哈希匹配。 */
    boolean matches(String rawPassword, String hashedPassword);
}
