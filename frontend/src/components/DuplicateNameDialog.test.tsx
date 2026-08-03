import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SkillPrecheckExisting } from '../api/types'
import { DuplicateNameDialog } from './DuplicateNameDialog'

const EXISTING: SkillPrecheckExisting = {
  skillId: 42,
  name: 'code-review',
  ownerDisplayName: '张三',
  ownerEmployeeNo: '80380486',
  latestVersion: '1.2.0',
  canPublishNewVersion: true,
}

describe('DuplicateNameDialog', () => {
  it('should_show_existing_skill_info_when_open', () => {
    render(
      <DuplicateNameDialog
        open
        existing={EXISTING}
        suggestedName="code-review-3f2a1c"
        onRenameAndCreate={() => {}}
        onPublishAsNewVersion={() => {}}
        onCancel={() => {}}
      />,
    )

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('code-review', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText(/张三/)).toBeInTheDocument()
    expect(screen.getByText(/1\.2\.0/)).toBeInTheDocument()
  })

  it('should_invoke_onRenameAndCreate_when_rename_button_clicked', () => {
    const handleRename = vi.fn()
    render(
      <DuplicateNameDialog
        open
        existing={EXISTING}
        suggestedName="code-review-3f2a1c"
        onRenameAndCreate={handleRename}
        onPublishAsNewVersion={() => {}}
        onCancel={() => {}}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /改名新建/ }))

    expect(handleRename).toHaveBeenCalledTimes(1)
  })

  it('should_invoke_onPublishAsNewVersion_when_new_version_button_clicked', () => {
    const handleNewVersion = vi.fn()
    render(
      <DuplicateNameDialog
        open
        existing={EXISTING}
        suggestedName="code-review-3f2a1c"
        onRenameAndCreate={() => {}}
        onPublishAsNewVersion={handleNewVersion}
        onCancel={() => {}}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '作为新版本发布' }))

    expect(handleNewVersion).toHaveBeenCalledTimes(1)
  })

  it('should_hide_new_version_option_when_canPublishNewVersion_is_false', () => {
    render(
      <DuplicateNameDialog
        open
        existing={{ ...EXISTING, canPublishNewVersion: false }}
        suggestedName="code-review-3f2a1c"
        onRenameAndCreate={() => {}}
        onPublishAsNewVersion={() => {}}
        onCancel={() => {}}
      />,
    )

    expect(screen.queryByRole('button', { name: '作为新版本发布' })).not.toBeInTheDocument()
    expect(screen.getByText(/只有该 skill 的归属人或管理员才能发布新版本/)).toBeInTheDocument()
  })

  it('should_invoke_onCancel_when_cancel_button_clicked', () => {
    const handleCancel = vi.fn()
    render(
      <DuplicateNameDialog
        open
        existing={EXISTING}
        suggestedName="code-review-3f2a1c"
        onRenameAndCreate={() => {}}
        onPublishAsNewVersion={() => {}}
        onCancel={handleCancel}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(handleCancel).toHaveBeenCalledTimes(1)
  })
})
