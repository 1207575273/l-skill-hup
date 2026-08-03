package com.landray.skillhub.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.repository.TagRepository;

/** tag 读侧用例编排。事务边界只在这一层(backend/CLAUDE.md 第五节)。 */
@Service
public class TagApplicationService {

    private final TagRepository tagRepository;

    public TagApplicationService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<TagWithSkillCount> listTags(String kindParam) {
        TagKind kind = kindParam == null || kindParam.isBlank() ? null : TagKind.fromParam(kindParam);
        return tagRepository.findAll(kind);
    }
}
