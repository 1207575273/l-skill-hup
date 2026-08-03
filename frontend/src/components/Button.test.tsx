import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'

describe('Button', () => {
  it('should_invoke_onClick_when_clicked', () => {
    const handleClick = vi.fn()
    render(<Button onClick={handleClick}>快速接入</Button>)

    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('should_default_to_button_type_when_type_not_specified', () => {
    render(<Button>提交</Button>)

    expect(screen.getByRole('button', { name: '提交' })).toHaveAttribute('type', 'button')
  })

  it('should_not_invoke_onClick_when_disabled', () => {
    const handleClick = vi.fn()
    render(
      <Button disabled onClick={handleClick}>
        禁用中
      </Button>,
    )

    fireEvent.click(screen.getByRole('button', { name: '禁用中' }))

    expect(handleClick).not.toHaveBeenCalled()
  })

  it('should_disable_button_and_show_status_when_loading', () => {
    render(<Button loading>提交</Button>)

    // loading 时 Spinner 的屏幕阅读器文案会并入按钮的可访问名,不能再用 name 精确匹配。
    const button = screen.getByRole('button')
    expect(button).toBeDisabled()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})
