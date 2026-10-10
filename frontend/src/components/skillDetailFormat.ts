import type { DeclaredOwner, SkillOwner, SkillTag } from '../api/types'

/**
 * skill 详情页专用的纯函数格式化工具。**不改 `textFormat.ts` / `SkillCard.tsx`**——那是既有文件,
 * 本轮任务明令不许碰;`SkillCard.tsx` 里的 `formatOwnerLabel`/`sortTagsForDisplay` 又是未导出的私有
 * 函数,拿不到,所以这里按同一套展示约定(7.1/7.2)单独维护一份。
 */

/**
 * 路由参数 `:id` 解析成 skill id。非数字、非整数、非正数一律视为无效,返回 `null` 交给页面
 * 渲染"无效编号"态,而不是把 `NaN`/负数传给 `useSkillDetail` 触发一次注定失败的请求。
 */
export function parseSkillIdParam(rawId: string | undefined): number | null {
  if (!rawId) {
    return null
  }
  const parsed = Number(rawId)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

/**
 * 归属人展示,与展示约定 7.1"工号必须跟在展示名后面"一致。`owner` 为 `null` 时降级显示
 * "归属未知"而不是崩掉——库里 `owner_user_id` 是 NOT NULL,真出现只可能是数据异常。
 */
/**
 * 归属标签的统一出口:已认领显示真实归属人,未认领显示"未认领"并把自称归属标成"自称"。
 *
 * **自称归属刻意不套 `@` 前缀**。`@张三(80380486)` 这种形态在这个产品里一直意味着"有账号背书的人",
 * 把一段谁都能填的自由文本渲染成同样的样子,等于在界面上伪造可信度 —— 而使用者正是靠这个判断要不要
 * 安装一个会被 Agent 执行的包。
 */
export function formatSkillOwnershipLabel(
  owner: SkillOwner | null,
  declaredOwner: DeclaredOwner | null,
  unclaimed: boolean,
): string {
  if (!unclaimed) {
    return formatSkillOwnerLabel(owner)
  }
  if (declaredOwner === null) {
    return '未认领'
  }
  const who = declaredOwner.employeeNo
    ? `${declaredOwner.name}(${declaredOwner.employeeNo})`
    : declaredOwner.name
  return `未认领 · 自称 ${who}`
}

export function formatSkillOwnerLabel(owner: SkillOwner | null): string {
  if (owner === null) {
    return '@归属未知'
  }
  return owner.employeeNo ? `@${owner.displayName}(${owner.employeeNo})` : `@${owner.displayName}`
}

const BYTE_UNITS = ['B', 'KB', 'MB', 'GB'] as const

/** 版本列表里的制品体积,人类可读。0 或负数(理论上不会出现,但保守处理)一律显示 "0 B"。 */
export function formatFileSize(bytes: number): string {
  if (bytes <= 0) {
    return '0 B'
  }
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), BYTE_UNITS.length - 1)
  const unit = BYTE_UNITS[exponent] ?? 'B'
  const value = bytes / 1024 ** exponent
  return exponent === 0 ? `${value} ${unit}` : `${value.toFixed(2)} ${unit}`
}

/**
 * 标签展示排序,展示约定 7.2:CURATED 排在最前(不带 `#`,是导航入口),FREE 在后(带 `#`),
 * 同类内按 `sortOrder`。与 `SkillCard.tsx` 里同名私有逻辑保持一致的排序规则。
 */
export function sortSkillTagsForDisplay(tags: readonly SkillTag[]): SkillTag[] {
  return [...tags].sort((a, b) => {
    if (a.kind !== b.kind) {
      return a.kind === 'CURATED' ? -1 : 1
    }
    return a.sortOrder - b.sortOrder
  })
}

const MAX_FREE_TAG_NAME_LENGTH = 16

/**
 * 把"编辑元数据"表单里逗号分隔的自由标签输入解析成去重后的名称数组。
 * 中英文逗号都认(用户手感不同),按应用层归一化的长度上限(5.7)截断单个标签名——
 * 这只是即时反馈,真正的校验服务端会重做一遍(项目宪法第七节)。
 */
export function parseFreeTagNamesInput(raw: string): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const part of raw.split(/[,，]+/)) {
    const trimmed = part.trim().slice(0, MAX_FREE_TAG_NAME_LENGTH)
    if (trimmed && !seen.has(trimmed)) {
      seen.add(trimmed)
      result.push(trimmed)
    }
  }
  return result
}
