import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Modal } from './Modal'

describe('Modal', () => {
  it('should_render_nothing_when_closed', () => {
    render(
      <Modal open={false} title="发现同名 skill" onClose={() => {}}>
        正文
      </Modal>,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('should_render_title_and_content_when_open', () => {
    render(
      <Modal open title="发现同名 skill" onClose={() => {}}>
        已存在同名 skill,选择覆盖或作为新版本发布
      </Modal>,
    )

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('发现同名 skill')).toBeInTheDocument()
    expect(screen.getByText('已存在同名 skill,选择覆盖或作为新版本发布')).toBeInTheDocument()
  })

  it('should_invoke_action_onClick_when_action_button_clicked', () => {
    const handleOverwrite = vi.fn()
    render(
      <Modal
        open
        title="发现同名 skill"
        onClose={() => {}}
        actions={[
          { label: '覆盖已有版本', onClick: handleOverwrite, variant: 'danger' },
          { label: '作为新版本发布', onClick: () => {} },
        ]}
      >
        正文
      </Modal>,
    )

    fireEvent.click(screen.getByRole('button', { name: '覆盖已有版本' }))

    expect(handleOverwrite).toHaveBeenCalledTimes(1)
  })

  it('should_invoke_onClose_when_close_button_clicked', () => {
    const handleClose = vi.fn()
    render(
      <Modal open title="发现同名 skill" onClose={handleClose}>
        正文
      </Modal>,
    )

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))

    expect(handleClose).toHaveBeenCalledTimes(1)
  })

  it('should_invoke_onClose_when_escape_key_pressed', () => {
    const handleClose = vi.fn()
    render(
      <Modal open title="发现同名 skill" onClose={handleClose}>
        正文
      </Modal>,
    )

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(handleClose).toHaveBeenCalledTimes(1)
  })

  it('should_not_invoke_onClose_when_clicking_inside_dialog_content', () => {
    const handleClose = vi.fn()
    render(
      <Modal open title="发现同名 skill" onClose={handleClose}>
        正文
      </Modal>,
    )

    fireEvent.click(screen.getByText('正文'))

    expect(handleClose).not.toHaveBeenCalled()
  })
})
