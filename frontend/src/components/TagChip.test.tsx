import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { TagChip } from './TagChip'

describe('TagChip', () => {
  it('should_render_name_without_hash_when_kind_is_curated', () => {
    render(<TagChip tag={{ id: 1, name: '代码审查', kind: 'CURATED' }} />)

    expect(screen.getByText('代码审查')).toBeInTheDocument()
    expect(screen.queryByText('#代码审查')).not.toBeInTheDocument()
  })

  it('should_render_name_with_hash_when_kind_is_free', () => {
    render(<TagChip tag={{ id: 2, name: '金字塔原理', kind: 'FREE' }} />)

    expect(screen.getByText('#金字塔原理')).toBeInTheDocument()
  })
})
