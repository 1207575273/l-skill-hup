import { useMemo } from 'react'
import type { SkillFileContent } from '../api/types'
import { renderMarkdownToSafeHtml } from '../lib/markdown'
import { EmptyState } from './EmptyState'
import { Spinner } from './Spinner'

export interface SkillFilePreviewProps {
  /** 当前选中的路径。为 `null` 表示还没点任何文件。 */
  readonly path: string | null
  readonly content: SkillFileContent | null
  readonly isLoading: boolean
  readonly isError: boolean
}

/** 按扩展名决定是否走 Markdown 渲染。**只影响排版,不影响安全** —— 净化在 renderMarkdownToSafeHtml 里。 */
const MARKDOWN_EXTENSIONS = ['.md', '.markdown']

function isMarkdown(path: string): boolean {
  const lower = path.toLowerCase()
  return MARKDOWN_EXTENSIONS.some((extension) => lower.endsWith(extension))
}

const NOT_PREVIEWABLE_MESSAGE: Record<string, string> = {
  BINARY: '这是二进制文件,不支持预览。安装后可在本地打开。',
  TOO_LARGE: '文件过大,不支持在线预览。安装后可在本地打开。',
}

/**
 * 文件树右侧的内容预览。
 *
 * <p><b>Markdown 走渲染、其余一律等宽原样展示。</b>脚本、配置、代码要的是"看清每个字符、能整段复制",
 * 渲染反而会吃掉缩进与符号;而 README / SKILL.md 是给人读的文档,不渲染就是一堆 `#` 和 `*`。
 * 按扩展名分流是纯排版决策 —— **安全边界在 `renderMarkdownToSafeHtml` 里**,与走哪条分支无关:
 * 非 Markdown 分支用 `{content}` 交给 React 转义,Markdown 分支的 HTML 出自 DOMPurify 白名单。
 *
 * <p>"不可预览"由服务端判定并给出原因,前端只负责翻译成人话。判定放服务端是因为它才拿得到字节 ——
 * 前端连文件内容都没有,没法知道里面有没有 NUL 字节。
 */
export function SkillFilePreview({ path, content, isLoading, isError }: SkillFilePreviewProps) {
  const html = useMemo(() => {
    if (content === null || !content.previewable || content.content === null) {
      return ''
    }
    return isMarkdown(content.path) ? renderMarkdownToSafeHtml(content.content) : ''
  }, [content])

  if (path === null) {
    return (
      <EmptyState
        title="选择左侧文件查看内容"
        description="Markdown 会渲染成排版后的文档,脚本与配置按原样展示。"
      />
    )
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" label="正在读取文件内容" />
      </div>
    )
  }

  if (isError || content === null) {
    return <EmptyState title="读取失败" description={`无法读取 ${path},请稍后重试。`} />
  }

  if (!content.previewable) {
    return (
      <EmptyState
        title="不支持预览"
        description={
          NOT_PREVIEWABLE_MESSAGE[content.notPreviewableReason ?? ''] ?? '这个文件不支持在线预览。'
        }
      />
    )
  }

  return (
    <div className="flex h-full flex-col gap-2">
      <p className="shrink-0 font-mono text-xs text-gray-500 dark:text-gray-400">{content.path}</p>
      {html !== '' ? (
        <div
          className="markdown-body flex-1 overflow-auto rounded-card border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900"
          // biome-ignore lint/security/noDangerouslySetInnerHtml: 内容出自 renderMarkdownToSafeHtml,出口是 DOMPurify 白名单
          dangerouslySetInnerHTML={{ __html: html }}
        />
      ) : (
        // 非 Markdown 走这里。**不换行、单独横向滚动** —— 强制折行会让人复制到断开的命令,
        // 而脚本正是最常被整段复制的东西。
        <pre className="flex-1 overflow-auto rounded-card border border-gray-200 bg-gray-50 p-4 font-mono text-sm text-gray-800 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-200">
          {content.content}
        </pre>
      )}
    </div>
  )
}
