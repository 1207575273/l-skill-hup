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

  /**
   * 认证是 ADMIN 专属(5.8)。给普通用户渲染一个点了必然 403 的按钮没有任何价值,所以这一列的存在与否
   * 完全由"调用方给没给回调"决定 —— 组件自己不去读登录态。
   */
  it('should_hide_certify_button_when_no_callback_given', () => {
    render(
      <SkillVersionList
        versions={[buildVersion()]}
        selectedVersionId={null}
        onSelectVersion={vi.fn()}
      />,
    )

    expect(screen.queryByRole('button', { name: '认证版本 1.0.0' })).not.toBeInTheDocument()
  })

  it('should_show_certify_button_and_pass_version_back_when_callback_given', () => {
    const onCertifyVersion = vi.fn()
    const target = buildVersion()
    render(
      <SkillVersionList
        versions={[target]}
        selectedVersionId={null}
        onSelectVersion={vi.fn()}
        onCertifyVersion={onCertifyVersion}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '认证版本 1.0.0' }))

    expect(onCertifyVersion).toHaveBeenCalledWith(target)
  })

  /** 认证按钮不该顺带把这一行选中 —— 那会悄悄改掉"快速接入"用的是哪个版本。 */
  it('should_not_select_version_when_certify_clicked', () => {
    const onSelectVersion = vi.fn()
    render(
      <SkillVersionList
        versions={[buildVersion()]}
        selectedVersionId={null}
        onSelectVersion={onSelectVersion}
        onCertifyVersion={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '认证版本 1.0.0' }))

    expect(onSelectVersion).not.toHaveBeenCalled()
  })
})
