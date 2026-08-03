/**
 * SkillCard 摘要与更新日期的小格式化函数。纯函数,不放进 lib/ —— lib/ 由并行任务
 * 维护(skill 包读取/校验/打包),这两个是卡片渲染专用的展示格式化,与那套逻辑无关。
 */

/**
 * 卡片摘要截断(展示约定 7.1):`cardExcerpt` 后端已截到 200 字符且不带省略号,
 * 前端再截到 `maxLength`(100)并自行加省略号。用单字符 `…` 而不是三个点。
 */
export function truncateWithEllipsis(text: string, maxLength: number): string {
  if (text.length <= maxLength) {
    return text
  }
  return `${text.slice(0, maxLength)}…`
}

/**
 * 卡片底部的更新日期只显示到天(展示约定 7.1)。输入是后端返回的 ISO 日期时间
 * 字符串;解析失败时原样返回,不让一条脏数据把整张卡片崩掉。
 */
export function formatDateOnly(isoDateTime: string): string {
  const parsed = new Date(isoDateTime)
  if (Number.isNaN(parsed.getTime())) {
    return isoDateTime
  }
  return parsed.toISOString().slice(0, 10)
}

/**
 * 精确到分钟的时间。评论区专用 —— 那里同一天会有多条,只显示日期就分不出先后,
 * 而"谁先说的"恰恰是读一段讨论时最需要的信息。
 *
 * 与 {@link formatDateOnly} 一样按 UTC 输出而不是本地时区:全站口径统一,
 * 混用会让同一份数据在两处显示成不同的时间,那种不一致比"时区不对"更让人困惑。
 * 解析失败时原样返回,不让一条脏数据把整个列表崩掉。
 */
export function formatDateTime(isoDateTime: string): string {
  const parsed = new Date(isoDateTime)
  if (Number.isNaN(parsed.getTime())) {
    return isoDateTime
  }
  return parsed.toISOString().slice(0, 16).replace('T', ' ')
}
