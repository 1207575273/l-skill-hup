import type { SkillVersionSummary } from '../api/types'
import { Button } from './Button'
import { CertificationBadge } from './CertificationBadge'
import { cx } from './classNames'
import { formatFileSize } from './skillDetailFormat'
import { formatDateOnly } from './textFormat'

export interface SkillVersionListProps {
  readonly versions: readonly SkillVersionSummary[]
  /** 当前用于"快速接入"的版本 id;默认是最新版本,用户可以选中历史版本改用它。 */
  readonly selectedVersionId: number | null
  readonly onSelectVersion: (versionId: number) => void
}

/**
 * skill 版本列表(业务建模文档 5.16):版本号、大小、文件数、发布时间、**各自的认证状态**——
 * 认证挂在版本上不是挂在 skill 上(3.6),所以每一行都要单独取自己的 `certification`/`certifyNote`,
 * 不能复用页面顶部的认证徽章。
 *
 * 选中某一行会把它交给调用方(详情页把它接到 `QuickInstallMenu` 的 `versionId`),
 * 从而让"选中历史版本时也能对该版本生成安装单"成立。
 */
export function SkillVersionList({
  versions,
  selectedVersionId,
  onSelectVersion,
}: SkillVersionListProps) {
  return (
    <ul aria-label="版本列表" className="flex flex-col gap-2">
      {versions.map((version) => {
        const isSelected = version.id === selectedVersionId
        return (
          <li
            key={version.id}
            className={cx(
              'flex flex-wrap items-center justify-between gap-3 rounded-control border p-3',
              isSelected
                ? 'border-brand-600 bg-brand-50 dark:border-brand-500 dark:bg-brand-700/10'
                : 'border-gray-200 dark:border-gray-800',
            )}
          >
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm font-medium text-gray-900 dark:text-gray-100">
                  v{version.version}
                </span>
                {isSelected && (
                  <span className="text-xs font-medium text-brand-600 dark:text-brand-400">
                    当前选中
                  </span>
                )}
              </div>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {formatFileSize(version.sizeBytes)} · {version.fileCount} 个文件 ·{' '}
                {formatDateOnly(version.createdAt)}
              </p>
              <CertificationBadge
                certification={version.certification}
                certifyNote={version.certifyNote}
              />
            </div>
            <Button
              variant={isSelected ? 'secondary' : 'ghost'}
              size="sm"
              disabled={isSelected}
              aria-label={`选择版本 ${version.version}`}
              onClick={() => onSelectVersion(version.id)}
            >
              {isSelected ? '已选中' : '选择此版本'}
            </Button>
          </li>
        )
      })}
    </ul>
  )
}
