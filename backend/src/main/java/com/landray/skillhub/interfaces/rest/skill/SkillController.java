package com.landray.skillhub.interfaces.rest.skill;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.SkillApplicationService;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 只读接口:列表、详情、版本列表。匿名可用(业务建模文档 5.16 权限矩阵),不做任何鉴权。
 *
 * <p>薄控制器:只收参、调 application、转 DTO,不写业务判断、不碰 SQL(backend/CLAUDE.md 第一节)。
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillApplicationService skillApplicationService;

    public SkillController(SkillApplicationService skillApplicationService) {
        this.skillApplicationService = skillApplicationService;
    }

    @GetMapping
    public PageResult<SkillSummaryResponse> listSkills(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(defaultValue = "false") boolean certifiedOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageResult<Skill> result = skillApplicationService.listSkills(
                source, tagIds, certifiedOnly, keyword, ownerUserId, sort, page, size);
        return result.map(SkillSummaryResponse::from);
    }

    @GetMapping("/{id}")
    public SkillDetailResponse getSkill(@PathVariable long id) {
        Skill skill = skillApplicationService.getSkillDetail(id);
        return SkillDetailResponse.from(skill);
    }

    @GetMapping("/{id}/versions")
    public List<SkillVersionResponse> getVersions(@PathVariable long id) {
        return skillApplicationService.listVersions(id).stream()
                .map(SkillVersionResponse::from)
                .toList();
    }
}
