import { type ReactNode, useEffect, useId } from 'react'
import type { ButtonVariant } from './Button'
import { Button } from './Button'
import { CloseIcon } from './icons'

export interface ModalAction {
  readonly label: string
  readonly onClick: () => void
  readonly variant?: ButtonVariant
}

export interface ModalProps {
  readonly open: boolean
  readonly title: string
  readonly onClose: () => void
  /** 正文插槽,如重名二选一弹窗里的说明文字与已有版本信息。 */
  readonly children: ReactNode
  /** 从左到右渲染的操作按钮,如"覆盖已有版本" / "作为新版本发布" / "取消"。 */
  readonly actions?: readonly ModalAction[]
}

/**
 * 通用弹窗,基础件之一。上传页的重名二选一交互就是拿它拼:`title` 给出冲突说明,
 * `children` 放已有版本的信息,`actions` 放两个决策按钮 + 一个取消。
 *
 * 关闭方式覆盖三种:点右上角关闭按钮、按 Escape、点遮罩层。不做完整的焦点陷阱
 * (打开时把焦点锁在弹窗内、Tab 循环),这个量级的弹窗(确认类,不含复杂表单)
 * 暂不值得为此引入额外复杂度,真出现可用性问题再补。
 */
export function Modal({ open, title, onClose, children, actions }: ModalProps) {
  const titleId = useId()

  useEffect(() => {
    if (!open) {
      return
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/50 p-4">
      {/* 遮罩层用真的 <button> 而不是带 onClick 的 <div>:原生按钮自带键盘可达性
          (Enter/Space 触发),不用额外补 onKeyDown 才能过 a11y 检查。对话框内容
          在 DOM 里排在后面,视觉上盖在遮罩之上,点内容不会穿透到遮罩按钮。 */}
      <button
        type="button"
        aria-label="关闭弹窗"
        className="absolute inset-0 cursor-default"
        onClick={onClose}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="relative w-full max-w-md rounded-card bg-white p-6 shadow-xl dark:bg-gray-900"
      >
        <div className="flex items-start justify-between gap-4">
          <h2 id={titleId} className="text-base font-semibold text-gray-900 dark:text-gray-100">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
          >
            <CloseIcon className="h-5 w-5" />
          </button>
        </div>
        <div className="mt-4 text-sm text-gray-700 dark:text-gray-300">{children}</div>
        {actions && actions.length > 0 && (
          <div className="mt-6 flex justify-end gap-2">
            {actions.map((action) => (
              <Button
                key={action.label}
                variant={action.variant ?? 'secondary'}
                onClick={action.onClick}
              >
                {action.label}
              </Button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
