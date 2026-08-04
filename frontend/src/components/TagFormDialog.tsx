import { useEffect, useState } from 'react'
import type { Tag, TagKind } from '../api/types'
import { Modal } from './Modal'
import { Select } from './Select'
import { TextInput } from './TextInput'

/** 与后端 `TagName.MAX_LENGTH` 同源。这里只做即时反馈,真正的判定在服务端归一化之后。 */
const NAME_MAX_LENGTH = 16
const DESCRIPTION_MAX_LENGTH = 128

export interface TagFormValue {
  readonly name: string
  readonly kind: TagKind
  readonly description: string
  readonly sortOrder: number
}

export interface TagFormDialogProps {
  readonly open: boolean
  /** `null` 表示新建。新建只能建 CURATED,所以那种情况不显示 kind 选择器。 */
  readonly tag: Tag | null
  readonly submitting: boolean
  readonly onClose: () => void
  readonly onSubmit: (value: TagFormValue) => void
}

const KIND_OPTIONS: ReadonlyArray<{ readonly value: TagKind; readonly label: string }> = [
  { value: 'CURATED', label: 'CURATED(分类,进导航)' },
  { value: 'FREE', label: 'FREE(自由标签)' },
]

/**
 * 建 / 改词条的表单弹窗。
 *
 * <p>新建时不给 kind 选择器:这条接口只建 CURATED(`POST /tags` 的契约就是这样)。给 ADMIN 一个建 FREE
 * 的口子,建出来的当场就是零引用孤儿,下一次有人改标签就被自动清理掉——一个只会制造困惑的选项。
 *
 * <p>转成 FREE 时把描述与排序禁用并说明原因,而不是留着让人填:这两个字段"仅 CURATED 用"
 * (业务建模文档 4.6),服务端会把它们清空。让人填完再被静默丢掉,比一开始就说清楚糟得多。
 */
export function TagFormDialog({ open, tag, submitting, onClose, onSubmit }: TagFormDialogProps) {
  const [name, setName] = useState('')
  const [kind, setKind] = useState<TagKind>('CURATED')
  const [description, setDescription] = useState('')
  const [sortOrder, setSortOrder] = useState('0')

  // 每次打开都按当前 tag 重置。不重置的话,关掉编辑 A 再打开编辑 B,看到的是 A 的值,
  // 而那看起来像"B 的数据就长这样"。
  useEffect(() => {
    if (!open) {
      return
    }
    setName(tag?.name ?? '')
    setKind(tag?.kind ?? 'CURATED')
    setDescription(tag?.description ?? '')
    setSortOrder(String(tag?.sortOrder ?? 0))
  }, [open, tag])

  const trimmedName = name.trim()
  const nameError =
    trimmedName.length > NAME_MAX_LENGTH ? `标签名不得超过 ${NAME_MAX_LENGTH} 个字符` : undefined
  const parsedSortOrder = Number.parseInt(sortOrder, 10)
  const sortOrderError = Number.isNaN(parsedSortOrder) ? '排序必须是整数' : undefined
  const canSubmit = trimmedName !== '' && nameError === undefined && sortOrderError === undefined
  const isCuratedOnlyFieldDisabled = kind === 'FREE'

  return (
    <Modal
      open={open}
      title={tag === null ? '新建分类' : `编辑词条:${tag.name}`}
      onClose={onClose}
      actions={[
        {
          label: tag === null ? '创建' : '保存',
          onClick: () =>
            onSubmit({
              name: trimmedName,
              kind,
              description: isCuratedOnlyFieldDisabled ? '' : description.trim(),
              sortOrder: isCuratedOnlyFieldDisabled ? 0 : parsedSortOrder,
            }),
          disabled: !canSubmit,
          loading: submitting,
        },
        { label: '取消', onClick: onClose, variant: 'ghost' },
      ]}
    >
      <div className="flex flex-col gap-4">
        <TextInput
          label="标签名"
          value={name}
          maxLength={NAME_MAX_LENGTH * 2}
          errorMessage={nameError}
          hint={`最多 ${NAME_MAX_LENGTH} 个字符,首尾空白与连续空格会被归一化`}
          onChange={(event) => setName(event.target.value)}
        />

        {tag !== null && (
          <Select
            label="类型"
            value={kind}
            options={KIND_OPTIONS}
            onChange={setKind}
            errorMessage={
              tag.kind === 'CURATED' && kind === 'FREE'
                ? '转成 FREE 后,挂着它的 skill 会从这个分类的导航下消失'
                : undefined
            }
          />
        )}

        <TextInput
          label="描述"
          value={isCuratedOnlyFieldDisabled ? '' : description}
          maxLength={DESCRIPTION_MAX_LENGTH}
          disabled={isCuratedOnlyFieldDisabled}
          hint={
            isCuratedOnlyFieldDisabled ? 'FREE 词条不展示描述,这个字段会被清空' : '展示在筛选面板上'
          }
          onChange={(event) => setDescription(event.target.value)}
        />

        <TextInput
          label="排序"
          type="number"
          value={isCuratedOnlyFieldDisabled ? '0' : sortOrder}
          disabled={isCuratedOnlyFieldDisabled}
          errorMessage={sortOrderError}
          hint={isCuratedOnlyFieldDisabled ? 'FREE 词条不参与导航排序' : '小的在前,相同则按名称'}
          onChange={(event) => setSortOrder(event.target.value)}
        />
      </div>
    </Modal>
  )
}
