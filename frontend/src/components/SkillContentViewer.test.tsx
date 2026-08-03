import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SkillContentViewer } from './SkillContentViewer'

describe('SkillContentViewer', () => {
  it('should_render_readme_and_label_when_readme_present', () => {
    render(<SkillContentViewer readmeContent="# README 正文" skillMdContent="# SKILL.md 正文" />)

    expect(screen.getByText('当前显示:README.md')).toBeInTheDocument()
    expect(screen.getByText('# README 正文')).toBeInTheDocument()
    expect(screen.queryByText('# SKILL.md 正文')).not.toBeInTheDocument()
  })

  it('should_fallback_to_skill_md_and_label_when_readme_missing', () => {
    render(<SkillContentViewer readmeContent={null} skillMdContent="# SKILL.md 正文" />)

    expect(screen.getByText('当前显示:SKILL.md(未提供 README.md)')).toBeInTheDocument()
    expect(screen.getByText('# SKILL.md 正文')).toBeInTheDocument()
  })

  it('should_preserve_line_breaks_when_rendering_content', () => {
    const { container } = render(
      <SkillContentViewer readmeContent={'第一行\n第二行'} skillMdContent="skill md" />,
    )

    expect(container.querySelector('pre')?.textContent).toBe('第一行\n第二行')
  })
})
