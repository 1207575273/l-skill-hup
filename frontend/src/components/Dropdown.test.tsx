import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Dropdown } from './Dropdown'

function renderDropdown() {
  return render(
    <div>
      <Dropdown
        renderTrigger={({ onClick, open }) => (
          <button type="button" onClick={onClick} aria-expanded={open}>
            快速接入
          </button>
        )}
      >
        <button type="button">claude · global</button>
      </Dropdown>
      <button type="button">页面上的其他按钮</button>
    </div>,
  )
}

describe('Dropdown', () => {
  it('should_hide_menu_when_not_opened', () => {
    renderDropdown()

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('should_show_menu_when_trigger_clicked', () => {
    renderDropdown()

    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))

    expect(screen.getByRole('menu')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'claude · global' })).toBeInTheDocument()
  })

  it('should_close_menu_when_trigger_clicked_again', () => {
    renderDropdown()
    const trigger = screen.getByRole('button', { name: '快速接入' })

    fireEvent.click(trigger)
    fireEvent.click(trigger)

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('should_close_menu_when_clicking_outside', () => {
    renderDropdown()
    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))

    fireEvent.mouseDown(screen.getByRole('button', { name: '页面上的其他按钮' }))

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('should_close_menu_when_escape_pressed', () => {
    renderDropdown()
    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})
