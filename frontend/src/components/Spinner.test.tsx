import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Spinner } from './Spinner'

describe('Spinner', () => {
  it('should_expose_default_label_to_screen_readers_when_label_not_provided', () => {
    render(<Spinner />)

    expect(screen.getByRole('status')).toHaveTextContent('加载中')
  })

  it('should_expose_custom_label_to_screen_readers_when_label_provided', () => {
    render(<Spinner label="上传中" />)

    expect(screen.getByRole('status')).toHaveTextContent('上传中')
  })
})
