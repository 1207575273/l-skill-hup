import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Select } from './Select'

const CLIENT_OPTIONS = [
  { value: 'claude', label: 'Claude Code' },
  { value: 'codex', label: 'Codex' },
] as const

describe('Select', () => {
  it('should_associate_label_with_select_when_rendered', () => {
    render(
      <Select label="目标客户端" value="claude" onChange={() => {}} options={CLIENT_OPTIONS} />,
    )

    expect(screen.getByLabelText('目标客户端')).toBeInTheDocument()
  })

  it('should_invoke_onChange_with_selected_value_when_option_picked', () => {
    const handleChange = vi.fn()
    render(
      <Select label="目标客户端" value="claude" onChange={handleChange} options={CLIENT_OPTIONS} />,
    )

    fireEvent.change(screen.getByLabelText('目标客户端'), { target: { value: 'codex' } })

    expect(handleChange).toHaveBeenCalledWith('codex')
  })

  it('should_render_all_option_labels_when_given_options', () => {
    render(
      <Select label="目标客户端" value="claude" onChange={() => {}} options={CLIENT_OPTIONS} />,
    )

    expect(screen.getByRole('option', { name: 'Claude Code' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Codex' })).toBeInTheDocument()
  })
})
