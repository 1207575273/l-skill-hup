package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.AppUserStatus;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.shared.model.PageResult;

/**
 * 起真 PostgreSQL 测 {@link AppUserRepository},不用 H2 冒充(backend/CLAUDE.md 8.1)。
 *
 * <p>重点验证一个真实的 MyBatis 陷阱:{@link AppUser} 是不可变 record,{@code save()} 靠 {@code Map} 参数
 * 拿回生成的自增 id(见 {@code AppUserMapper.xml} 顶部注释),这里必须证明这条路径真的能取到非空 id,而不是
 * 静默拿到 null——那种"表面能跑但 id 一直是 null"的错误只有连真库才能测出来。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class AppUserRepositoryImplTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("skillhub")
            .withUsername("skillhub")
            .withPassword("skillhub");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private com.landray.skillhub.domain.repository.UserIdentityRepository userIdentityRepository;

    @Test
    void should_returnGeneratedId_when_saveCalled() {
        AppUser saved = appUserRepository.save(AppUser.newUser("测试用户-" + System.nanoTime(), UserRole.USER));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.id()).isGreaterThan(1L); // 1 是 V0002 种下的 system 账号
    }

    @Test
    void should_roundTripAllFields_when_findByIdAfterSave() {
        AppUser created = AppUser.newUser("往返测试-" + System.nanoTime(), UserRole.ADMIN);

        AppUser saved = appUserRepository.save(created);
        Optional<AppUser> found = appUserRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().displayName()).isEqualTo(created.displayName());
        assertThat(found.get().role()).isEqualTo(UserRole.ADMIN);
        assertThat(found.get().status()).isEqualTo(AppUserStatus.ACTIVE);
        assertThat(found.get().createdAt()).isNotNull();
        assertThat(found.get().updatedAt()).isNotNull();
    }

    @Test
    void should_returnEmpty_when_findByIdWithUnknownId() {
        Optional<AppUser> found = appUserRepository.findById(Long.MAX_VALUE);

        assertThat(found).isEmpty();
    }

    @Test
    void should_persistDisabledStatus_when_updateCalled() {
        AppUser saved = appUserRepository.save(AppUser.newUser("待禁用-" + System.nanoTime(), UserRole.USER));

        appUserRepository.update(saved.disable());
        Optional<AppUser> found = appUserRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(AppUserStatus.DISABLED);
    }

    @Test
    void should_returnUsersMatchingIds_when_findByIdsCalled() {
        AppUser first = appUserRepository.save(AppUser.newUser("批量1-" + System.nanoTime(), UserRole.USER));
        AppUser second = appUserRepository.save(AppUser.newUser("批量2-" + System.nanoTime(), UserRole.USER));

        List<AppUser> found = appUserRepository.findByIds(List.of(first.id(), second.id()));

        assertThat(found).extracting(AppUser::id).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void should_returnEmptyList_when_findByIdsCalledWithEmptyCollection() {
        List<AppUser> found = appUserRepository.findByIds(List.of());

        assertThat(found).isEmpty();
    }

    @Test
    void should_onlyReturnUsersHoldingGivenProvider_when_searchFilteredByProvider() {
        AppUser withLocal = appUserRepository.save(AppUser.newUser("带本地身份-" + System.nanoTime(), UserRole.USER));
        userIdentityRepository.save(com.landray.skillhub.domain.model.UserIdentity.newLocal(
                withLocal.id(), "provider-filter-login-" + System.nanoTime(), "hash"));
        AppUser withoutLocal = appUserRepository.save(AppUser.newUser("无本地身份-" + System.nanoTime(), UserRole.USER));

        PageResult<AppUser> page = appUserRepository.search(1, 100, IdentityProvider.LOCAL);

        assertThat(page.items()).extracting(AppUser::id).contains(withLocal.id());
        assertThat(page.items()).extracting(AppUser::id).doesNotContain(withoutLocal.id());
    }

    @Test
    void should_returnAllUsers_when_searchCalledWithoutProviderFilter() {
        appUserRepository.save(AppUser.newUser("无过滤-" + System.nanoTime(), UserRole.USER));

        PageResult<AppUser> page = appUserRepository.search(1, 100, null);

        // system(id=1)本身在 V0002 就已存在,断言总数至少覆盖它 + 刚插入的这条
        assertThat(page.total()).isGreaterThanOrEqualTo(2);
        assertThat(page.items()).isNotEmpty();
    }
}
