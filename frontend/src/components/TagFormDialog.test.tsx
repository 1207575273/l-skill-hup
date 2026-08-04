import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Tag } from '../api/types'
import { TagFormDialog } from './TagFormDialog'

const CURATED_TAG: Tag = {
  id: 1,
  name: '代码审查',
  kind: 'CURATED',
  description: '评审清单',
  sortOrder: 20,
  skillCount: 3,
}

function typeInto(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('TagFormDialog', () => {
  /** `POST /tags` 的契约就是"新建 CURATED 词条";给 ADMIN 一个建 FREE 的口子只会造出零引用孤儿。 */
  it('should_hide_kind_selector_when_creating', () => {
    render(
      <TagFormDialog open tag={null} submitting={false} onClose={vi.fn()} onSubmit={vi.fn()} />,
    )

    expect(screen.queryByLabelText('类型')).not.toBeInTheDocument()
  })

  it('should_prefill_fields_when_editing', () => {
    render(
      <TagFormDialog
        open
        tag={CURATED_TAG}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('标签名')).toHaveValue('代码审查')
    expect(screen.getByLabelText('描述')).toHaveValue('评审清单')
    expect(screen.getByLabelText('排序')).toHaveValue(20)
  })

  it('should_disable_submit_when_name_blank', () => {
    render(
      <TagFormDialog open tag={null} submitting={false} onClose={vi.fn()} onSubmit={vi.fn()} />,
    )

    expect(screen.getByRole('button', { name: '创建' })).toBeDisabled()
  })

  it('should_reject_name_longer_than_16_chars', () => {
    render(
      <TagFormDialog open tag={null} submitting={false} onClose={vi.fn()} onSubmit={vi.fn()} />,
    )

    typeInto('标签名', '一二三四五六七八九十一二三四五六七')

    expect(screen.getByText('标签名不得超过 16 个字符')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '创建' })).toBeDisabled()
  })

  it('should_trim_name_when_submitting', () => {
    const onSubmit = vi.fn()
    render(
      <TagFormDialog open tag={null} submitting={false} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    typeInto('标签名', '  性能优化  ')
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ name: '性能优化', kind: 'CURATED' }),
    )
  })

  /**
   * 4.6 写死 description 与 sort_order "仅 CURATED 用"。让人填完再被服务端静默丢掉,比一开始就
   * 禁用并说明原因糟得多。
   */
  it('should_disable_and_clear_curated_only_fields_when_switching_to_free', () => {
    const onSubmit = vi.fn()
    render(
      <TagFormDialog
        open
        tag={CURATED_TAG}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    typeInto('类型', 'FREE')

    expect(screen.getByLabelText('描述')).toBeDisabled()
    expect(screen.getByLabelText('排序')).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: '保存' }))
    expect(onSubmit).toHaveBeenCalledWith({
      name: '代码审查',
      kind: 'FREE',
      description: '',
      sortOrder: 0,
    })
  })

  it('should_warn_when_converting_curated_to_free', () => {
    render(
      <TagFormDialog
        open
        tag={CURATED_TAG}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    typeInto('类型', 'FREE')

    expect(screen.getByText(/会从这个分类的导航下消失/)).toBeInTheDocument()
  })

  /** 关掉编辑 A 再打开编辑 B 却看到 A 的值,看起来像"B 的数据就长这样"。 */
  it('should_reset_fields_when_reopened_with_another_tag', () => {
    const { rerender } = render(
      <TagFormDialog
        open
        tag={CURATED_TAG}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )
    rerender(
      <TagFormDialog
        open={false}
        tag={CURATED_TAG}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )
    rerender(
      <TagFormDialog
        open
        tag={{ ...CURATED_TAG, id: 2, name: '文档', description: null, sortOrder: 60 }}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('标签名')).toHaveValue('文档')
    expect(screen.getByLabelText('描述')).toHaveValue('')
  })
})
