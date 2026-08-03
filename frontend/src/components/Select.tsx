import { useId } from 'react'
import { cx } from './classNames'

export interface SelectOption<T extends string> {
  readonly value: T
  readonly label: string
}

export interface SelectProps<T extends string> {
  readonly label: string
  readonly value: T
  readonly onChange: (value: T) => void
  readonly options: ReadonlyArray<SelectOption<T>>
  readonly placeholder?: string
  readonly errorMessage?: string
  readonly disabled?: boolean
  readonly id?: string
}

/**
 * 泛型下拉选择,`T` 收窄到具体的字符串字面量联合(如 `ClientCode`/`ScopeCode`),
 * 调用方拿到的 `onChange` 值天然是收窄后的类型,不需要在业务代码里再做一次类型断言。
 */
export function Select<T extends string>({
  label,
  value,
  onChange,
  options,
  placeholder,
  errorMessage,
  disabled,
  id,
}: SelectProps<T>) {
  const generatedId = useId()
  const selectId = id ?? generatedId

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={selectId} className="text-sm font-medium text-gray-700 dark:text-gray-300">
        {label}
      </label>
      <select
        id={selectId}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as T)}
        className={cx(
          'h-10 rounded-control border bg-white px-3 text-sm text-gray-900 outline-none transition-colors',
          'focus:border-brand-600 focus:ring-1 focus:ring-brand-600',
          'disabled:cursor-not-allowed disabled:text-gray-400',
          'dark:bg-gray-900 dark:text-gray-100',
          errorMessage ? 'border-danger-500' : 'border-gray-300 dark:border-gray-700',
        )}
      >
        {placeholder && (
          <option value="" disabled hidden>
            {placeholder}
          </option>
        )}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {errorMessage && (
        <p className="text-xs text-danger-600 dark:text-danger-400">{errorMessage}</p>
      )}
    </div>
  )
}
