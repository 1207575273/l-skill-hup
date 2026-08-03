package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.domain.exception.TagSelectionInvalidException;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.domain.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
class SkillTagAssignmentServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private SkillRepository skillRepository;

    private SkillTagAssignmentService service;

    private SkillTagAssignmentService service() {
        return new SkillTagAssignmentService(tagRepository, skillRepository);
    }

    private Tag curatedTag(long id) {
        return new Tag(id, "分类" + id, TagKind.CURATED, "描述", 0, 1L, null, null);
    }

    private Tag freeTag(long id, String name) {
        return new Tag(id, name, TagKind.FREE, null, 0, 1L, null, null);
    }

    @Test
    @DisplayName("CURATED 数量为 0 时拒绝")
    void should_reject_when_curatedCountIsZero() {
        service = service();

        assertThatThrownBy(() -> service.resolveTagIds(List.of(), List.of(), 1L))
                .isInstanceOf(TagSelectionInvalidException.class);
    }

    @Test
    @DisplayName("CURATED 数量超过 3 时拒绝")
    void should_reject_when_curatedCountExceedsThree() {
        service = service();

        assertThatThrownBy(() -> service.resolveTagIds(List.of(1L, 2L, 3L, 4L), List.of(), 1L))
                .isInstanceOf(TagSelectionInvalidException.class);
    }

    @Test
    @DisplayName("FREE 数量超过 10 时拒绝")
    void should_reject_when_freeCountExceedsTen() {
        service = service();
        List<String> elevenNames = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> "标签" + i)
                .toList();

        assertThatThrownBy(() -> service.resolveTagIds(List.of(1L), elevenNames, 1L))
                .isInstanceOf(TagSelectionInvalidException.class);
    }

    @Test
    @DisplayName("引用不存在的 CURATED 标签 id 时拒绝")
    void should_reject_when_curatedTagIdDoesNotExist() {
        when(tagRepository.findByIds(List.of(1L))).thenReturn(List.of());
        service = service();

        assertThatThrownBy(() -> service.resolveTagIds(List.of(1L), List.of(), 1L))
                .isInstanceOf(TagSelectionInvalidException.class);
    }

    @Test
    @DisplayName("引用的标签 id 存在但 kind 不是 CURATED 时拒绝")
    void should_reject_when_referencedTagIsNotCurated() {
        when(tagRepository.findByIds(List.of(1L))).thenReturn(List.of(freeTag(1L, "误用")));
        service = service();

        assertThatThrownBy(() -> service.resolveTagIds(List.of(1L), List.of(), 1L))
                .isInstanceOf(TagSelectionInvalidException.class);
    }

    @Test
    @DisplayName("FREE 标签名超过 16 字符时拒绝")
    void should_reject_when_freeTagNameExceeds16Chars() {
        service = service();
        String longName = "一二三四五六七八九十一二三四五六七";

        assertThatThrownBy(() -> service.resolveTagIds(List.of(1L), List.of(longName), 1L))
                .isInstanceOf(TagSelectionInvalidException.class);
    }

    @Test
    @DisplayName("合法的 CURATED + FREE 组合能正确解析,FREE 不存在则自动创建")
    void should_resolveTagIds_when_selectionIsValid() {
        when(tagRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(curatedTag(1L), curatedTag(2L)));
        when(tagRepository.findOrCreateFreeTag("新标签", 9L)).thenReturn(freeTag(100L, "新标签"));
        service = service();

        List<Long> result = service.resolveTagIds(List.of(1L, 2L), List.of("新标签"), 9L);

        assertThat(result).containsExactly(1L, 2L, 100L);
    }

    @Test
    @DisplayName("FREE 标签名去首尾空白、折叠中间空白、去重")
    void should_normalizeAndDeduplicateFreeTagNames() {
        when(tagRepository.findByIds(List.of(1L))).thenReturn(List.of(curatedTag(1L)));
        when(tagRepository.findOrCreateFreeTag("代码 审查", 1L)).thenReturn(freeTag(50L, "代码 审查"));
        service = service();

        List<Long> result = service.resolveTagIds(List.of(1L), List.of("  代码  审查  ", "代码 审查"), 1L);

        assertThat(result).containsExactly(1L, 50L);
        verify(tagRepository, org.mockito.Mockito.times(1)).findOrCreateFreeTag(eq("代码 审查"), eq(1L));
    }

    @Test
    @DisplayName("替换标签时清理本次操作中失去全部引用的旧 FREE 标签")
    void should_cleanupOrphanFreeTags_when_replacingTags() {
        when(skillRepository.replaceTags(1L, List.of(1L, 2L))).thenReturn(List.of(1L, 3L, 4L));
        service = service();

        service.replaceSkillTags(1L, List.of(1L, 2L));

        verify(tagRepository).deleteFreeTagIfOrphaned(3L);
        verify(tagRepository).deleteFreeTagIfOrphaned(4L);
        verify(tagRepository, never()).deleteFreeTagIfOrphaned(1L);
    }
}
