import { useState } from 'react'
import type { ApiError } from '../api/client'
import type { ManagedSkill } from '../api/types'
import { useDeleteSkill } from '../hooks/useSkillMutations'
import { ErrorBanner } from './ErrorBanner'
import { Modal } from './Modal'
import { TextInput } from './TextInput'

export interface DeleteSkillDialogProps {
  readonly skill: ManagedSkill
  readonly onClose: () => void
}

/**
 * 删除确认弹窗。**要求手输 skill 的 name 才放行。**
 *
 * <p>这不是仪式感。物理删除会连同全部版本与制品文件一起清掉,没有回收站;而"再点一次确认"这种
 * 二次确认对连续操作的人几乎等于没有——手已经在往下点了。要求把名字打出来,强制的是"看清楚你选的
 * 是哪一个",这恰好是误删最常见的成因:在一张长表格里点错了行。
 *
 * <p>比对用 `name` 不是 `displayName`:`name` 全局唯一且不可修改(它参与安装路径拼接,是安全边界),
 * 而展示名可以重名,拿它做校验等于放行"两个都叫『代码审查』,删错了那个"。
 */
export function DeleteSkillDialog({ skill, onClose }: DeleteSkillDialogProps) {
  const [confirmName, setConfirmName] = useState('')
  const deleteMutation = useDeleteSkill(skill.id)

  const canDelete = confirmName.trim() === skill.name && !deleteMutation.isPending

  function handleDelete() {
    if (!canDelete) {
      return
    }
    deleteMutation.mutate(undefined, { onSuccess: onClose })
  }

  return (
    <Modal
      open
      title="删除 skill"
      onClose={onClose}
      actions={[
        { label: '取消', onClick: onClose, variant: 'secondary' },
        {
          label: '永久删除',
          onClick: handleDelete,
          variant: 'danger',
          disabled: !canDelete,
          loading: deleteMutation.isPending,
        },
      ]}
    >
      <div className="flex flex-col gap-3">
        <p className="text-sm text-gray-700 dark:text-gray-300">
          将永久删除 <span className="font-medium">{skill.displayName}</span> 的全部版本与制品文件,
          <span className="font-medium">此操作不可撤销</span>。
        </p>
        <p className="text-sm text-gray-700 dark:text-gray-300">
          已经流传出去的安装命令会跳过它并给出提示;如果只是想让它不再出现在浏览页,用「下架」而不是删除。
        </p>
        <TextInput
          label={`输入 ${skill.name} 以确认`}
          value={confirmName}
          autoComplete="off"
          onChange={(event) => setConfirmName(event.target.value)}
        />
        {deleteMutation.isError && <ErrorBanner error={deleteMutation.error as ApiError} />}
      </div>
    </Modal>
  )
}
