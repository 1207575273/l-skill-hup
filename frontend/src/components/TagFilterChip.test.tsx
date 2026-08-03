import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { TagFilterChip } from './TagFilterChip'

describe('TagFilterChip', () => {
  it('should_render_name_without_hash_when_kind_is_curated', () => {
    render(
      <TagFilterChip
        tag={{ id: 1, name: '代码审查', kind: 'CURATED' }}
        selected={false}
        onToggle={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '代码审查' })).toBeInTheDocument()
  })

  it('should_render_name_with_hash_when_kind_is_free', () => {
    render(
      <TagFilterChip
        tag={{ id: 2, name: '金字塔原理', kind: 'FREE' }}
        selected={false}
        onToggle={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '#金字塔原理' })).toBeInTheDocument()
  })

  it('should_mark_pressed_when_selected', () => {
    render(
      <TagFilterChip
        tag={{ id: 1, name: '代码审查', kind: 'CURATED' }}
        selected={true}
        onToggle={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '代码审查' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('should_call_onToggle_with_tag_id_when_clicked', () => {
    const handleToggle = vi.fn()
    render(
      <TagFilterChip
        tag={{ id: 7, name: '调试', kind: 'FREE' }}
        selected={false}
        onToggle={handleToggle}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '#调试' }))

    expect(handleToggle).toHaveBeenCalledWith(7)
  })
})
