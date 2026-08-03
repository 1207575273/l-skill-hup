import type { SkillPrecheckExisting } from '../api/types'
import type { ModalAction } from './Modal'
import { Modal } from './Modal'

export interface DuplicateNameDialogProps {
  readonly open: boolean
  readonly existing: SkillPrecheckExisting
  /** precheck 算好的最终 name(业务建模 5.5),`null` 只在极端场景(响应字段缺失)出现。 */
  readonly suggestedName: string | null
  readonly onRenameAndCreate: () => void
  readonly onPublishAsNewVersion: () => void
  readonly onCancel: () => void
  /** 发布请求进行中时把按钮文案换成"发布中…",双击兜底交给调用方在业务逻辑里做。 */
  readonly submitting?: boolean
}

/**
 * 重名二选一弹窗(业务建模 3.8 / 5.1):改名新建 / 作为新版本。
 *
 * `existing.canPublishNewVersion` 由服务端算好——**前端只用它决定要不要显示"作为新版本"
 * 按钮,不自己判断权限**;真正的拦截仍在服务端(`docs/20260802224934_业务建模与接口契约.md`
 * 6.2)。这里额外给一句解释文案,避免用户困惑"为什么这个按钮不见了"。
 */
export function DuplicateNameDialog({
  open,
  existing,
  suggestedName,
  onRenameAndCreate,
  onPublishAsNewVersion,
  onCancel,
  submitting = false,
}: DuplicateNameDialogProps) {
  const actions: ModalAction[] = [
    {
      label: submitting ? '发布中…' : `改名新建(${suggestedName ?? '将自动生成新名称'})`,
      onClick: onRenameAndCreate,
    },
  ]

  if (existing.canPublishNewVersion) {
    actions.push({
      label: submitting ? '发布中…' : '作为新版本发布',
      onClick: onPublishAsNewVersion,
      variant: 'primary',
    })
  }

  actions.push({ label: '取消', onClick: onCancel, variant: 'ghost' })

  return (
    <Modal open={open} title="发现同名 skill" onClose={onCancel} actions={actions}>
      <p>
        已存在同名 skill <strong>{existing.name}</strong>(归属 {existing.ownerDisplayName}
        {existing.ownerEmployeeNo ? `,工号 ${existing.ownerEmployeeNo}` : ''},最新 v
        {existing.latestVersion})。
      </p>
      <p className="mt-2">选择改名新建一个独立的 skill,或者作为该 skill 的新版本发布。</p>
      {!existing.canPublishNewVersion && (
        <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
          只有该 skill 的归属人或管理员才能发布新版本,你可以改名新建一个属于自己的 skill。
        </p>
      )}
    </Modal>
  )
}
