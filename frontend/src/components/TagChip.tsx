import type { Tag } from '../api/types'

export interface TagChipProps {
  readonly tag: Pick<Tag, 'id' | 'name' | 'kind'>
}

/**
 * 标签的视觉编码(展示约定 7.2):CURATED 不带 `#`、实心底色,排在最前、是导航
 * 入口;FREE 带 `#`、纯文字、更弱化。两者靠符号区分,不需要图例。
 */
export function TagChip({ tag }: TagChipProps) {
  if (tag.kind === 'CURATED') {
    return (
      <span className="inline-flex items-center rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700 dark:bg-brand-700/20 dark:text-brand-100">
        {tag.name}
      </span>
    )
  }

  return (
    <span className="inline-flex items-center px-1 text-xs text-gray-500 dark:text-gray-400">
      #{tag.name}
    </span>
  )
}
