import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cx } from './classNames'
import { Spinner } from './Spinner'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'sm' | 'md'

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  readonly variant?: ButtonVariant
  readonly size?: ButtonSize
  readonly iconLeft?: ReactNode
  readonly iconRight?: ReactNode
  /** 加载态:禁用交互并把 iconLeft 换成 Spinner,不影响文案。 */
  readonly loading?: boolean
  readonly fullWidth?: boolean
  readonly children: ReactNode
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-brand-100 disabled:text-brand-700/50',
  secondary:
    'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50 disabled:text-gray-400 dark:bg-gray-900 dark:text-gray-200 dark:border-gray-700 dark:hover:bg-gray-800',
  ghost:
    'bg-transparent text-gray-700 hover:bg-gray-100 disabled:text-gray-400 dark:text-gray-200 dark:hover:bg-gray-800',
  danger:
    'bg-danger-600 text-white hover:bg-danger-700 disabled:bg-danger-50 disabled:text-danger-700/50',
}

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-sm gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
}

/**
 * 基础按钮,四个语义变体覆盖本轮已知场景:primary(主操作,如"快速接入")、
 * secondary(次操作,如"查看详情"、Modal 里的"取消")、ghost(低调操作)、
 * danger(破坏性操作,如未来的下架/撤销认证)。
 *
 * 默认 `type="button"`:大多数按钮不是表单提交按钮,漏写 type 在 `<form>` 里会
 * 意外触发提交,这是比多写一个 prop 更贵的坑。
 */
export function Button({
  variant = 'primary',
  size = 'md',
  iconLeft,
  iconRight,
  loading = false,
  fullWidth = false,
  disabled,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      type={type}
      disabled={disabled || loading}
      className={cx(
        'inline-flex items-center justify-center rounded-control font-medium transition-colors',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600',
        'disabled:cursor-not-allowed',
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        fullWidth && 'w-full',
      )}
    >
      {loading ? <Spinner size="sm" label="处理中" /> : iconLeft}
      {children}
      {!loading && iconRight}
    </button>
  )
}
