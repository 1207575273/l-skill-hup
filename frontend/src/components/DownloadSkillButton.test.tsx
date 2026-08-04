import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { API_BASE_URL } from '../api/client'
import { DownloadSkillButton } from './DownloadSkillButton'

describe('DownloadSkillButton', () => {
  /**
   * 必须是 `<a>` 而不是 `<button>`:下载是浏览器原生能力。渲染成 button 的话点了什么都不会发生,
   * 而这是"给完全不懂的人用"的那个按钮 —— 它坏掉时最没人能自己绕过去。
   */
  it('should_render_as_anchor_with_download_attribute', () => {
    render(<DownloadSkillButton skillId={42} version="1.0.0" />)

    const link = screen.getByRole('link', { name: /下载/ })
    expect(link).toHaveAttribute('href', `${API_BASE_URL}/skills/42/versions/1.0.0/download`)
    expect(link).toHaveAttribute('download')
  })

  /** 版本号进 URL 路径,带特殊字符时必须 encode,否则拼出来的地址是坏的。 */
  it('should_encode_version_in_url', () => {
    render(<DownloadSkillButton skillId={7} version="1.0.0+build 1" />)

    expect(screen.getByRole('link', { name: /下载/ })).toHaveAttribute(
      'href',
      `${API_BASE_URL}/skills/7/versions/${encodeURIComponent('1.0.0+build 1')}/download`,
    )
  })

  it('should_use_custom_label_when_given', () => {
    render(<DownloadSkillButton skillId={1} version="2.0.0" label="下载" />)

    expect(screen.getByRole('link', { name: '下载 2.0.0 版本的 zip' })).toBeInTheDocument()
  })

  /** 无障碍名要带上版本号:一页上会有好几个"下载",只靠可见文案读屏软件区分不出来。 */
  it('should_include_version_in_accessible_name', () => {
    render(<DownloadSkillButton skillId={1} version="1.2.3" />)

    expect(screen.getByRole('link', { name: '下载 1.2.3 版本的 zip' })).toBeInTheDocument()
  })
})
