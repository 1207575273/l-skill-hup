import type { DirectoryTreeNode } from '../lib/directoryTree'
import { buildTreeFromPaths } from '../lib/directoryTree'
import { cx } from './classNames'
import { formatFileSize } from './skillDetailFormat'

export interface SkillFileTreeProps {
  /** 版本的文件清单,来自 `latestVersion.files`。存量版本可能是空数组。 */
  readonly files: readonly { readonly path: string; readonly sizeBytes: number }[]
  /** 当前选中的文件路径,没有选中时为 `null`。 */
  readonly selectedPath: string | null
  /** 点选某个文件。目录不可点 —— 目录没有内容可预览,让它可点只会制造一次无效点击。 */
  readonly onSelectFile: (path: string) => void
}

/**
 * skill 制品的完整文件结构,可点选。
 *
 * <p><b>默认全展开,不做折叠。</b>这个部件的目的就是让人一眼看清"这个 skill 到底装了些什么" ——
 * 折叠起来等于把答案又藏回去了,还得逐层点开。skill 包受 5.2 的上限约束(条目数 500、深度 8),
 * 不存在需要靠折叠才能看的规模;真到几百个文件时,外层的滚动容器兜住高度就够了。
 *
 * <p>用 `ul`/`li` 嵌套而不是拿 div 拼缩进:目录层级本身就是列表的嵌套关系,屏幕阅读器能直接播报
 * 层级与条目数,而 div + `padding-left` 对读屏软件是一片没有结构的文本。
 *
 * <p>文件节点用 `button` 而不是给 `span` 加 onClick:键盘能 Tab 到、能回车触发、读屏软件会播报成
 * 可操作元素 —— 这三件事加 onClick 都得不到,而补齐它们要手写 tabIndex + onKeyDown + role,
 * 还不如直接用天生就有这些行为的元素。
 */
export function SkillFileTree({ files, selectedPath, onSelectFile }: SkillFileTreeProps) {
  if (files.length === 0) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">该版本暂无文件清单信息</p>
  }

  const tree = buildTreeFromPaths(files)

  return (
    <div className="h-full overflow-auto rounded-card border border-gray-200 bg-gray-50 p-3 dark:border-gray-800 dark:bg-gray-900">
      <TreeLevel nodes={tree} selectedPath={selectedPath} onSelectFile={onSelectFile} />
    </div>
  )
}

interface TreeLevelProps {
  readonly nodes: readonly DirectoryTreeNode[]
  readonly selectedPath: string | null
  readonly onSelectFile: (path: string) => void
}

function TreeLevel({ nodes, selectedPath, onSelectFile }: TreeLevelProps) {
  return (
    <ul className="flex flex-col gap-0.5">
      {nodes.map((node) => (
        <li key={node.path}>
          {node.type === 'directory' ? (
            <>
              <span className="flex items-center gap-1.5 py-0.5 text-sm font-medium text-gray-700 dark:text-gray-200">
                <FolderGlyph />
                {node.name}
              </span>
              {/* 缩进画在嵌套层上而不是每个条目上:靠左边框顺带把层级关系变成一条可见的竖线 */}
              <div className="ml-2 border-l border-gray-200 pl-3 dark:border-gray-700">
                <TreeLevel
                  nodes={node.children}
                  selectedPath={selectedPath}
                  onSelectFile={onSelectFile}
                />
              </div>
            </>
          ) : (
            <button
              type="button"
              onClick={() => onSelectFile(node.path)}
              aria-current={node.path === selectedPath ? 'true' : undefined}
              className={cx(
                'flex w-full items-center gap-1.5 rounded px-1 py-0.5 text-left text-sm',
                node.path === selectedPath
                  ? 'bg-brand-100 text-brand-700 dark:bg-brand-700/30 dark:text-brand-500'
                  : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800',
              )}
            >
              <FileGlyph />
              {/* title 给完整路径而不是文件名:树宽度固定,深层文件名必然被截断,
                  而 hover 时看到完整路径比只看到文件名更有用(能确认是哪个目录下的那一个)。 */}
              <span className="truncate font-mono" title={node.path}>
                {node.name}
              </span>
              <span className="ml-auto shrink-0 text-xs text-gray-400 dark:text-gray-500">
                {formatFileSize(node.size)}
              </span>
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}

/* 两个图形用内联 SVG 而不是图标库:项目不引 UI 组件库(frontend/CLAUDE.md 第四节),
   而这两个形状简单到不值得为它引一个依赖。aria-hidden 是因为它们纯装饰 ——
   条目是文件还是目录,读屏软件从列表嵌套结构就能知道,再念一遍图标只是噪音。 */
function FolderGlyph() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 16 16"
      className="h-3.5 w-3.5 shrink-0 fill-current text-gray-400 dark:text-gray-500"
    >
      <path d="M1.5 3.5A1.5 1.5 0 0 1 3 2h3l1.5 1.5H13A1.5 1.5 0 0 1 14.5 5v6.5A1.5 1.5 0 0 1 13 13H3a1.5 1.5 0 0 1-1.5-1.5v-8Z" />
    </svg>
  )
}

function FileGlyph() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 16 16"
      className="h-3.5 w-3.5 shrink-0 fill-current text-gray-400 dark:text-gray-500"
    >
      <path d="M4 1.5h5L12.5 5v9a.5.5 0 0 1-.5.5H4a.5.5 0 0 1-.5-.5V2a.5.5 0 0 1 .5-.5Zm5 1V5h2.5L9 2.5Z" />
    </svg>
  )
}
