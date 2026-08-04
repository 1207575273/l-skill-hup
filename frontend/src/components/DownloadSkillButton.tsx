import { buildSkillDownloadUrl } from '../api/skills'
import { type ButtonSize, type ButtonVariant, buttonClassName } from './Button'
import { DownloadIcon } from './icons'

export interface DownloadSkillButtonProps {
  readonly skillId: number
  /** 版本号(不是 versionId)—— 下载接口按版本号定位,与安装脚本、认证接口一致。 */
  readonly version: string
  readonly variant?: ButtonVariant
  readonly size?: ButtonSize
  readonly label?: string
}

/**
 * 直接下载某个版本的 zip。
 *
 * <p><b>为什么要有它:一键安装解决不了"完全不懂的人"。</b>"快速接入"要先选客户端、再选全局还是项目级,
 * 然后拿一条命令去终端里粘 —— 这中间每一步都假定使用者知道自己在干什么。而"下载一个 zip"是零概念的动作,
 * 任何人都会。装不装得进去是下一步的事,先让他能把东西拿到手。
 *
 * <p><b>是 {@code <a>} 不是 {@code <button>}</b>:下载是浏览器原生能力,`<a href download>` 直接交给
 * 下载管理器,支持断点续传、能看到进度、失败了浏览器自己会说。用 JS fetch 再造个临时 a 标签点一下,
 * 除了多绕一圈没有任何收益,还会把几 MB 的 zip 先读进内存。
 *
 * <p><b>不做 loading 态</b>:点击之后导航交给浏览器,页面本身什么都不会发生,给个转圈只会让人以为卡住了。
 *
 * <p>匿名可用(权限矩阵 5.16:下载 zip 不要求登录),所以这个按钮不判登录态。下载计数由服务端在字节
 * 完整写出后才增加,这里不参与。
 */
export function DownloadSkillButton({
  skillId,
  version,
  variant = 'secondary',
  size = 'md',
  label = '下载 zip',
}: DownloadSkillButtonProps) {
  return (
    <a
      href={buildSkillDownloadUrl(skillId, version)}
      // download 属性只在同源时生效,而本项目前后端同源(前端产物打进 jar 同源托管),成立。
      // 服务端也会回 Content-Disposition: attachment,两道保险 —— 少了任一道,某些浏览器会把
      // zip 当成可预览内容直接在标签页里打开,用户看到一屏乱码。
      download
      className={buttonClassName({ variant, size })}
      aria-label={`下载 ${version} 版本的 zip`}
    >
      <DownloadIcon className="h-4 w-4" />
      {label}
    </a>
  )
}
