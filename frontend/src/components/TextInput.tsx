import { type InputHTMLAttributes, useId } from 'react'
import { cx } from './classNames'

export interface TextInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className' | 'size'> {
  readonly label: string
  readonly hint?: string
  readonly errorMessage?: string
}

/**
 * 受控输入框,`label` 必填并显式关联 `htmlFor`/`id`(用 `useId` 生成,不依赖调用方
 * 自己传 `id`),保证 `getByLabelText` 能查到,不用测试专用 class。
 */
export function TextInput({ label, hint, errorMessage, id, ...rest }: TextInputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = hint ? `${inputId}-hint` : undefined
  const errorId = errorMessage ? `${inputId}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-sm font-medium text-gray-700 dark:text-gray-300">
        {label}
      </label>
      <input
        {...rest}
        id={inputId}
        aria-invalid={errorMessage ? true : undefined}
        aria-describedby={describedBy}
        className={cx(
          'h-10 rounded-control border px-3 text-sm text-gray-900 outline-none transition-colors',
          'focus:border-brand-600 focus:ring-1 focus:ring-brand-600',
          'dark:bg-gray-900 dark:text-gray-100',
          errorMessage ? 'border-danger-500' : 'border-gray-300 dark:border-gray-700',
        )}
      />
      {hint && !errorMessage && (
        <p id={hintId} className="text-xs text-gray-500 dark:text-gray-400">
          {hint}
        </p>
      )}
      {errorMessage && (
        <p id={errorId} className="text-xs text-danger-600 dark:text-danger-400">
          {errorMessage}
        </p>
      )}
    </div>
  )
}
