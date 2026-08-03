import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('should_render_nothing_when_total_is_zero', () => {
    render(<Pagination page={1} size={20} total={0} onPageChange={() => {}} />)

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('should_disable_previous_button_when_on_first_page', () => {
    render(<Pagination page={1} size={20} total={45} onPageChange={() => {}} />)

    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).not.toBeDisabled()
  })

  it('should_disable_next_button_when_on_last_page', () => {
    render(<Pagination page={3} size={20} total={45} onPageChange={() => {}} />)

    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '上一页' })).not.toBeDisabled()
  })

  it('should_invoke_onPageChange_with_next_page_when_next_clicked', () => {
    const handlePageChange = vi.fn()
    render(<Pagination page={1} size={20} total={45} onPageChange={handlePageChange} />)

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))

    expect(handlePageChange).toHaveBeenCalledWith(2)
  })

  it('should_show_total_page_count_when_rendered', () => {
    render(<Pagination page={2} size={20} total={45} onPageChange={() => {}} />)

    expect(screen.getByText('第 2 / 3 页 · 共 45 条')).toBeInTheDocument()
  })
})
