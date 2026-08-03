import { useMemo } from 'react'
import { renderMarkdownToSafeHtml } from '../lib/markdown'

export interface SkillContentViewerProps {
  readonly readmeContent: string | null
  readonly skillMdContent: string
}

/**
 * skill 详情页正文:README 优先,没有才回落 SKILL.md(业务建模文档 5.6)。
 *
 * **必须让人看得出当前显示的是哪一个**——不加标识的话,作者写了 README 却因为解析问题没生效,
 * 页面上完全看不出来。所以正文上方总有一行来源标签。
 *
 * **用 `dangerouslySetInnerHTML` 是经过权衡的,不是图省事。** 这里插的 HTML 全部来自
 * `renderMarkdownToSafeHtml`,那个函数出口就是 DOMPurify —— 它的白名单里没有 `script`、
 * 没有事件属性、没有 `style`。绕过它直接插入原始内容会被
 * `should_strip_script_when_content_embeds_raw_html` 拦下。
 *
 * 排版样式挂在 `.markdown-body` 上,定义在 `styles/index.css`。Markdown 生成的元素上没有 class,
 * 只能靠后代选择器,这正是 frontend/CLAUDE.md 第四节所说"确实无法组件化的场景"。
 */
export function SkillContentViewer({ readmeContent, skillMdContent }: SkillContentViewerProps) {
  // 严格按文档定义的"非空"判断——是否为 null,不是是否非空字符串。空字符串的 README 理论上不该
  // 出现(precheck 阶段就该拒绝),但即使出现也仍应视为"存在",与后端 5.6 的判断口径保持一致。
  const usingReadme = readmeContent !== null
  const content = usingReadme ? readmeContent : skillMdContent

  // 解析 + 净化对一篇长 README 不算便宜,而这个组件会随详情页上任何状态变化重渲染
  // (切版本、展开快速接入、编辑元数据)。内容没变就不该重算。
  const html = useMemo(() => renderMarkdownToSafeHtml(content), [content])

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium text-gray-500 dark:text-gray-400">
        {usingReadme ? '当前显示:README.md' : '当前显示:SKILL.md(未提供 README.md)'}
      </p>
      {html === '' ? (
        <p className="rounded-card border border-gray-200 bg-gray-50 p-4 text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-400">
          该 skill 没有提供说明内容
        </p>
      ) : (
        <div
          className="markdown-body max-h-[32rem] overflow-auto rounded-card border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900"
          // biome-ignore lint/security/noDangerouslySetInnerHtml: 内容出自 renderMarkdownToSafeHtml,出口是 DOMPurify 白名单,理由见组件注释
          dangerouslySetInnerHTML={{ __html: html }}
        />
      )}
    </div>
  )
}
