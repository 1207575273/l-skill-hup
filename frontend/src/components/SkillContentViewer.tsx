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
 * 项目没有引入任何 Markdown 渲染依赖(frontend/CLAUDE.md 第六节的范围之外,任务明令不许自行
 * `pnpm add`),这一轮用等宽字体 `<pre>` 原样展示、保留换行与空白,不解析 —— 半吊子 Markdown
 * 解析器比不解析更糟,渲染错了没人看得出来。是否要接一个真正的渲染器由后续任务决定。
 */
export function SkillContentViewer({ readmeContent, skillMdContent }: SkillContentViewerProps) {
  // 严格按文档定义的"非空"判断——是否为 null,不是是否非空字符串。空字符串的 README 理论上不该
  // 出现(precheck 阶段就该拒绝),但即使出现也仍应视为"存在",与后端 5.6 的判断口径保持一致。
  const usingReadme = readmeContent !== null
  const content = usingReadme ? readmeContent : skillMdContent

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium text-gray-500 dark:text-gray-400">
        {usingReadme ? '当前显示:README.md' : '当前显示:SKILL.md(未提供 README.md)'}
      </p>
      <pre className="max-h-[32rem] overflow-auto whitespace-pre-wrap break-words rounded-card border border-gray-200 bg-gray-50 p-4 font-mono text-sm text-gray-800 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-200">
        {content}
      </pre>
    </div>
  )
}
