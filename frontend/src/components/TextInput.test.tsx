import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { TextInput } from './TextInput'

describe('TextInput', () => {
  it('should_associate_label_with_input_when_rendered', () => {
    render(<TextInput label="skill 名称" defaultValue="" onChange={() => {}} />)

    expect(screen.getByLabelText('skill 名称')).toBeInTheDocument()
  })

  it('should_invoke_onChange_when_user_types', () => {
    const handleChange = vi.fn()
    render(<TextInput label="skill 名称" defaultValue="" onChange={handleChange} />)

    fireEvent.change(screen.getByLabelText('skill 名称'), { target: { value: 'pdf-summarizer' } })

    expect(handleChange).toHaveBeenCalledTimes(1)
  })

  it('should_mark_input_invalid_and_show_error_message_when_errorMessage_provided', () => {
    render(<TextInput label="skill 名称" defaultValue="" errorMessage="名称已存在" />)

    const input = screen.getByLabelText('skill 名称')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('名称已存在')).toBeInTheDocument()
  })
})
