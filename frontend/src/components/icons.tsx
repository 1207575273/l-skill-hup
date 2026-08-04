import type { SVGProps } from 'react'

/**
 * 手绘极简图标集,不引任何图标库(项目宪法:不新增依赖)。当前用到的五处:
 * 下拉箭头(SkillCard 快速接入)、认证徽章的对勾/警示三角(CertificationBadge)、
 * Modal 的关闭按钮、下载(DownloadSkillButton)。
 * 都是纯装饰,`aria-hidden` 恒为 true,文字含义靠旁边的文案传达。
 */
type IconProps = SVGProps<SVGSVGElement>

export function ChevronDownIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" {...props}>
      <path
        fillRule="evenodd"
        d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08Z"
        clipRule="evenodd"
      />
    </svg>
  )
}

export function CheckCircleIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" {...props}>
      <path
        fillRule="evenodd"
        d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm3.7-9.3a1 1 0 0 0-1.4-1.4L9 10.59l-1.3-1.3a1 1 0 0 0-1.4 1.42l2 2a1 1 0 0 0 1.4 0l4-4Z"
        clipRule="evenodd"
      />
    </svg>
  )
}

export function WarningTriangleIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" {...props}>
      <path
        fillRule="evenodd"
        d="M9.4 2.7a1.2 1.2 0 0 1 1.2 0c.2.1.4.3.5.5l7 12.2c.4.7 0 1.6-.9 1.6H2.8c-.9 0-1.3-.9-.9-1.6l7-12.2c.1-.2.3-.4.5-.5ZM10 7a.75.75 0 0 1 .75.75v3a.75.75 0 0 1-1.5 0v-3A.75.75 0 0 1 10 7Zm0 6.5a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8Z"
        clipRule="evenodd"
      />
    </svg>
  )
}

export function CloseIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" {...props}>
      <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
    </svg>
  )
}

/** 向下箭头 + 托盘,通用的"下载"隐喻。 */
export function DownloadIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" {...props}>
      <path d="M10 2.5a.75.75 0 0 1 .75.75v7.19l2.22-2.22a.75.75 0 1 1 1.06 1.06l-3.5 3.5a.75.75 0 0 1-1.06 0l-3.5-3.5a.75.75 0 0 1 1.06-1.06l2.22 2.22V3.25A.75.75 0 0 1 10 2.5Z" />
      <path d="M3.5 12.75a.75.75 0 0 1 .75.75v1.5c0 .14.11.25.25.25h11a.25.25 0 0 0 .25-.25v-1.5a.75.75 0 0 1 1.5 0v1.5A1.75 1.75 0 0 1 15.5 16.75h-11A1.75 1.75 0 0 1 2.75 15v-1.5a.75.75 0 0 1 .75-.75Z" />
    </svg>
  )
}
