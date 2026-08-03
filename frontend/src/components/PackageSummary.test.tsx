import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PackageValidationReport } from '../lib/skillPackageTypes'
import { PackageSummary } from './PackageSummary'

const REPORT: PackageValidationReport = {
  valid: true,
  root: null,
  parsed: { name: 'code-review', description: '代码审查 skill', allowedTools: ['Read', 'Grep'] },
  fileCount: 3,
  unpackedBytes: 2048,
  hasReadme: true,
  violations: [],
}

describe('PackageSummary', () => {
  it('should_render_parsed_frontmatter_fields_when_present', () => {
    render(<PackageSummary report={REPORT} />)

    expect(screen.getByText('code-review')).toBeInTheDocument()
    expect(screen.getByText('代码审查 skill')).toBeInTheDocument()
    expect(screen.getByText('Read, Grep')).toBeInTheDocument()
  })

  it('should_format_unpackedBytes_in_kilobytes_when_over_1024', () => {
    render(<PackageSummary report={REPORT} />)

    expect(screen.getByText('2.0 KB')).toBeInTheDocument()
  })

  it('should_render_yes_when_hasReadme_true', () => {
    render(<PackageSummary report={REPORT} />)

    expect(screen.getByText('有')).toBeInTheDocument()
  })

  it('should_render_placeholder_when_allowedTools_empty', () => {
    const reportWithoutAllowedTools: PackageValidationReport = {
      ...REPORT,
      parsed: { name: 'code-review', description: '代码审查 skill', allowedTools: [] },
    }
    render(<PackageSummary report={reportWithoutAllowedTools} />)

    expect(screen.getByText('未声明')).toBeInTheDocument()
  })
})
