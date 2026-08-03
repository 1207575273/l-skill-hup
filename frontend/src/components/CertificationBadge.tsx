import type { CertificationStatus } from '../api/types'
import { CheckCircleIcon, WarningTriangleIcon } from './icons'

export interface CertificationBadgeProps {
  readonly certification: CertificationStatus
  /**
   * REVOKED 时详情页要展示的撤销说明全文(展示约定 7.3)。卡片场景不传 ——
   * 卡片只露出徽章本身,note 只在详情页跟着徽章一起显示。
   */
  readonly certifyNote?: string | null
}

/**
 * 认证徽章三态(展示约定 7.3)。`NONE` 刻意返回 `null`:大多数 skill 都是这个状态,
 * 全挂"未认证"灰标只会制造视觉噪音,还会把"未认证"污名化成"有问题"。
 * 徽章的价值在稀缺,不在覆盖。
 */
export function CertificationBadge({ certification, certifyNote }: CertificationBadgeProps) {
  if (certification === 'NONE') {
    return null
  }

  if (certification === 'CERTIFIED') {
    return (
      <span className="inline-flex w-fit items-center gap-1 rounded-full bg-success-50 px-2 py-0.5 text-xs font-medium text-success-700 dark:bg-success-700/20 dark:text-success-100">
        <CheckCircleIcon className="h-3.5 w-3.5" />
        平台认证
      </span>
    )
  }

  return (
    <div className="flex flex-col gap-1">
      <span className="inline-flex w-fit items-center gap-1 rounded-full bg-danger-50 px-2 py-0.5 text-xs font-medium text-danger-700 dark:bg-danger-700/20 dark:text-danger-100">
        <WarningTriangleIcon className="h-3.5 w-3.5" />
        认证已撤销
      </span>
      {certifyNote && <p className="text-xs text-danger-600 dark:text-danger-300">{certifyNote}</p>}
    </div>
  )
}
