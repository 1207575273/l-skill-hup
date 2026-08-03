import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Tag } from '../api/types'
import { SkillMetadataEditForm } from './SkillMetadataEditForm'

const CURATED_OPTIONS: Tag[] = [
  { id: 1, name: '代码审查', kind: 'CURATED', description: null, sortOrder: 1, skillCount: 3 },
  { id: 2, name: '测试', kind: 'CURATED', description: null, sortOrder: 2, skillCount: 1 },
]

function renderForm(overrides: Partial<Parameters<typeof SkillMetadataEditForm>[0]> = {}) {
  const onSubmit = vi.fn()
  const onCancel = vi.fn()
  render(
    <SkillMetadataEditForm
      skillName="code-review-helper"
      initialDisplayName="代码审查助手"
      initialSource="INTERNAL"
      initialCuratedTagIds={[1]}
      initialFreeTagNames={['pdf']}
      curatedTagOptions={CURATED_OPTIONS}
      submitting={false}
      onCancel={onCancel}
      onSubmit={onSubmit}
      {...overrides}
    />,
  )
  return { onSubmit, onCancel }
}

describe('SkillMetadataEditForm', () => {
  it('should_show_readonly_skill_name_when_rendered', () => {
    renderForm()

    expect(screen.getByText('code-review-helper')).toBeInTheDocument()
    // name 不可改:表单里不该出现任何以它为 value 的可编辑输入框。
    expect(screen.queryByLabelText('name')).not.toBeInTheDocument()
  })

  it('should_submit_payload_with_edited_fields_when_form_submitted', () => {
    const { onSubmit } = renderForm()

    fireEvent.change(screen.getByLabelText('展示名'), { target: { value: '新展示名' } })
    fireEvent.click(screen.getByRole('button', { name: '保存修改' }))

    expect(onSubmit).toHaveBeenCalledWith({
      displayName: '新展示名',
      source: 'INTERNAL',
      curatedTagIds: [1],
      freeTagNames: ['pdf'],
    })
  })

  it('should_toggle_curated_tag_selection_when_checkbox_clicked', () => {
    const { onSubmit } = renderForm()

    fireEvent.click(screen.getByRole('checkbox', { name: '测试' }))
    fireEvent.click(screen.getByRole('button', { name: '保存修改' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ curatedTagIds: expect.arrayContaining([1, 2]) }),
    )
  })

  it('should_block_submit_and_show_validation_message_when_no_curated_tag_selected', () => {
    const { onSubmit } = renderForm({ initialCuratedTagIds: [1] })

    fireEvent.click(screen.getByRole('checkbox', { name: '代码审查' }))
    fireEvent.click(screen.getByRole('button', { name: '保存修改' }))

    expect(screen.getByRole('alert')).toHaveTextContent('分类标签需选择 1~3 个')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('should_call_onCancel_when_cancel_button_clicked', () => {
    const { onCancel } = renderForm()

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onCancel).toHaveBeenCalled()
  })
})
