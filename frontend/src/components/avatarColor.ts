/**
 * 卡片左上角首字母头像色块的取色(展示约定 7.1):"颜色由 name 哈希决定,稳定,
 * 不随机"。返回的是 `var(--color-avatar-N)` 这个 CSS 变量引用,而不是 Tailwind
 * 类名字符串 —— 类名要拼成 `bg-avatar-${n}`,Tailwind 的静态扫描器扫不到源码里
 * 拼出来的类名,产物里会被摇树摇掉;调色板本身定义在 styles/index.css 的 @theme。
 */

const AVATAR_PALETTE = [
  'var(--color-avatar-1)',
  'var(--color-avatar-2)',
  'var(--color-avatar-3)',
  'var(--color-avatar-4)',
  'var(--color-avatar-5)',
  'var(--color-avatar-6)',
  'var(--color-avatar-7)',
  'var(--color-avatar-8)',
] as const

const DEFAULT_AVATAR_COLOR = AVATAR_PALETTE[0]

/** djb2 变体,只求稳定分布,不需要密码学强度。 */
function hashSkillName(name: string): number {
  const hash = Array.from(name).reduce((acc, char) => (acc * 33) ^ char.charCodeAt(0), 5381)
  return Math.abs(hash)
}

export function pickAvatarColor(name: string): string {
  const index = hashSkillName(name) % AVATAR_PALETTE.length
  return AVATAR_PALETTE[index] ?? DEFAULT_AVATAR_COLOR
}
