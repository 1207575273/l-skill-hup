import { useId } from 'react'
import type { Tag } from '../api/types'
import { cx } from './classNames'

export interface CuratedTagPickerProps {
  readonly tags: readonly Tag[]
  readonly value: readonly number[]
  readonly onChange: (value: number[]) => void
  /** 上限(业务建模 5.7:CURATED 每个 skill 至多 3 个)。达到上限后未选中的选项禁用,不是隐藏。 */
  readonly max: number
  readonly errorMessage?: string
}

/**
 * CURATED 标签多选(业务建模 5.7:每个 skill 必须 1~3 个)。用原生 checkbox 而不是自制
 * 下拉多选——`getByRole('checkbox', { name })` 天然可测,键盘可达性也不需要额外实现。
 */
export function CuratedTagPicker({
  tags,
  value,
  onChange,
  max,
  errorMessage,
}: CuratedTagPickerProps) {
  const errorId = useId()

  function toggle(tagId: number) {
    if (value.includes(tagId)) {
      onChange(value.filter((id) => id !== tagId))
      return
    }
    if (value.length >= max) {
      return
    }
    onChange([...value, tagId])
  }

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-medium text-gray-700 dark:text-gray-300">分类标签</legend>
      <div className="flex flex-wrap gap-2" aria-describedby={errorMessage ? errorId : undefined}>
        {tags.map((tag) => {
          const checked = value.includes(tag.id)
          const disabledByLimit = !checked && value.length >= max
          return (
            <label
              key={tag.id}
              className={cx(
                'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs',
                checked
                  ? 'border-brand-600 bg-brand-50 text-brand-700 dark:bg-brand-700/20 dark:text-brand-100'
                  : 'border-gray-300 text-gray-600 dark:border-gray-700 dark:text-gray-300',
                disabledByLimit && 'cursor-not-allowed opacity-50',
              )}
            >
              <input
                type="checkbox"
                checked={checked}
                disabled={disabledByLimit}
                onChange={() => toggle(tag.id)}
                className="sr-only"
              />
              {tag.name}
            </label>
          )
        })}
      </div>
      {errorMessage && (
        <p id={errorId} className="text-xs text-danger-600 dark:text-danger-400">
          {errorMessage}
        </p>
      )}
    </fieldset>
  )
}
