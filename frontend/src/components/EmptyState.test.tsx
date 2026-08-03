import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('should_render_title_only_when_description_and_action_not_provided', () => {
    render(<EmptyState title="还没有 skill" />)

    expect(screen.getByText('还没有 skill')).toBeInTheDocument()
  })

  it('should_render_description_and_action_when_provided', () => {
    render(
      <EmptyState
        title="没有符合条件的 skill"
        description="试试清空筛选条件"
        action={<button type="button">清空筛选条件</button>}
      />,
    )

    expect(screen.getByText('试试清空筛选条件')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '清空筛选条件' })).toBeInTheDocument()
  })
})
