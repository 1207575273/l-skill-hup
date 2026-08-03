import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Tag } from '../api/types'
import { CuratedTagPicker } from './CuratedTagPicker'

const TAGS: Tag[] = [
  { id: 1, name: '代码审查', kind: 'CURATED', description: null, sortOrder: 10, skillCount: 3 },
  { id: 2, name: '测试', kind: 'CURATED', description: null, sortOrder: 20, skillCount: 1 },
  { id: 3, name: '文档', kind: 'CURATED', description: null, sortOrder: 30, skillCount: 0 },
]

describe('CuratedTagPicker', () => {
  it('should_invoke_onChange_with_added_id_when_unchecked_tag_clicked', () => {
    const handleChange = vi.fn()
    render(<CuratedTagPicker tags={TAGS} value={[]} onChange={handleChange} max={3} />)

    fireEvent.click(screen.getByRole('checkbox', { name: '代码审查' }))

    expect(handleChange).toHaveBeenCalledWith([1])
  })

  it('should_invoke_onChange_with_removed_id_when_checked_tag_clicked', () => {
    const handleChange = vi.fn()
    render(<CuratedTagPicker tags={TAGS} value={[1, 2]} onChange={handleChange} max={3} />)

    fireEvent.click(screen.getByRole('checkbox', { name: '代码审查' }))

    expect(handleChange).toHaveBeenCalledWith([2])
  })

  it('should_disable_unchecked_options_when_max_reached', () => {
    const handleChange = vi.fn()
    render(<CuratedTagPicker tags={TAGS} value={[1, 2]} onChange={handleChange} max={2} />)

    const remaining = screen.getByRole('checkbox', { name: '文档' })
    expect(remaining).toBeDisabled()

    fireEvent.click(remaining)
    expect(handleChange).not.toHaveBeenCalled()
  })

  it('should_render_errorMessage_when_provided', () => {
    render(
      <CuratedTagPicker
        tags={TAGS}
        value={[]}
        onChange={() => {}}
        max={3}
        errorMessage="分类标签需选择 1~3 个"
      />,
    )

    expect(screen.getByText('分类标签需选择 1~3 个')).toBeInTheDocument()
  })
})
