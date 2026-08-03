import type { ReactNode } from 'react'

export interface EmptyStateProps {
  readonly title: string
  readonly description?: string
  /** 可选操作插槽,如列表页无结果时的"清空筛选条件"按钮。 */
  readonly action?: ReactNode
  readonly icon?: ReactNode
}

/** 空态展示,基础件之一。列表无结果、搜索无匹配、详情页找不到 skill 时复用。 */
export function EmptyState({ title, description, action, icon }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-card border border-dashed border-gray-300 px-6 py-12 text-center dark:border-gray-700">
      {icon}
      <p className="text-base font-medium text-gray-900 dark:text-gray-100">{title}</p>
      {description && <p className="text-sm text-gray-500 dark:text-gray-400">{description}</p>}
      {action}
    </div>
  )
}
