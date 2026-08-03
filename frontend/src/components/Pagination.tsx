import { Button } from './Button'

export interface PaginationProps {
  /** 从 1 开始(接口契约 6.1)。 */
  readonly page: number
  readonly size: number
  readonly total: number
  readonly onPageChange: (page: number) => void
}

/**
 * 分页,基础件之一。展示约定没有规定具体形态,这里选最简单的"上一页 / 页码 /
 * 下一页",不做跳页输入框或页码按钮列表 —— 列表页的量级(几百条 skill)不需要。
 */
export function Pagination({ page, size, total, onPageChange }: PaginationProps) {
  if (total === 0) {
    return null
  }

  const totalPages = Math.max(1, Math.ceil(total / size))
  const canGoPrevious = page > 1
  const canGoNext = page < totalPages

  return (
    <nav aria-label="分页" className="flex items-center justify-center gap-3">
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onPageChange(page - 1)}
        disabled={!canGoPrevious}
      >
        上一页
      </Button>
      <span className="text-sm text-gray-600 dark:text-gray-400">
        第 {page} / {totalPages} 页 · 共 {total} 条
      </span>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onPageChange(page + 1)}
        disabled={!canGoNext}
      >
        下一页
      </Button>
    </nav>
  )
}
