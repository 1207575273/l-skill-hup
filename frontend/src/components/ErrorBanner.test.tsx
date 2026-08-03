import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { ErrorBanner } from './ErrorBanner'

function buildError(): ApiError {
  return new ApiError({
    code: 'SKILL_NAME_DUPLICATED',
    message: '已存在同名 skill',
    traceId: '8f3c1e2a9b4d',
  })
}

describe('ErrorBanner', () => {
  let writeText: ReturnType<typeof vi.fn>

  beforeEach(() => {
    writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('should_show_message_and_traceId_when_rendered', () => {
    render(<ErrorBanner error={buildError()} />)

    expect(screen.getByRole('alert')).toHaveTextContent('已存在同名 skill')
    expect(screen.getByText('8f3c1e2a9b4d')).toBeInTheDocument()
  })

  it('should_copy_traceId_to_clipboard_when_copy_button_clicked', async () => {
    render(<ErrorBanner error={buildError()} />)

    fireEvent.click(screen.getByRole('button', { name: '复制 traceId' }))

    expect(writeText).toHaveBeenCalledWith('8f3c1e2a9b4d')
    expect(await screen.findByRole('button', { name: '已复制' })).toBeInTheDocument()
  })

  it('should_render_retry_button_when_onRetry_provided', () => {
    const handleRetry = vi.fn()
    render(<ErrorBanner error={buildError()} onRetry={handleRetry} />)

    fireEvent.click(screen.getByRole('button', { name: '重试' }))

    expect(handleRetry).toHaveBeenCalledTimes(1)
  })

  it('should_not_render_retry_button_when_onRetry_not_provided', () => {
    render(<ErrorBanner error={buildError()} />)

    expect(screen.queryByRole('button', { name: '重试' })).not.toBeInTheDocument()
  })
})
