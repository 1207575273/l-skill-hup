import { useState } from 'react'
import type { ApiError } from '../api/client'
import { Button } from './Button'
import { WarningTriangleIcon } from './icons'

export interface ErrorBannerProps {
  /** 只从 `ApiError` 取字段,不自己解析响应体(项目宪法:请求一律走 api/ 层)。 */
  readonly error: ApiError
  readonly onRetry?: () => void
}

const COPIED_LABEL_DURATION_MS = 2000

/**
 * 错误展示,基础件之一,也是这个项目排障的第一入口(frontend/CLAUDE.md 第七节)。
 * `traceId` 必须能被看到并复制 —— 用户报障时贴的就是这一串。
 */
export function ErrorBanner({ error, onRetry }: ErrorBannerProps) {
  const [copied, setCopied] = useState(false)

  const handleCopyTraceId = () => {
    void navigator.clipboard.writeText(error.traceId).then(() => {
      setCopied(true)
      window.setTimeout(() => setCopied(false), COPIED_LABEL_DURATION_MS)
    })
  }

  return (
    <div
      role="alert"
      className="flex flex-col gap-3 rounded-card border border-danger-500 bg-danger-50 p-4 dark:border-danger-700 dark:bg-danger-700/10"
    >
      <div className="flex items-start gap-2">
        <WarningTriangleIcon className="mt-0.5 h-5 w-5 shrink-0 text-danger-600 dark:text-danger-300" />
        <div className="flex-1">
          <p className="text-sm font-medium text-danger-700 dark:text-danger-100">
            {error.message}
          </p>
          <p className="mt-1 text-xs text-danger-600 dark:text-danger-300">
            traceId:{' '}
            <code className="rounded bg-white px-1 py-0.5 font-mono dark:bg-gray-900">
              {error.traceId}
            </code>
          </p>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="secondary" size="sm" onClick={handleCopyTraceId}>
          {copied ? '已复制' : '复制 traceId'}
        </Button>
        {onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry}>
            重试
          </Button>
        )}
      </div>
    </div>
  )
}
