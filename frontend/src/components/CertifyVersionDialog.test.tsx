import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { SkillVersionSummary } from '../api/types'
import { CertifyVersionDialog } from './CertifyVersionDialog'

function version(overrides: Partial<SkillVersionSummary> = {}): SkillVersionSummary {
  return {
    id: 10,
    version: '1.0.0',
    sizeBytes: 1024,
    fileCount: 3,
    certification: 'NONE',
    certifiedByUserId: null,
    certifiedAt: null,
    certifyNote: null,
    createdByUserId: 2,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function renderDialog(target: SkillVersionSummary = version()) {
  const onSubmit = vi.fn()
  render(
    <CertifyVersionDialog
      open
      version={target}
      submitting={false}
      onClose={vi.fn()}
      onSubmit={onSubmit}
    />,
  )
  return onSubmit
}

function selectCertification(value: string) {
  fireEvent.change(screen.getByLabelText('认证状态'), { target: { value } })
}

describe('CertifyVersionDialog', () => {
  it('should_render_nothing_when_no_version_selected', () => {
    const { container } = render(
      <CertifyVersionDialog
        open
        version={null}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('should_prefill_current_state_when_opened', () => {
    renderDialog(version({ certification: 'REVOKED', certifyNote: '有问题' }))

    expect(screen.getByLabelText('认证状态')).toHaveValue('REVOKED')
    expect(screen.getByLabelText('撤销理由(必填)')).toHaveValue('有问题')
  })

  /**
   * 撤销理由会原样进安装脚本的警示文本,是使用者判断"要不要继续装"的唯一依据。空理由等于给一个
   * 只会吓人、不提供信息的红色警告。
   */
  it('should_disable_submit_when_revoking_without_note', () => {
    renderDialog()

    selectCertification('REVOKED')

    expect(screen.getByRole('button', { name: '提交' })).toBeDisabled()
  })

  it('should_enable_submit_after_note_filled_when_revoking', () => {
    const onSubmit = renderDialog()

    selectCertification('REVOKED')
    fireEvent.change(screen.getByLabelText('撤销理由(必填)'), {
      target: { value: '存在命令注入风险' },
    })
    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(onSubmit).toHaveBeenCalledWith('REVOKED', '存在命令注入风险')
  })

  it('should_allow_certifying_without_note', () => {
    const onSubmit = renderDialog()

    selectCertification('CERTIFIED')
    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(onSubmit).toHaveBeenCalledWith('CERTIFIED', '')
  })

  it('should_trim_note_before_submitting', () => {
    const onSubmit = renderDialog()

    selectCertification('CERTIFIED')
    fireEvent.change(screen.getByLabelText('认证意见'), { target: { value: '  看过了  ' } })
    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(onSubmit).toHaveBeenCalledWith('CERTIFIED', '看过了')
  })

  /** 只填空格不算填了理由——否则必填校验一秒就能被绕过。 */
  it('should_treat_whitespace_only_note_as_missing_when_revoking', () => {
    renderDialog()

    selectCertification('REVOKED')
    fireEvent.change(screen.getByLabelText('撤销理由(必填)'), { target: { value: '    ' } })

    expect(screen.getByRole('button', { name: '提交' })).toBeDisabled()
  })

  /** "撤销"这个词很容易被误以为连带下架,说清楚比事后解释便宜。 */
  it('should_warn_that_revoking_does_not_unlist_the_skill', () => {
    renderDialog()

    selectCertification('REVOKED')

    expect(screen.getByText(/撤销不会下架这个 skill/)).toBeInTheDocument()
  })

  /**
   * 错误必须显示在弹窗<b>内部</b>。放到页面上会被遮罩层盖住,用户看到的是"点了提交、什么都没发生、
   * 弹窗还开着"的死局 —— 而错误信息就在他看不见的地方。
   */
  it('should_show_server_error_inside_the_dialog', () => {
    render(
      <CertifyVersionDialog
        open
        version={version()}
        submitting={false}
        error={
          new ApiError({
            code: 'CERTIFICATION_DECISION_INVALID',
            message: '撤销认证必须填写理由',
            traceId: 't-1',
          })
        }
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('撤销认证必须填写理由')).toBeInTheDocument()
  })

  it('should_offer_none_option_to_undo_a_mistake', () => {
    const onSubmit = renderDialog(version({ certification: 'CERTIFIED' }))

    selectCertification('NONE')
    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(onSubmit).toHaveBeenCalledWith('NONE', '')
  })
})
