import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CommandBlock } from './CommandBlock'

describe('CommandBlock', () => {
  it('should_render_label_and_command_when_rendered', () => {
    render(<CommandBlock label="PowerShell(Windows)" command="irm http://x/i/abc.ps1 | iex" />)

    expect(screen.getByText('PowerShell(Windows)')).toBeInTheDocument()
    expect(screen.getByText('irm http://x/i/abc.ps1 | iex')).toBeInTheDocument()
  })

  it('should_render_copy_button_when_rendered', () => {
    render(<CommandBlock label="bash" command="curl -fsSL http://x/i/abc.sh | bash" />)

    expect(screen.getByRole('button', { name: '复制' })).toBeInTheDocument()
  })
})
