package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.domain.exception.InvalidSkillSortException;
import com.landray.skillhub.domain.exception.InvalidSkillSourceException;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.model.PageResult;

/**
 * Mockito mock 掉仓储接口,只验编排逻辑与参数规范化——不起 Spring、不碰 DB(backend/CLAUDE.md 8.1)。
 */
@ExtendWith(MockitoExtension.class)
class SkillApplicationServiceTest {

    @Mock
    private SkillRepository skillRepository;

    private SkillApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SkillApplicationService(skillRepository);
    }

    private Skill sampleSkill() {
        return new Skill(
                1L,
                new SkillName("code-review"),
                "代码审查助手",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(2L, "张三", "80380486"),
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
    @DisplayName("page 为 null 时默认第 1 页")
    void should_defaultPageToOne_when_pageParamIsNull() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("page 小于 1 时回落到第 1 页,而不是把非法值原样传给仓储")
    void should_defaultPageToOne_when_pageParamIsBelowMinimum() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, 0, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("size 为 null 时默认 20")
    void should_defaultSizeToTwenty_when_sizeParamIsNull() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("size 超过 100 时被夹到 100,不是拒绝请求")
    void should_clampSizeToMaximum_when_sizeParamExceedsLimit() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 100));

        service.listSkills(null, null, false, null, null, null, 1, 500);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size 小于等于 0 时回落到默认值 20")
    void should_defaultSizeToTwenty_when_sizeParamIsNotPositive() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, 1, 0);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("source 参数解析成功时传给仓储的是解析后的枚举")
    void should_parseSourceEnum_when_sourceParamIsValid() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills("internal", null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(SkillSource.INTERNAL);
    }

    @Test
    @DisplayName("source 参数不合法时在到达仓储前就抛出业务异常")
    void should_throwBeforeCallingRepository_when_sourceParamIsInvalid() {
        assertThatThrownBy(() -> service.listSkills("nowhere", null, false, null, null, null, null, null))
                .isInstanceOf(InvalidSkillSourceException.class);
        verifyNoInteractions(skillRepository);
    }

    @Test
    @DisplayName("sort 参数不合法时在到达仓储前就抛出业务异常")
    void should_throwBeforeCallingRepository_when_sortParamIsInvalid() {
        assertThatThrownBy(() -> service.listSkills(null, null, false, null, null, "popularity", null, null))
                .isInstanceOf(InvalidSkillSortException.class);
        verifyNoInteractions(skillRepository);
    }

    @Test
    @DisplayName("sort 参数为空时默认 LATEST")
    void should_defaultSortToLatest_when_sortParamIsNull() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().sort()).isEqualTo(SkillSortOption.LATEST);
    }

    @Test
    @DisplayName("详情命中时直接返回领域模型")
    void should_returnSkill_when_detailFound() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));

        Skill result = service.getSkillDetail(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("详情未命中时抛出 404 语义的资源不存在异常")
    void should_throwResourceNotFound_when_detailMissing() {
        when(skillRepository.findDetailById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSkillDetail(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).httpStatus())
                        .isEqualTo(404));
    }

    @Test
    @DisplayName("版本列表:skill 不存在时抛出 404,不再去查版本表")
    void should_throwResourceNotFound_when_listingVersionsForMissingSkill() {
        when(skillRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.listVersions(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("版本列表:skill 存在时返回仓储查到的版本")
    void should_returnVersions_when_skillExists() {
        when(skillRepository.existsById(1L)).thenReturn(true);
        SkillVersion version = new SkillVersion(
                1L, 1L, "1.0.0", 100L, 1, List.of(), null, null, null, Certification.NONE, null, null, null, 1L,
                null);
        when(skillRepository.findVersionsBySkillId(1L)).thenReturn(List.of(version));

        List<SkillVersion> result = service.listVersions(1L);

        assertThat(result).containsExactly(version);
    }
}
