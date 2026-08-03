package com.landray.skillhub.application.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.domain.auth.PasswordHasher;
import com.landray.skillhub.domain.exception.AccountLockedException;
import com.landray.skillhub.domain.exception.InvalidCredentialException;
import com.landray.skillhub.domain.exception.LocalRegisterDisabledException;
import com.landray.skillhub.domain.exception.OldPasswordMismatchException;
import com.landray.skillhub.domain.exception.UserDisabledException;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.SessionToken;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.model.UserSession;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.domain.repository.UserSessionRepository;

/**
 * 认证用例编排:本地登录 / 注册 / 登出 / 会话解析 / 身份列表 / 改密码(业务建模文档 6.2 的"认证""身份与会话"两组接口)。
 *
 * <p>{@code @Transactional} 只出现在这一层(backend/CLAUDE.md 第一节)。P0 只有 LOCAL 一种登录方式,
 * 认证流程直接编排在这里;3.1 提到的 {@code AuthProvider} 抽象是为 P1 钉钉 OAuth2 准备的边界,
 * 本轮不引入没有第二个实现的抽象层(backend/CLAUDE.md 第二节判定标准:不引它,现在具体哪里痛?
 * 密码哈希的痛点已经由 {@link PasswordHasher} 接口单独解决)。
 */
