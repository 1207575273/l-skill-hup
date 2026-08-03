import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { SkillFileContent } from '../api/types'
import { SkillFilePreview } from './SkillFilePreview'

function buildContent(overrides: Partial<SkillFileContent> = {}): SkillFileContent {
  return {
    path: 'SKILL.md',
    sizeBytes: 100,
    previewable: true,
    content: '# 标题',
    notPreviewableReason: null,
    ...overrides,
  }
}

describe('SkillFilePreview', () => {
  it('should_prompt_to_pick_a_file_when_nothing_selected', () => {
    render(<SkillFilePreview path={null} content={null} isLoading={false} isError={false} />)

    expect(screen.getByText('选择左侧文件查看内容')).toBeInTheDocument()
  })

  it('should_render_markdown_when_file_is_md', () => {
    render(
      <SkillFilePreview
        path="SKILL.md"
        content={buildContent({ path: 'SKILL.md', content: '# 标题' })}
        isLoading={false}
        isError={false}
      />,
    )

    // md 走渲染:`# 标题` 变成真标题,而不是字面量文本
    expect(screen.getByRole('heading', { name: '标题' })).toBeInTheDocument()
  })

  it('should_render_script_as_plain_text_when_file_is_not_markdown', () => {
    // 脚本要的是"看清每个字符、能整段复制",渲染反而会吃掉缩进与符号。
    const { container } = render(
      <SkillFilePreview
        path="scripts/run.sh"
        content={buildContent({ path: 'scripts/run.sh', content: '#!/bin/sh\necho "hi"' })}
        isLoading={false}
        isError={false}
      />,
    )

    const pre = container.querySelector('pre')
    expect(pre?.textContent).toBe('#!/bin/sh\necho "hi"')
    // 不能被当成 Markdown 的 h1 渲染掉 —— `#!/bin/sh` 开头是 `#`
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })

  it('should_explain_reason_when_file_is_binary', () => {
    render(
      <SkillFilePreview
        path="assets/logo.png"
        content={buildContent({
          path: 'assets/logo.png',
          previewable: false,
          content: null,
          notPreviewableReason: 'BINARY',
        })}
        isLoading={false}
        isError={false}
      />,
    )

    expect(screen.getByText(/二进制文件/)).toBeInTheDocument()
  })

  it('should_explain_reason_when_file_too_large', () => {
    render(
      <SkillFilePreview
        path="data/dump.json"
        content={buildContent({
          previewable: false,
          content: null,
          notPreviewableReason: 'TOO_LARGE',
        })}
        isLoading={false}
        isError={false}
      />,
    )

    expect(screen.getByText(/文件过大/)).toBeInTheDocument()
  })

  it('should_show_error_state_when_request_failed', () => {
    render(
      <SkillFilePreview
        path="references/guide.md"
        content={null}
        isLoading={false}
        isError={true}
      />,
    )

    expect(screen.getByText('读取失败')).toBeInTheDocument()
  })

  it('should_strip_script_when_markdown_file_embeds_raw_html', () => {
    // 预览的是别人上传的文件内容,与详情页正文是同一个威胁面。
    // 这条验的是预览组件确实走了净化那条路,而不是把内容直接塞进 innerHTML。
    const { container } = render(
      <SkillFilePreview
        path="README.md"
        content={buildContent({
          path: 'README.md',
          content: '正文\n\n<script>alert(1)</script>',
        })}
        isLoading={false}
        isError={false}
      />,
    )

    expect(container.querySelector('script')).toBeNull()
    expect(container.innerHTML).not.toContain('alert(1)')
  })
})
