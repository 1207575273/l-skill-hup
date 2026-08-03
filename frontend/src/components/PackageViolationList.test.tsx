import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PackageViolationList } from './PackageViolationList'

describe('PackageViolationList', () => {
  it('should_render_nothing_when_no_violations', () => {
    const { container } = render(<PackageViolationList title="校验未通过" violations={[]} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('should_render_title_and_each_violation_when_violations_present', () => {
    render(
      <PackageViolationList
        title="本地校验未通过"
        violations={[
          { code: 'SKILL_MD_MISSING', message: '包内缺少 SKILL.md' },
          { code: 'PATH_TRAVERSAL', message: '路径包含 .. 段', path: 'references/../../etc' },
        ]}
      />,
    )

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('本地校验未通过')).toBeInTheDocument()
    expect(screen.getByText('SKILL_MD_MISSING')).toBeInTheDocument()
    expect(screen.getByText('包内缺少 SKILL.md')).toBeInTheDocument()
    expect(screen.getByText('PATH_TRAVERSAL')).toBeInTheDocument()
    expect(screen.getByText('references/../../etc')).toBeInTheDocument()
  })
})
