import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SkillVersionSummary } from '../api/types'
import { SkillVersionList } from './SkillVersionList'

function buildVersion(overrides: Partial<SkillVersionSummary> = {}): SkillVersionSummary {
  return {
    id: 10,
    version: '1.0.0',
    sizeBytes: 2048,
    fileCount: 5,
    certification: 'NONE',
    certifiedByUserId: null,
    certifiedAt: null,
    certifyNote: null,
    createdByUserId: 1,
    createdAt: '2026-07-20T08:00:00Z',
    ...overrides,
  }
}

describe('SkillVersionList', () => {
  it('should_render_version_size_and_date_when_rendered', () => {
    render(
      <SkillVersionList
        versions={[buildVersion()]}
        selectedVersionId={null}
        onSelectVersion={vi.fn()}
      />,
    )

    expect(screen.getByText('v1.0.0')).toBeInTheDocument()
    expect(screen.getByText(/2\.00 KB/)).toBeInTheDocument()
    expect(screen.getByText(/5 个文件/)).toBeInTheDocument()
    expect(screen.getByText(/2026-07-20/)).toBeInTheDocument()
  })

  it('should_mark_selected_version_and_disable_its_button_when_selected', () => {
    render(
      <SkillVersionList
        versions={[buildVersion({ id: 10 })]}
        selectedVersionId={10}
        onSelectVersion={vi.fn()}
      />,
    )

    expect(screen.getByText('当前选中')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '选择版本 1.0.0' })).toBeDisabled()
  })

  it('should_call_onSelectVersion_with_version_id_when_row_button_clicked', () => {
    const handleSelect = vi.fn()
    render(
      <SkillVersionList
        versions={[buildVersion({ id: 10 }), buildVersion({ id: 11, version: '2.0.0' })]}
        selectedVersionId={10}
        onSelectVersion={handleSelect}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '选择版本 2.0.0' }))

    expect(handleSelect).toHaveBeenCalledWith(11)
  })

  it('should_show_certification_badge_when_version_revoked', () => {
    render(
      <SkillVersionList
        versions={[buildVersion({ certification: 'REVOKED', certifyNote: '含未脱敏内网地址' })]}
        selectedVersionId={null}
        onSelectVersion={vi.fn()}
      />,
    )

    expect(screen.getByText('认证已撤销')).toBeInTheDocument()
    expect(screen.getByText('含未脱敏内网地址')).toBeInTheDocument()
  })
})
