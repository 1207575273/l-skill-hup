import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SkillContentViewer } from './SkillContentViewer'

describe('SkillContentViewer', () => {
  it('should_render_readme_as_markdown_and_label_when_readme_present', () => {
    render(<SkillContentViewer readmeContent="# README 正文" skillMdContent="# SKILL.md 正文" />)

    expect(screen.getByText('当前显示:README.md')).toBeInTheDocument()
    // 断言渲染成了真标题而不是字面量 "# README 正文" —— 后者说明 Markdown 根本没解析。
    expect(screen.getByRole('heading', { name: 'README 正文' })).toBeInTheDocument()
    expect(screen.queryByText(/SKILL\.md 正文/)).not.toBeInTheDocument()
  })

  it('should_fallback_to_skill_md_and_label_when_readme_missing', () => {
    render(<SkillContentViewer readmeContent={null} skillMdContent="# SKILL.md 正文" />)

    expect(screen.getByText('当前显示:SKILL.md(未提供 README.md)')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'SKILL.md 正文' })).toBeInTheDocument()
  })

  it('should_render_fenced_code_block_when_content_has_one', () => {
    const { container } = render(
      <SkillContentViewer readmeContent={'```bash\nnpm install\n```'} skillMdContent="skill md" />,
    )

    expect(container.querySelector('pre code')?.textContent).toContain('npm install')
  })

  it('should_render_list_when_content_has_bullets', () => {
    render(<SkillContentViewer readmeContent={'- 第一条\n- 第二条'} skillMdContent="skill md" />)

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('should_strip_script_when_content_embeds_raw_html', () => {
    // lib/markdown.test.ts 已经把净化规则测透了。这一条验的是**组件确实走了那条路**——
    // 只要有人图省事把原始 HTML 直接塞进 dangerouslySetInnerHTML,它就会红。
    const { container } = render(
      <SkillContentViewer
        readmeContent={'正文\n\n<script>alert(1)</script>'}
        skillMdContent="skill md"
      />,
    )

    expect(container.querySelector('script')).toBeNull()
    expect(container.innerHTML).not.toContain('alert(1)')
  })

  it('should_show_placeholder_when_content_is_empty', () => {
    render(<SkillContentViewer readmeContent="" skillMdContent="skill md" />)

    // 空 README 在 precheck 阶段就该被拦下,但真出现时要说明"这是空的",
    // 而不是渲染出一块什么都没有的白区让人以为页面坏了。
    expect(screen.getByText('该 skill 没有提供说明内容')).toBeInTheDocument()
  })
})
