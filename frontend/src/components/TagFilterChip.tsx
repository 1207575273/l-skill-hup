import type { Tag } from '../api/types'
import { cx } from './classNames'

export interface TagFilterChipProps {
  readonly tag: Pick<Tag, 'id' | 'name' | 'kind'>
  readonly selected: boolean
  readonly onToggle: (tagId: number) => void
}

/**
 * 标签筛选面板用的可切换 chip。视觉编码延续展示约定 7.2(CURATED 不带 `#`、FREE 带 `#`),
 * 但 `TagChip` 是纯展示 `<span>`,没有可交互语义——筛选面板需要的是"选中了没有"这个状态,
 * 所以新建这个按钮版本,不改 `TagChip` 本身(它被列表卡片等只读场景复用,不该多出交互副作用)。
 */
export function TagFilterChip({ tag, selected, onToggle }: TagFilterChipProps) {
  const label = tag.kind === 'CURATED' ? tag.name : `#${tag.name}`

  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={() => onToggle(tag.id)}
      className={cx(
        'inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium transition-colors',
        selected
          ? 'border-brand-600 bg-brand-600 text-white'
          : 'border-gray-300 bg-white text-gray-600 hover:border-brand-300 hover:text-brand-600 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:border-brand-600 dark:hover:text-brand-400',
      )}
    >
      {label}
    </button>
  )
}
