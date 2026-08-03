import { useState } from 'react'
import { Button } from './Button'

export interface CommandBlockProps {
  readonly label: string
  readonly command: string
}

const COPIED_LABEL_DURATION_MS = 2000

/**
 * 单条可复制命令的展示块(标签 + 命令 + 复制按钮)。`QuickInstallMenu.tsx` 内部有一份
 * 私有实现(`CommandBlock` 未导出),安装单页(`InstallPage`)需要完全相同的展示,但那个
 * 文件不许改,所以独立成一个可复用组件而不是复制粘贴一份私有函数。
 */
export function CommandBlock({ label, command }: CommandBlockProps) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(command)
    setCopied(true)
    window.setTimeout(() => setCopied(false), COPIED_LABEL_DURATION_MS)
  }

  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <span className="text-sm font-medium">{label}</span>
        <Button size="sm" variant="ghost" onClick={handleCopy}>
          {copied ? '已复制' : '复制'}
        </Button>
      </div>
      <pre className="overflow-x-auto rounded bg-gray-900 p-3 text-xs text-gray-100">
        <code>{command}</code>
      </pre>
    </div>
  )
}
