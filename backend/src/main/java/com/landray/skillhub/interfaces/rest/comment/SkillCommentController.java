package com.landray.skillhub.interfaces.rest.comment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.SkillCommentApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.RequireLogin;
import com.landray.skillhub.shared.model.PageResult;

/**
 * 评论接口(业务建模文档 5.11 / 6.2)。
 *
 * <p>读侧匿名可用,写侧一律 {@link RequireLogin};"能不能删这一条"是<b>资源级</b>判断(取决于你是不是作者),
 * 在 application 层完成,controller 不做业务判断(backend/CLAUDE.md 第一节)。
 *
 * <p><b>删除挂在 {@code /comments/{id}/delete} 而不是 {@code /skills/{skillId}/comments/{id}/delete}</b>:
 * commentId 全局唯一,路径里再带一个 skillId 只会多出一个"两者对不上时怎么办"的分支,而那个分支除了让人
 * 犯错没有别的用途。发表则必须带 skillId —— 那时评论还不存在,skill 是它唯一的归属依据。
 */
@RestController
@RequestMapping("/api/v1")
public class SkillCommentController {

    private final SkillCommentApplicationService skillCommentApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public SkillCommentController(
            SkillCommentApplicationService skillCommentApplicationService,
            CurrentUserAccessor currentUserAccessor) {
        this.skillCommentApplicationService = skillCommentApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    /** 评论列表,顶级分页,回复随顶级返回。匿名可用。 */
    @GetMapping("/skills/{skillId}/comments")
    public PageResult<SkillCommentResponse> listComments(
            @PathVariable long skillId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageResult<SkillComment> result = skillCommentApplicationService.listComments(skillId, page, size);
        return result.map(SkillCommentResponse::from);
    }

    /** 发表评论或回复(需登录)。 */
    @RequireLogin
    @PostMapping("/skills/{skillId}/comments")
    public SkillCommentResponse addComment(@PathVariable long skillId, @RequestBody AddCommentRequest request) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        SkillComment created = skillCommentApplicationService.addComment(
                skillId, request.parentId(), request.content(), actingUser);
        return SkillCommentResponse.from(created);
    }

    /**
     * 软删除评论(作者本人 / ADMIN)。
     *
     * <p>返回 204 而不是删除后的评论:调用方要的结果是"这条不再显示",而回一份 {@code deleted: true} 的空壳
     * 只会让前端多一条"要不要用它替换本地那条"的判断。列表刷新是更直接的下一步。
     */
    @RequireLogin
    @PostMapping("/comments/{commentId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable long commentId) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        skillCommentApplicationService.deleteComment(commentId, actingUser);
    }
}