@Service
public class AuthApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AuthApplicationService.class);

    private final AppUserRepository appUserRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;

    public AuthApplicationService(
            AppUserRepository appUserRepository,
            UserIdentityRepository userIdentityRepository,
            UserSessionRepository userSessionRepository,
            PasswordHasher passwordHasher,
            AuthProperties authProperties) {
        this.appUserRepository = appUserRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordHasher = passwordHasher;
        this.authProperties = authProperties;
    }

    /** 本站启用了哪些登录方式,P0 只有 LOCAL(业务建模文档 6.2)。 */
    public List<String> enabledProviders() {
        return List.of(IdentityProvider.LOCAL.name());
    }

    public boolean isLocalRegisterEnabled() {
        return authProperties.localRegisterEnabled();
    }

    /**
     * 本地登录。失败路径覆盖:登录名不存在、密码错误、账号被禁用、身份被锁定——顺序本身是业务规则的一部分,
     * 详见各分支注释。
     *
     * <p><b>{@code noRollbackFor = InvalidCredentialException.class} 不可省</b>——密码错误分支先
     * {@code userIdentityRepository.update(...)} 记一次失败,再抛 {@link InvalidCredentialException}。
     * Spring 事务默认对未受检异常整体回滚,如果不显式声明不回滚,这次 UPDATE 会被同一事务的回滚撤销,
     * {@code failed_login_count} 永远增长不到锁定阈值——这条坑真实存在过:Mockito 单测测不出来(它不模拟事务),
     * 是 {@code AuthSecurityIntegrationTest} 起真 PostgreSQL 才暴露的。
     */
    @Transactional(noRollbackFor = InvalidCredentialException.class)
    public LoginResult login(String login, String rawPassword, String userAgent, String clientIp) {
        UserIdentity identity = userIdentityRepository
                .findByProviderAndExternalId(IdentityProvider.LOCAL, login)
                .filter(UserIdentity::isActive)
                // 登录名不存在与"存在但已被撤销"统一报同一个错误,不给攻击者用报错差异探测账号是否存在。
                .orElseThrow(() -> {
                    log.warn("登录失败,登录名不存在或身份已撤销, login={}", login);
                    return new InvalidCredentialException();
                });

        Instant now = Instant.now();
        // 锁定检查必须先于密码校验:锁定期内即使密码正确也要拒绝(业务建模文档 3.2),
        // 且不能因为这次尝试而重新计算/延长锁定,避免持续攻击把账号永久锁死。
        if (identity.isLocked(now)) {
            log.warn("登录失败,该身份处于锁定期内, login={}, lockedUntil={}", login, identity.lockedUntil());
            throw new AccountLockedException(identity.lockedUntil());
        }

        AppUser user = appUserRepository
                .findById(identity.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "user_identity 引用了不存在的 app_user, identityId=" + identity.id()));
        if (!user.isActive()) {
            log.warn("登录失败,账号已被禁用, login={}, userId={}", login, user.id());
            throw new UserDisabledException();
        }

        if (!passwordHasher.matches(rawPassword, identity.credential())) {
            userIdentityRepository.update(identity.recordLoginFailure(now));
            log.warn("登录失败,密码错误, login={}, failedLoginCount={}", login, identity.failedLoginCount() + 1);
            throw new InvalidCredentialException();
        }

        userIdentityRepository.update(identity.recordLoginSuccess(now));
        log.info("登录成功, login={}, userId={}", login, user.id());
        return issueSession(user, identity, userAgent, clientIp, now);
    }

    /** 本地注册,受 {@code local-register-enabled} 开关控制,P0 默认关闭(业务建模文档 3.2)。 */
    @Transactional
    public LoginResult register(String displayName, String login, String rawPassword, String userAgent, String clientIp) {
        if (!authProperties.localRegisterEnabled()) {
            throw new LocalRegisterDisabledException();
        }
        PasswordPolicy.validate(rawPassword);

        AppUser savedUser = appUserRepository.save(AppUser.newUser(displayName, UserRole.USER));
        UserIdentity savedIdentity = userIdentityRepository.save(
                UserIdentity.newLocal(savedUser.id(), login, passwordHasher.hash(rawPassword)));

        log.info("本地注册成功, login={}, userId={}", login, savedUser.id());
        return issueSession(savedUser, savedIdentity, userAgent, clientIp, Instant.now());
    }

    /** 引导管理员:若 (LOCAL, login) 不存在则创建 ADMIN 账号,幂等——重复启动不会重复创建或报错。 */
    @Transactional
    public void ensureBootstrapAdmin(String login, String rawPassword) {
        boolean alreadyExists = userIdentityRepository
                .findByProviderAndExternalId(IdentityProvider.LOCAL, login)
                .isPresent();
        if (alreadyExists) {
            return;
        }
        PasswordPolicy.validate(rawPassword);

        AppUser savedAdmin = appUserRepository.save(AppUser.newUser(login, UserRole.ADMIN));
        userIdentityRepository.save(UserIdentity.newLocal(savedAdmin.id(), login, passwordHasher.hash(rawPassword)));

        // 引导管理员创建属于安全审计事件,WARN 起步(backend/CLAUDE.md 6.6),不带密码。
        log.warn("引导管理员已创建, event=bootstrap_admin_created, login={}, userId={}", login, savedAdmin.id());
    }

    /** 撤销当前会话(登出)。 */
    @Transactional
    public void logout(long sessionId) {
        userSessionRepository.revoke(sessionId, Instant.now());
    }

    /**
     * 按 Bearer token 解析当前登录者。找不到 / 已过期 / 已撤销 / 所属用户已被禁用,统一返回空——这些对调用方
     * 而言都是同一个结果("未认证"),由 interfaces 层的拦截器统一映射成 401(业务建模文档 6.1)。
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> resolveSession(String rawToken) {
        String tokenHash = SessionToken.hashOf(rawToken);
        Optional<UserSession> session = userSessionRepository.findByTokenHash(tokenHash);
        if (session.isEmpty() || !session.get().isValid(Instant.now())) {
            return Optional.empty();
        }
        Optional<AppUser> user = appUserRepository.findById(session.get().userId());
        if (user.isEmpty() || !user.get().isActive()) {
            return Optional.empty();
        }
        UserSession validSession = session.get();
        return Optional.of(new AuthenticatedUser(
                user.get().id(), user.get().role(), validSession.identityId(), validSession.id()));
    }

    @Transactional(readOnly = true)
    public AppUser getUser(long userId) {
        return appUserRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("会话引用了不存在的 app_user, userId=" + userId));
    }

    /** 一个人持有的全部登录方式,{@code GET /identities} 与 {@code GET /auth/me} 共用。 */
    @Transactional(readOnly = true)
    public List<UserIdentity> listIdentities(long userId) {
        return userIdentityRepository.findByUserId(userId);
    }

    /**
     * 改**当前会话所用身份**的密码,identityId 由 {@code user_session.identity_id} 定位——一个人可能有两个
     * LOCAL 身份,不定死改的是哪一个就有歧义(业务建模文档 4.4)。
     */
    @Transactional
    public void changePassword(long identityId, String oldPassword, String newPassword) {
        UserIdentity identity = userIdentityRepository
                .findById(identityId)
                .orElseThrow(() -> new IllegalStateException("会话引用了不存在的 user_identity, identityId=" + identityId));
        if (!passwordHasher.matches(oldPassword, identity.credential())) {
            throw new OldPasswordMismatchException();
        }
        PasswordPolicy.validate(newPassword);

        userIdentityRepository.update(identity.changeCredential(passwordHasher.hash(newPassword)));
        log.info("密码已修改, identityId={}, userId={}", identityId, identity.userId());
    }

    private LoginResult issueSession(
            AppUser user, UserIdentity identity, String userAgent, String clientIp, Instant now) {
        SessionToken token = SessionToken.generate();
        UserSession saved = userSessionRepository.save(
                UserSession.issue(user.id(), identity.id(), token, userAgent, clientIp, now));
        return new LoginResult(token.rawValue(), saved.expiresAt(), user, identity.id());
    }
}
