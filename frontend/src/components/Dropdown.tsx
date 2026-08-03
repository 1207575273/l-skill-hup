import { type ReactNode, useEffect, useRef, useState } from 'react'
import { cx } from './classNames'

export interface DropdownRenderTriggerProps {
  readonly onClick: () => void
  readonly open: boolean
}

export interface DropdownProps {
  /**
   * 触发器用 render prop 而不是直接传 `ReactNode`:面板的开关状态(`open`)与切换
   * 回调(`onClick`)要接到触发器自己的可交互元素(通常是 `Button`)上,而不是再套
   * 一层 `<div onClick>` 包住任意节点——那样会出现"可交互元素嵌套可交互元素",
   * 键盘可达性和语义都不对。
   */
  readonly renderTrigger: (props: DropdownRenderTriggerProps) => ReactNode
  /** 展开面板的内容插槽,如"快速接入"下拉里的客户端/安装级别选项。 */
  readonly children: ReactNode
  readonly align?: 'start' | 'end'
}

/**
 * 通用的锚定下拉/菜单容器,基础件之一。展示约定 7.4 的"快速接入"下拉需要它,
 * `SkillCard` 在内部用它包住主操作按钮;面板内容(从 `GET /clients` 渲染出的选项)
 * 由调用方注入,本组件不发请求、不知道客户端列表长什么样。
 */
export function Dropdown({ renderTrigger, children, align = 'start' }: DropdownProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target
      if (target instanceof Node && !containerRef.current?.contains(target)) {
        setOpen(false)
      }
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  return (
    <div ref={containerRef} className="relative inline-block">
      {renderTrigger({ open, onClick: () => setOpen((prev) => !prev) })}
      {open && (
        <div
          role="menu"
          className={cx(
            'absolute z-20 mt-2 min-w-48 rounded-control border border-gray-200 bg-white p-1 shadow-lg dark:border-gray-700 dark:bg-gray-900',
            align === 'end' ? 'right-0' : 'left-0',
          )}
        >
          {children}
        </div>
      )}
    </div>
  )
}
