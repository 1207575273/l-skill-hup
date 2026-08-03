import { cx } from './classNames'

export type SpinnerSize = 'sm' | 'md' | 'lg'

export interface SpinnerProps {
  readonly size?: SpinnerSize
  /** 供屏幕阅读器朗读,默认"加载中"。视觉上不显示文字,只显示转圈的圆环。 */
  readonly label?: string
}

const SIZE_CLASSES: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-10 w-10 border-4',
}

/** 加载态指示器,基础件之一。`Button` 的 `loading` 态内部也复用它。 */
export function Spinner({ size = 'md', label = '加载中' }: SpinnerProps) {
  return (
    <span role="status" className="inline-flex items-center gap-2">
      <span
        aria-hidden="true"
        className={cx(
          'animate-spin rounded-full border-gray-300 border-t-brand-600 dark:border-gray-700 dark:border-t-brand-500',
          SIZE_CLASSES[size],
        )}
      />
      <span className="sr-only">{label}</span>
    </span>
  )
}
