/**
 * skill 名称校验(kebab-case)。
 *
 * 与后端 `com.landray.skillhub.domain.model.SkillName` 是**同一套规则的两份实现**,
 * 这不是重复代码,是两个不同性质的东西:
 *
 * - 这一份是**用户体验**:作者在上传前就看到问题,不用等服务器往返。
 * - 后端那一份是**安全边界**:防的是绕过前端直接打 API 的恶意上传。
 *
 * 因此改规则时两边必须同步改,并且**永远不能因为"前端已经校验过了"就让服务端略过**。
 *
 * 名称直接参与安装路径拼接(落盘到 `<skills 根目录>/<name>/`),
 * 所以字符集限制同时是一道路径穿越防线,放宽正则前务必想清楚。
 */

export const SKILL_NAME_MAX_LENGTH = 64

/** 单个连字符分隔的小写字母数字段。不允许 . / \ 空格 下划线 大写。 */
const KEBAB_CASE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export type ValidationResult =
  | { readonly valid: true }
  | { readonly valid: false; readonly reason: string }

export function validateSkillName(value: string | null | undefined): ValidationResult {
  if (value == null || value.trim() === '') {
    return { valid: false, reason: 'skill 名称不能为空' }
  }

  if (value.length > SKILL_NAME_MAX_LENGTH) {
    return {
      valid: false,
      reason: `skill 名称长度不得超过 ${SKILL_NAME_MAX_LENGTH} 个字符,实际 ${value.length}`,
    }
  }

  if (!KEBAB_CASE.test(value)) {
    return {
      valid: false,
      reason: `skill 名称必须是 kebab-case(小写字母、数字、单个连字符,不以连字符开头或结尾),实际为 ${value}`,
    }
  }

  return { valid: true }
}
