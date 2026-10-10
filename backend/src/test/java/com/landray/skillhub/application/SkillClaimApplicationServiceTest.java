package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.DeclaredOwner;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

/**
 * 认领的编排与两道"已被认领"判断。
 *
 * <p>这里最该守住的不是正常路径,而是**并发**那一支:仓储的比较并交换返回 false 时必须报冲突,而不是当成成功。
 * 少了那一支,两个人同时点认领会双双看到"认领成功",其中一个人的归属被静默覆盖 —— 这类缺陷在手工测试里
 * 几乎不可能被发现,只能靠一条明确的用例钉住。
 */
@ExtendWith(MockitoExtension.class)
class SkillClaimApplicationServiceTest {

    private static final long SKILL_ID = 7L;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private AuditLog auditLog;

    private SkillClaimApplicationService service() {
        return new SkillClaimApplicationService(skillRepository, auditLog);
    }

    private static AuthenticatedUser user(long userId) {
        return new AuthenticatedUser(userId, UserRole.USER, 1L, 1L);
    }

    /** owner 是内置 system(id=1)即无主待认领。 */
    private static Skill unclaimedSkill() {
        return skillOwnedBy(AppUser.SYSTEM_USER_ID, "system", new DeclaredOwner("张三", "80380486"));
    }

    private static Skill claimedSkill(long ownerUserId) {
        return skillOwnedBy(ownerUserId, "李四", null);
    }

    private static Skill skillOwnedBy(long ownerUserId, String ownerName, DeclaredOwner declaredOwner) {
        return new Skill(
                SKILL_ID,
                new SkillName("code-review"),
                "代码审查助手",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(ownerUserId, ownerName, null),
                declaredOwner,
                "摘要",
                SkillStatus.PUBLISHED,
                0L,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                List.of());
    }

    @Test
    @DisplayName("无主 skill 认领成功:落库后重新读一遍,返回带新归属的详情")
    void should_claimAndReturnReloadedSkill_when_skillIsUnclaimed() {
        when(skillRepository.findDetailById(SKILL_ID))
                .thenReturn(Optional.of(unclaimedSkill()))
                .thenReturn(Optional.of(claimedSkill(9L)));
        when(skillRepository.claimOwnership(SKILL_ID, 9L)).thenReturn(true);

        Skill result = service().claim(SKILL_ID, user(9L));

        // 返回的必须是认领**之后**重新读出来的那一份:直接把之前读到的无主实例返回去,
        // 前端会拿到一个 unclaimed=true 的响应,界面上表现为"认领了但按钮还在"。
        assertThat(result.isUnclaimed()).isFalse();
        assertThat(result.owner().userId()).isEqualTo(9L);
        verify(skillRepository).claimOwnership(SKILL_ID, 9L);
    }

    @Test
    @DisplayName("已有归属人时报 409,并且不去写库")
    void should_rejectWithConflict_when_skillAlreadyClaimed() {
        when(skillRepository.findDetailById(SKILL_ID)).thenReturn(Optional.of(claimedSkill(5L)));

        assertThatThrownBy(() -> service().claim(SKILL_ID, user(9L)))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> assertThat(((ResourceConflictException) ex).code()).isEqualTo("SKILL_ALREADY_CLAIMED"))
                .hasMessageContaining("李四");

        verify(skillRepository, never()).claimOwnership(anyLong(), anyLong());
    }

    @Test
    @DisplayName("比较并交换失败(被人抢先)时报 409 —— 不能把 0 行受影响当成成功")
    void should_rejectWithConflict_when_compareAndSetLoses() {
        // 读到的时候还是无主,写的时候已经被别人抢走:这正是并发认领的真实形态,
        // 前一道判断放行了它,只有仓储那一道能拦住。
        when(skillRepository.findDetailById(SKILL_ID)).thenReturn(Optional.of(unclaimedSkill()));
        when(skillRepository.claimOwnership(SKILL_ID, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service().claim(SKILL_ID, user(9L)))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> assertThat(((ResourceConflictException) ex).code()).isEqualTo("SKILL_ALREADY_CLAIMED"))
                .hasMessageContaining("刚刚被其他人认领");
    }

    @Test
    @DisplayName("skill 不存在时报 404")
    void should_throwNotFound_when_skillMissing() {
        when(skillRepository.findDetailById(SKILL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().claim(SKILL_ID, user(9L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("认领成功会留一条审计事件 —— 归属变更等于权限面变更")
    void should_writeAuditEvent_when_claimSucceeds() {
        when(skillRepository.findDetailById(SKILL_ID))
                .thenReturn(Optional.of(unclaimedSkill()))
                .thenReturn(Optional.of(claimedSkill(9L)));
        when(skillRepository.claimOwnership(SKILL_ID, 9L)).thenReturn(true);

        service().claim(SKILL_ID, user(9L));

        verify(auditLog).info(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }
}
