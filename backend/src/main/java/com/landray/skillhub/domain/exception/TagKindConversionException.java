package com.landray.skillhub.domain.exception;

import java.util.List;
import java.util.Map;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * {@code kind} 转换被拦下(业务建模文档 5.7)。两个方向的性质不同,所以给两个 code,前端据此决定是"报错"
 * 还是"弹确认框":
 *
 * <ul>
 *   <li>{@code TAG_KIND_CONVERSION_BLOCKED} —— {@code FREE -> CURATED} 会让某些 skill 的 CURATED 数溢出上限。
 *       <b>这是硬拒绝</b>,ADMIN 得先去把那些 skill 的分类腾出位置。
 *   <li>{@code TAG_KIND_CONVERSION_NEEDS_CONFIRMATION} —— {@code CURATED -> FREE} 会让一批 skill 从导航里消失。
 *       CURATED 下限放宽到 0 之后这不再是硬约束,所以<b>从"拒绝"降级为"告知"</b>:把受影响清单摆出来,
 *       ADMIN 带 {@code confirmed=true} 再来一次就放行。
 * </ul>
 *
 * <p>两条都是 409:请求本身没写错,是当前数据状态与这个动作冲突。
 */
public class TagKindConversionException extends BusinessException {

    private static final String BLOCKED_CODE = "TAG_KIND_CONVERSION_BLOCKED";
    private static final String NEEDS_CONFIRMATION_CODE = "TAG_KIND_CONVERSION_NEEDS_CONFIRMATION";
    private static final int HTTP_STATUS_CONFLICT = 409;

    private TagKindConversionException(String code, String message, Map<String, Object> details) {
        super(code, message, HTTP_STATUS_CONFLICT, details);
    }

    /** {@code FREE -> CURATED}:{@code affectedSkills} 是已经挂满 CURATED 的 skill 名字样本。 */
    public static TagKindConversionException curatedLimitWouldOverflow(
            int curatedLimit, List<String> affectedSkills) {
        return new TagKindConversionException(
                BLOCKED_CODE,
                "转成 CURATED 会让 " + affectedSkills.size() + " 个 skill 的分类数超过上限 " + curatedLimit
                        + ",请先调整这些 skill 的分类",
                Map.of("curatedLimit", curatedLimit, "affectedSkills", affectedSkills));
    }

    /** {@code CURATED -> FREE}:{@code affectedSkillCount} 是全量,{@code affectedSkills} 只是能摆给人看的样本。 */
    public static TagKindConversionException needsConfirmation(
            long affectedSkillCount, List<String> affectedSkills) {
        return new TagKindConversionException(
                NEEDS_CONFIRMATION_CODE,
                "转成 FREE 会让 " + affectedSkillCount + " 个 skill 不再出现在这个分类的导航下,确认后再提交",
                Map.of("affectedSkillCount", affectedSkillCount, "affectedSkills", affectedSkills));
    }
}
