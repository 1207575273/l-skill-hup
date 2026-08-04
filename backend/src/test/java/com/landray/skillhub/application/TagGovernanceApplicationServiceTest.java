package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.exception.TagDeleteRequiresMigrationException;
import com.landray.skillhub.domain.exception.TagKindConversionException;
import com.landray.skillhub.domain.exception.TagMigrationTargetInvalidException;
import com.landray.skillhub.domain.exception.TagNameInvalidException;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagName;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.TagRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

/**
 * 标签治理的编排逻辑。mock 掉仓储,只验规则本身(backend/CLAUDE.md 8.1)。
 *
 * <p>这里验的每一条数据库都拦不住:kind 转换的两道校验、迁移目标的 kind 一致性、转成 FREE 时清空
 * description/sortOrder。漏一条不会报错,只会让数据悄悄变得不自洽。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagGovernanceApplicationServiceTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(9L, UserRole.ADMIN, 1L, 1L);

    @Mock
    private TagRepository tagRepository;

    @Mock
    private AuditLog auditLog;

    private TagGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TagGovernanceApplicationService(tagRepository, auditLog);
    }

    private static Tag curated(long id, String name) {
        return new Tag(id, name, TagKind.CURATED, "描述", 10, 1L, null, null);
    }

    private static Tag free(long id, String name) {
        return new Tag(id, name, TagKind.FREE, null, 0, 1L, null, null);
    }

    // ---------- 新建 ----------

    @Test
    @DisplayName("新建 CURATED 词条时名字被归一化后落库")
    void should_normalizeName_when_creatingCuratedTag() {
        when(tagRepository.findByName("代码 审查")).thenReturn(Optional.empty());
        when(tagRepository.create(any(), any(), any(), anyInt(), anyLong())).thenReturn(curated(1L, "代码 审查"));

        service.createCuratedTag("  代码   审查  ", "评审清单", 20, ADMIN);

        ArgumentCaptor<TagName> name = ArgumentCaptor.forClass(TagName.class);
        verify(tagRepository).create(name.capture(), eq(TagKind.CURATED), eq("评审清单"), eq(20), eq(9L));
        assertThat(name.getValue().value()).isEqualTo("代码 审查");
    }

    /** 撞的是一个 FREE 词条也要拒——{@code uk_tag_name} 不分 kind,放它走到 insert 只会换成一条 500。 */
    @Test
    @DisplayName("新建时撞已有词条名返回 409,不落库")
    void should_rejectCreate_when_nameAlreadyTaken() {
        when(tagRepository.findByName("测试")).thenReturn(Optional.of(free(7L, "测试")));

        assertThatThrownBy(() -> service.createCuratedTag("测试", null, 0, ADMIN))
                .isInstanceOf(ResourceConflictException.class);

        verify(tagRepository, never()).create(any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("新建时名字超过 16 字符被 TagName 拦下")
    void should_rejectCreate_when_nameTooLong() {
        assertThatThrownBy(() -> service.createCuratedTag("一二三四五六七八九十一二三四五六七", null, 0, ADMIN))
                .isInstanceOf(TagNameInvalidException.class);
    }

    @Test
    @DisplayName("空白描述统一存 null,不留下空串")
    void should_storeNullDescription_when_descriptionIsBlank() {
        when(tagRepository.findByName("通用")).thenReturn(Optional.empty());
        when(tagRepository.create(any(), any(), any(), anyInt(), anyLong())).thenReturn(curated(1L, "通用"));

        service.createCuratedTag("通用", "   ", null, ADMIN);

        verify(tagRepository).create(any(), eq(TagKind.CURATED), eq(null), eq(0), eq(9L));
    }

    // ---------- 改名与描述 ----------

    @Test
    @DisplayName("改标签时名字没变不算重名冲突")
    void should_allowUpdate_when_nameUnchanged() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "测试")));
        when(tagRepository.findByName("测试")).thenReturn(Optional.of(curated(1L, "测试")));

        Tag updated = service.updateTag(1L, "测试", null, "新描述", 30, false, ADMIN);

        assertThat(updated.description()).isEqualTo("新描述");
        verify(tagRepository).update(eq(1L), any(), eq(TagKind.CURATED), eq("新描述"), eq(30));
    }

    @Test
    @DisplayName("改名撞到别的词条返回 409")
    void should_rejectUpdate_when_newNameBelongsToAnotherTag() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "测试")));
        when(tagRepository.findByName("文档")).thenReturn(Optional.of(curated(2L, "文档")));

        assertThatThrownBy(() -> service.updateTag(1L, "文档", null, null, 0, false, ADMIN))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    @DisplayName("kind 缺省表示不动它,不会被重置")
    void should_keepExistingKind_when_kindParamAbsent() {
        when(tagRepository.findById(5L)).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.findByName("随手标")).thenReturn(Optional.of(free(5L, "随手标")));

        service.updateTag(5L, "随手标", null, null, null, false, ADMIN);

        verify(tagRepository).update(eq(5L), any(), eq(TagKind.FREE), eq(null), eq(0));
    }

    @Test
    @DisplayName("改不存在的词条返回 404")
    void should_reject_when_updatingMissingTag() {
        when(tagRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTag(404L, "任意", null, null, 0, false, ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- kind 转换:两道校验 ----------

    @Test
    @DisplayName("FREE 转 CURATED 时,已挂满 3 个分类的 skill 存在就硬拒绝")
    void should_rejectConversion_when_someSkillWouldExceedCuratedLimit() {
        when(tagRepository.findById(5L)).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.findByName("随手标")).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.findSkillNamesAtCuratedLimit(eq(5L), eq(3), anyInt()))
                .thenReturn(List.of("skill-a", "skill-b"));

        assertThatThrownBy(() -> service.updateTag(5L, "随手标", "CURATED", null, 0, false, ADMIN))
                .isInstanceOf(TagKindConversionException.class)
                .satisfies(e -> assertThat(((TagKindConversionException) e).code())
                        .isEqualTo("TAG_KIND_CONVERSION_BLOCKED"));

        verify(tagRepository, never()).update(anyLong(), any(), any(), any(), anyInt());
    }

    /** 这个方向<b>没有</b>确认开关:溢出上限是硬约束,勾什么都不该放行。 */
    @Test
    @DisplayName("FREE 转 CURATED 的溢出拒绝不受 confirmed 影响")
    void should_stillRejectConversion_when_confirmedButLimitWouldOverflow() {
        when(tagRepository.findById(5L)).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.findByName("随手标")).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.findSkillNamesAtCuratedLimit(eq(5L), eq(3), anyInt())).thenReturn(List.of("skill-a"));

        assertThatThrownBy(() -> service.updateTag(5L, "随手标", "CURATED", null, 0, true, ADMIN))
                .isInstanceOf(TagKindConversionException.class);
    }

    @Test
    @DisplayName("FREE 转 CURATED 时没有 skill 会溢出就放行")
    void should_allowConversion_when_noSkillWouldExceedCuratedLimit() {
        when(tagRepository.findById(5L)).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.findByName("随手标")).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.findSkillNamesAtCuratedLimit(eq(5L), eq(3), anyInt())).thenReturn(List.of());

        service.updateTag(5L, "随手标", "CURATED", "补的描述", 40, false, ADMIN);

        verify(tagRepository).update(eq(5L), any(), eq(TagKind.CURATED), eq("补的描述"), eq(40));
    }

    @Test
    @DisplayName("CURATED 转 FREE 且仍有引用时,未确认先返回受影响清单")
    void should_askForConfirmation_when_convertingReferencedCuratedToFree() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "测试")));
        when(tagRepository.findByName("测试")).thenReturn(Optional.of(curated(1L, "测试")));
        when(tagRepository.countReferences(1L)).thenReturn(3L);
        when(tagRepository.findSkillNamesReferencing(eq(1L), anyInt())).thenReturn(List.of("a", "b", "c"));

        assertThatThrownBy(() -> service.updateTag(1L, "测试", "FREE", null, 0, false, ADMIN))
                .isInstanceOf(TagKindConversionException.class)
                .satisfies(e -> {
                    TagKindConversionException conversion = (TagKindConversionException) e;
                    assertThat(conversion.code()).isEqualTo("TAG_KIND_CONVERSION_NEEDS_CONFIRMATION");
                    assertThat(conversion.details()).containsEntry("affectedSkillCount", 3L);
                });

        verify(tagRepository, never()).update(anyLong(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("CURATED 转 FREE 带 confirmed=true 时放行")
    void should_allowConversion_when_confirmedByAdmin() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "测试")));
        when(tagRepository.findByName("测试")).thenReturn(Optional.of(curated(1L, "测试")));

        service.updateTag(1L, "测试", "FREE", "描述", 10, true, ADMIN);

        verify(tagRepository).update(eq(1L), any(), eq(TagKind.FREE), eq(null), eq(0));
    }

    /** 零引用时不该弹确认框:没有任何人受影响,弹了只是训练 ADMIN 无脑点确认。 */
    @Test
    @DisplayName("CURATED 转 FREE 但零引用时无需确认")
    void should_allowConversionWithoutConfirmation_when_noSkillReferencesTag() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "空分类")));
        when(tagRepository.findByName("空分类")).thenReturn(Optional.of(curated(1L, "空分类")));
        when(tagRepository.countReferences(1L)).thenReturn(0L);

        service.updateTag(1L, "空分类", "FREE", null, 0, false, ADMIN);

        verify(tagRepository).update(eq(1L), any(), eq(TagKind.FREE), eq(null), eq(0));
    }

    /**
     * 4.6 写死 description 与 sort_order "仅 CURATED 用"。转成 FREE 却把旧描述留在库里,再转回 CURATED
     * 时会诈尸出一段没人记得的文案。
     */
    @Test
    @DisplayName("转成 FREE 时清空 description 与 sortOrder,哪怕请求里带着值")
    void should_clearCuratedOnlyFields_when_convertingToFree() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "测试")));
        when(tagRepository.findByName("测试")).thenReturn(Optional.of(curated(1L, "测试")));

        Tag updated = service.updateTag(1L, "测试", "free", "还想留着的描述", 99, true, ADMIN);

        verify(tagRepository).update(eq(1L), any(), eq(TagKind.FREE), eq(null), eq(0));
        assertThat(updated.description()).isNull();
        assertThat(updated.sortOrder()).isZero();
    }

    // ---------- 删除 ----------

    @Test
    @DisplayName("删仍被引用的 CURATED 且没给 migrateToTagId 时返回 409")
    void should_rejectDelete_when_curatedStillReferencedWithoutMigration() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "测试")));
        when(tagRepository.countReferences(1L)).thenReturn(4L);
        when(tagRepository.findSkillNamesReferencing(eq(1L), anyInt())).thenReturn(List.of("a"));

        assertThatThrownBy(() -> service.deleteTag(1L, null, ADMIN))
                .isInstanceOf(TagDeleteRequiresMigrationException.class);

        verify(tagRepository, never()).deleteTag(anyLong());
    }

    @Test
    @DisplayName("零引用的 CURATED 不需要迁移目标,直接删")
    void should_deleteDirectly_when_curatedHasNoReference() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "空分类")));
        when(tagRepository.countReferences(1L)).thenReturn(0L);

        service.deleteTag(1L, null, ADMIN);

        verify(tagRepository).deleteTag(1L);
    }

    /** FREE 是用户随手打的,零引用时本来就会被自动清理,不必逼 ADMIN 指定迁移目标。 */
    @Test
    @DisplayName("仍被引用的 FREE 不强制迁移,直接断开引用后删")
    void should_deleteFreeTagWithReferences_when_noMigrationGiven() {
        when(tagRepository.findById(5L)).thenReturn(Optional.of(free(5L, "随手标")));
        when(tagRepository.countReferences(5L)).thenReturn(2L);

        service.deleteTag(5L, null, ADMIN);

        verify(tagRepository).deleteReferences(5L);
        verify(tagRepository).deleteTag(5L);
    }

    @Test
    @DisplayName("给了迁移目标时先迁引用再删词条")
    void should_migrateReferences_when_migrationTargetGiven() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "旧分类")));
        when(tagRepository.findById(2L)).thenReturn(Optional.of(curated(2L, "新分类")));
        when(tagRepository.countReferences(1L)).thenReturn(3L);

        service.deleteTag(1L, 2L, ADMIN);

        verify(tagRepository).migrateReferences(1L, 2L);
        verify(tagRepository).deleteTag(1L);
        // 迁移已经把旧引用行删干净了,不能再来一次全量 delete——那条语句此刻无害,但语义上是"我不确定迁移做完没有"。
        verify(tagRepository, never()).deleteReferences(anyLong());
    }

    @Test
    @DisplayName("迁移目标不存在时返回 400,不删任何东西")
    void should_rejectDelete_when_migrationTargetMissing() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "旧分类")));
        when(tagRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTag(1L, 2L, ADMIN))
                .isInstanceOf(TagMigrationTargetInvalidException.class);

        verify(tagRepository, never()).deleteTag(anyLong());
    }

    /**
     * kind 不一致的迁移是"看起来无损、实际有损"的那一类:CURATED 迁到 FREE 后那批 skill 表面上还有标签,
     * 实际已经整体从导航消失。
     */
    @Test
    @DisplayName("迁移目标 kind 与被删词条不一致时返回 400")
    void should_rejectDelete_when_migrationTargetKindMismatches() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "旧分类")));
        when(tagRepository.findById(5L)).thenReturn(Optional.of(free(5L, "随手标")));

        assertThatThrownBy(() -> service.deleteTag(1L, 5L, ADMIN))
                .isInstanceOf(TagMigrationTargetInvalidException.class);

        verify(tagRepository, never()).migrateReferences(anyLong(), anyLong());
    }

    @Test
    @DisplayName("迁移目标是自己时返回 400——否则会先把引用迁没再把词条删掉")
    void should_rejectDelete_when_migrationTargetIsItself() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(curated(1L, "旧分类")));

        assertThatThrownBy(() -> service.deleteTag(1L, 1L, ADMIN))
                .isInstanceOf(TagMigrationTargetInvalidException.class);
    }

    @Test
    @DisplayName("删不存在的词条返回 404")
    void should_reject_when_deletingMissingTag() {
        when(tagRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTag(404L, null, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- 联想 ----------

    @Test
    @DisplayName("空关键词直接返回空列表,不打库")
    void should_returnEmpty_when_suggestKeywordIsBlank() {
        assertThat(service.suggest("   ", null, null)).isEmpty();

        verify(tagRepository, never()).suggest(any(), any(), anyInt());
    }

    @Test
    @DisplayName("联想关键词被归一化后传给仓储,limit 缺省为 10")
    void should_normalizeKeywordAndApplyDefaultLimit_when_suggesting() {
        when(tagRepository.suggest("code review", null, 10)).thenReturn(List.of());

        service.suggest("  code   review ", null, null);

        verify(tagRepository).suggest("code review", null, 10);
    }

    @Test
    @DisplayName("联想 limit 超过上限时被夹到 50")
    void should_clampSuggestLimit_when_callerAsksForTooMany() {
        service.suggest("code", null, 9999);

        verify(tagRepository).suggest("code", null, 50);
    }

    @Test
    @DisplayName("联想的 kind 参数解析后传给仓储")
    void should_passKindFilter_when_suggestingWithKind() {
        service.suggest("code", "curated", 5);

        verify(tagRepository).suggest("code", TagKind.CURATED, 5);
    }
}
