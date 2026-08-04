import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Tag } from '../api/types'
import { DeleteTagDialog } from './DeleteTagDialog'

function tag(overrides: Partial<Tag> = {}): Tag {
  return {
    id: 1,
    name: '代码审查',
    kind: 'CURATED',
    description: null,
    sortOrder: 20,
    skillCount: 0,
    ...overrides,
  }
}

function renderDialog(target: Tag, candidates: Tag[] = []) {
  const onConfirm = vi.fn()
  render(
    <DeleteTagDialog
      open
      tag={target}
      migrationCandidates={candidates}
      submitting={false}
      onClose={vi.fn()}
      onConfirm={onConfirm}
    />,
  )
  return onConfirm
}

describe('DeleteTagDialog', () => {
  it('should_allow_delete_without_migration_when_curated_unreferenced', () => {
    const onConfirm = renderDialog(tag({ skillCount: 0 }))

    const confirm = screen.getByRole('button', { name: '确认删除' })
    expect(confirm).toBeEnabled()

    fireEvent.click(confirm)
    expect(onConfirm).toHaveBeenCalledWith(undefined)
  })

  /** 删掉一个在用的分类等于一批 skill 同时失去分类,那不该是一次点击的副作用。 */
  it('should_require_migration_when_curated_still_referenced', () => {
    renderDialog(tag({ skillCount: 4 }), [tag({ id: 2, name: '测试' })])

    expect(screen.getByRole('button', { name: '确认删除' })).toBeDisabled()
    expect(screen.getByText(/必须指定把这些 skill 迁到哪个分类下/)).toBeInTheDocument()
  })

  it('should_enable_confirm_after_picking_migration_target', () => {
    const onConfirm = renderDialog(tag({ skillCount: 4 }), [tag({ id: 2, name: '测试' })])

    fireEvent.change(screen.getByLabelText('迁移到'), { target: { value: '2' } })

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
    expect(onConfirm).toHaveBeenCalledWith(2)
  })

  /** FREE 是用户随手打的,零引用时本来就会被自动清理,不必逼 ADMIN 指定迁移目标。 */
  it('should_not_require_migration_for_referenced_free_tag', () => {
    const onConfirm = renderDialog(tag({ kind: 'FREE', skillCount: 3 }))

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
    expect(onConfirm).toHaveBeenCalledWith(undefined)
  })

  it('should_say_so_when_no_migration_candidate_exists', () => {
    renderDialog(tag({ skillCount: 4 }), [])

    expect(screen.getByText('没有同类型的其他词条可以迁移。')).toBeInTheDocument()
    expect(screen.queryByLabelText('迁移到')).not.toBeInTheDocument()
  })

  it('should_render_nothing_when_no_tag_selected', () => {
    const { container } = render(
      <DeleteTagDialog
        open
        tag={null}
        migrationCandidates={[]}
        submitting={false}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(container).toBeEmptyDOMElement()
  })
})
