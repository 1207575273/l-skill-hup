package com.landray.skillhub.interfaces.rest.tag;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.TagApplicationService;
import com.landray.skillhub.application.TagGovernanceApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.RequireAdmin;

/**
 * tag 接口:读侧匿名可用,治理侧(建 / 改 / 删)ADMIN 专属。
 *
 * <p><b>{@link RequireAdmin} 标在方法上而不是类上</b>:同一组资源里 {@code GET /tags} 与 {@code GET
 * /tags/suggest} 必须匿名可达(列表页与上传页都要用),标在类上会把它们一起关掉。
 *
 * <p>列表不分页——业务建模文档通用约定第 8 条:标签几十条量级,一次全量返回即可,分页反而增加前端复杂度。
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagApplicationService tagApplicationService;
    private final TagGovernanceApplicationService tagGovernanceApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public TagController(
            TagApplicationService tagApplicationService,
            TagGovernanceApplicationService tagGovernanceApplicationService,
            CurrentUserAccessor currentUserAccessor) {
        this.tagApplicationService = tagApplicationService;
        this.tagGovernanceApplicationService = tagGovernanceApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public List<TagResponse> listTags(@RequestParam(required = false) String kind) {
        return tagApplicationService.listTags(kind).stream().map(TagResponse::from).toList();
    }

    /** 输入联想。匿名可用:上传页在用户打 FREE 标签时逐字调用,那时可能还没登录到写接口这一步。 */
    @GetMapping("/suggest")
    public List<TagResponse> suggest(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer limit) {
        return tagGovernanceApplicationService.suggest(keyword, kind, limit).stream()
                .map(TagResponse::from)
                .toList();
    }

    @PostMapping
    @RequireAdmin
    public TagResponse createTag(@Valid @RequestBody CreateTagRequest request) {
        AuthenticatedUser admin = currentUserAccessor.require();
        Tag created = tagGovernanceApplicationService.createCuratedTag(
                request.name(), request.description(), request.sortOrder(), admin);
        // 刚建出来的词条必然零引用,不再多发一次 GROUP BY 查询去确认这件事。
        return TagResponse.ofNewTag(created);
    }

    @PostMapping("/{id}/update")
    @RequireAdmin
    public TagResponse updateTag(@PathVariable long id, @Valid @RequestBody UpdateTagRequest request) {
        AuthenticatedUser admin = currentUserAccessor.require();
        Tag updated = tagGovernanceApplicationService.updateTag(
                id,
                request.name(),
                request.kind(),
                request.description(),
                request.sortOrder(),
                request.confirmed() != null && request.confirmed(),
                admin);
        // skillCount 这里给不出准确值(kind 转换不改引用数,但改名不影响、迁移才影响),前端拿到响应后
        // 会重新拉一次列表,不在这条响应里编一个可能过期的数字。
        return TagResponse.ofNewTag(updated);
    }

    @PostMapping("/{id}/delete")
    @RequireAdmin
    public ResponseEntity<Void> deleteTag(
            @PathVariable long id, @RequestBody(required = false) DeleteTagRequest request) {
        AuthenticatedUser admin = currentUserAccessor.require();
        Long migrateToTagId = request == null ? null : request.migrateToTagId();
        tagGovernanceApplicationService.deleteTag(id, migrateToTagId, admin);
        return ResponseEntity.noContent().build();
    }
}
