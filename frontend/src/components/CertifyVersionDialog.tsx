import { useEffect, useState } from 'react'
import type { ApiError } from '../api/client'
import type { CertificationStatus, SkillVersionSummary } from '../api/types'
import { ErrorBanner } from './ErrorBanner'
import { Modal } from './Modal'
import { Select } from './Select'
import { TextInput } from './TextInput'

/** 与后端 `CertificationDecision.MAX_NOTE_LENGTH` 同源。 */
const NOTE_MAX_LENGTH = 256

const CERTIFICATION_OPTIONS: ReadonlyArray<{
  readonly value: CertificationStatus
  readonly label: string
}> = [
  { value: 'NONE', label: '未认证(清除标记)' },
  { value: 'CERTIFIED', label: '平台认证' },
  { value: 'REVOKED', label: '撤销认证' },
]

export interface CertifyVersionDialogProps {
  readonly open: boolean
  readonly version: SkillVersionSummary | null
  readonly submitting: boolean
  /**
   * 服务端返回的错误,**必须显示在弹窗内部**。放到页面上会被弹窗的遮罩层盖住,用户看到的是一个
   * "点了提交、什么都没发生、弹窗还开着"的死局 —— 而错误信息其实就在他看不见的地方。
   */
  readonly error?: ApiError | null
  readonly onClose: () => void
  readonly onSubmit: (certification: CertificationStatus, note: string) => void
}

/**
 * 认证 / 撤销某个版本(业务建模文档 5.8)。
 *
 * <p><b>撤销必填理由</b>,而且这里要把"为什么必填"说出来:撤销理由会原样进安装脚本的警示文本,
 * 是使用者判断"这个包出了什么事、我该不该继续装"的<b>唯一依据</b>。不解释的话,填的人只会当成又一个
 * 挡路的必填项,随手敲两个字了事 —— 那样的警示等于没有。
 *
 * <p>选到"撤销"时提示这个动作<b>不会下架 skill</b>:两者是不同烈度的处置,而"撤销"这个词很容易被
 * 误以为连带下架。说清楚比事后解释便宜。
 */
export function CertifyVersionDialog({
  open,
  version,
  submitting,
  error,
  onClose,
  onSubmit,
}: CertifyVersionDialogProps) {
  const [certification, setCertification] = useState<CertificationStatus>('CERTIFIED')
  const [note, setNote] = useState('')

  // 每次打开都按目标版本的当前状态重置,否则关掉 A 再开 B 会看到 A 的选择。
  useEffect(() => {
    if (open) {
      setCertification(version?.certification ?? 'CERTIFIED')
      setNote(version?.certifyNote ?? '')
    }
  }, [open, version])

  if (version === null) {
    return null
  }

  const trimmedNote = note.trim()
  const noteRequired = certification === 'REVOKED'
  const noteTooLong = trimmedNote.length > NOTE_MAX_LENGTH
  const canSubmit = (!noteRequired || trimmedNote !== '') && !noteTooLong

  return (
    <Modal
      open={open}
      title={`认证版本 v${version.version}`}
      onClose={onClose}
      actions={[
        {
          label: '提交',
          variant: certification === 'REVOKED' ? 'danger' : 'primary',
          disabled: !canSubmit,
          loading: submitting,
          onClick: () => onSubmit(certification, trimmedNote),
        },
        { label: '取消', onClick: onClose, variant: 'ghost' },
      ]}
    >
      <div className="flex flex-col gap-4">
        {error && <ErrorBanner error={error} />}

        <Select
          label="认证状态"
          value={certification}
          options={CERTIFICATION_OPTIONS}
          onChange={setCertification}
        />

        <TextInput
          label={noteRequired ? '撤销理由(必填)' : '认证意见'}
          value={note}
          maxLength={NOTE_MAX_LENGTH}
          onChange={(event) => setNote(event.target.value)}
          errorMessage={noteTooLong ? `不得超过 ${NOTE_MAX_LENGTH} 个字符` : undefined}
          hint={
            noteRequired
              ? '这段话会原样出现在安装脚本的警示里,是使用者判断要不要继续装的唯一依据'
              : '可不填'
          }
        />

        {noteRequired && (
          <p className="text-sm text-amber-700 dark:text-amber-500">
            撤销后,新生成的安装脚本会默认中止,需要显式 --force 才继续。
            <b>撤销不会下架这个 skill</b> —— 那是另一档处置,要下架请去管理操作。
          </p>
        )}
      </div>
    </Modal>
  )
}
