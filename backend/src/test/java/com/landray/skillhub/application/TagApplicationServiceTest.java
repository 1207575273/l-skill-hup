package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.domain.exception.InvalidTagKindException;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
class TagApplicationServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TagApplicationService(tagRepository);
    }

    @Test
    @DisplayName("kind 为 null 时不筛选,把 null 原样传给仓储")
    void should_passNullKind_when_kindParamIsNull() {
        when(tagRepository.findAll(null)).thenReturn(List.of());

        service.listTags(null);

        verify(tagRepository).findAll(null);
    }

    @Test
    @DisplayName("kind 为空白字符串时同样视为不筛选")
    void should_passNullKind_when_kindParamIsBlank() {
        when(tagRepository.findAll(null)).thenReturn(List.of());

        service.listTags("  ");

        verify(tagRepository).findAll(null);
    }

    @Test
    @DisplayName("kind 合法时解析成枚举传给仓储")
    void should_parseKind_when_kindParamIsValid() {
        when(tagRepository.findAll(TagKind.CURATED)).thenReturn(List.of());

        service.listTags("curated");

        verify(tagRepository).findAll(TagKind.CURATED);
    }

    @Test
    @DisplayName("kind 不合法时抛出业务异常,不调用仓储")
    void should_throw_when_kindParamIsInvalid() {
        assertThatThrownBy(() -> service.listTags("SYSTEM")).isInstanceOf(InvalidTagKindException.class);
    }

    @Test
    @DisplayName("返回仓储查到的结果")
    void should_returnRepositoryResult() {
        Tag tag = new Tag(1L, "代码审查", TagKind.CURATED, "描述", 10, 1L, null, null);
        TagWithSkillCount item = new TagWithSkillCount(tag, 3L);
        when(tagRepository.findAll(null)).thenReturn(List.of(item));

        List<TagWithSkillCount> result = service.listTags(null);

        assertThat(result).containsExactly(item);
    }
}
