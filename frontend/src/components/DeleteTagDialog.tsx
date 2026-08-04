import { useEffect, useState } from 'react'
import type { Tag } from '../api/types'
import { Modal } from './Modal'
import { Select } from './Select'

export interface DeleteTagDialogProps {
  readonly open: boolean
  readonly tag: Tag | null
  /** 可作为迁移目标的词条:与被删词条 kind 相同、且不是它自己。筛选由调用方做,这里只渲染。 */
  readonly migrationCandidates: readonly Tag[]
  readonly submitting: boolean
  readonly onClose: () => void
  readonly onConfirm: (migrateToTagId: number | undefined) => void
}

/** `Select` 的值必须是字符串,用它表示"不迁移,直接断开引用"。 */
const NO_MIGRATION = ''

/**
 * 删除词条的确认弹窗。
 *
 * <p><b>是否必须选迁移目标由 kind 与引用数共同决定,不是一个固定规则</b>:仍被引用的 CURATED 必须选
 * (删掉等于一批 skill 同时失去分类,那不该是一次点击的副作用);FREE 与零引用的 CURATED 不必选。
 * 前端按同一条规则禁用确认按钮只是为了少一次往返,<b>真正的拦截在服务端</b>——它会用 409 顶回来。
 *
 * <p>而且前端<b>判不准</b>:这里能看到的 `skillCount` 只数已发布 skill(与列表页口径一致),服务端的
 * 引用计数含已下架的。一个只被下架 skill 挂着的分类,在这里显示 0、按钮可点,提交后被 409 顶回来 ——
 * 这不是 bug,是两个口径各自正确。所以调用方必须把服务端的错误显示出来,不能只依赖这里的禁用。
 *
 * <p>候选列表只给同 kind 的词条:把 CURATED 迁到 FREE 上,那批 skill 表面还有标签,实际已从导航整体
 * 消失,而操作者以为自己做的是一次无损搬迁。服务端同样校验这条,这里只是不把非法选项摆出来。
 */
export function DeleteTagDialog({
  open,
  tag,
  migrationCandidates,
  submitting,
  onClose,
  onConfirm,
}: DeleteTagDialogProps) {
  const [migrateToTagId, setMigrateToTagId] = useState<string>(NO_MIGRATION)

  useEffect(() => {
    if (open) {
      setMigrateToTagId(NO_MIGRATION)
    }
  }, [open])

  if (tag === null) {
    return null
  }

  const isReferenced = tag.skillCount > 0
  const migrationRequired = tag.kind === 'CURATED' && isReferenced
  const canConfirm = !migrationRequired || migrateToTagId !== NO_MIGRATION

  const options = [
    { value: NO_MIGRATION, label: migrationRequired ? '请选择迁移目标' : '不迁移,直接移除关联' },
    ...migrationCandidates.map((candidate) => ({
      value: String(candidate.id),
      label: `${candidate.name}(${candidate.skillCount})`,
    })),
  ]

  return (
    <Modal
      open={open}
      title={`删除词条:${tag.name}`}
      onClose={onClose}
      actions={[
        {
          label: '确认删除',
          variant: 'danger',
          disabled: !canConfirm,
          loading: submitting,
          onClick: () =>
            onConfirm(migrateToTagId === NO_MIGRATION ? undefined : Number(migrateToTagId)),
        },
        { label: '取消', onClick: onClose, variant: 'ghost' },
      ]}
    >
      <div className="flex flex-col gap-4">
        <p className="text-sm text-gray-600 dark:text-gray-400">
          {isReferenced
            ? `当前有 ${tag.skillCount} 个已发布 skill 挂着它。`
            : '当前没有已发布 skill 挂着它。'}
        </p>

        {migrationRequired && (
          <p className="text-sm text-amber-700 dark:text-amber-500">
            这是一个仍在使用的分类,必须指定把这些 skill 迁到哪个分类下。
          </p>
        )}

        {migrationCandidates.length > 0 ? (
          <Select
            label="迁移到"
            value={migrateToTagId}
            options={options}
            onChange={setMigrateToTagId}
          />
        ) : (
          <p className="text-sm text-gray-500 dark:text-gray-400">没有同类型的其他词条可以迁移。</p>
        )}

        <p className="text-sm text-gray-500 dark:text-gray-400">
          删除不可逆。skill 本身不受影响,只是不再挂这个词条。
        </p>
      </div>
    </Modal>
  )
}
