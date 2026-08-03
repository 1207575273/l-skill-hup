import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CertificationBadge } from './CertificationBadge'

describe('CertificationBadge', () => {
  it('should_render_nothing_when_certification_is_none', () => {
    const { container } = render(<CertificationBadge certification="NONE" />)

    expect(container).toBeEmptyDOMElement()
  })

  it('should_render_certified_label_when_certification_is_certified', () => {
    render(<CertificationBadge certification="CERTIFIED" />)

    expect(screen.getByText('平台认证')).toBeInTheDocument()
  })

  it('should_render_revoked_label_without_note_when_certifyNote_not_provided', () => {
    render(<CertificationBadge certification="REVOKED" />)

    expect(screen.getByText('认证已撤销')).toBeInTheDocument()
  })

  it('should_render_certifyNote_full_text_when_certification_revoked_with_note', () => {
    render(<CertificationBadge certification="REVOKED" certifyNote="包含未脱敏的内网地址" />)

    expect(screen.getByText('包含未脱敏的内网地址')).toBeInTheDocument()
  })
})
