import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as installApi from '../api/install'
import { QuickInstallMenu } from './QuickInstallMenu'

vi.mock('../api/install')

function renderWithQuery(node: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>)
}

describe('QuickInstallMenu', () => {
  beforeEach(() => {
    vi.mocked(installApi.fetchInstallClients).mockResolvedValue([
      { client: 'claude', scope: 'global', skillsSubDir: '.claude/skills' },
      { client: 'claude', scope: 'project', skillsSubDir: '.claude/skills' },
    ])
    vi.mocked(installApi.createInstallBundle).mockResolvedValue({
      token: 'abc123',
      expiresAt: '2026-09-01T00:00:00Z',
      client: 'claude',
      scope: 'global',
      powerShellCommand: 'irm http://x/skillhub/i/abc123.ps1 | iex',
      bashCommand: 'curl -fsSL http://x/skillhub/i/abc123.sh | bash',
    })
  })

  it('should_list_only_targets_returned_by_server_when_menu_opened', async () => {
    // 适配表里未实测的组合(当前是 workbuddy 的项目级)后端压根不返回,
    // 前端不需要也不该自己判断哪些能装 —— 猜路径是明令禁止的。
    renderWithQuery(<QuickInstallMenu skillId={1} versionId={10} />)

    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))

    expect(await screen.findByText('Claude Code · 全局')).toBeInTheDocument()
    expect(screen.getByText('Claude Code · 当前项目')).toBeInTheDocument()
    expect(screen.queryByText(/WorkBuddy/)).not.toBeInTheDocument()
  })

  it('should_show_both_commands_from_server_when_target_picked', async () => {
    // 命令文本由后端拼好返回,前端只展示。自己拼模板等于制造第二个事实源。
    renderWithQuery(<QuickInstallMenu skillId={1} versionId={10} />)

    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))
    fireEvent.click(await screen.findByText('Claude Code · 全局'))

    await waitFor(() => {
      expect(screen.getByText('irm http://x/skillhub/i/abc123.ps1 | iex')).toBeInTheDocument()
    })
    expect(screen.getByText('curl -fsSL http://x/skillhub/i/abc123.sh | bash')).toBeInTheDocument()
  })

  it('should_request_bundle_for_the_given_skill_version_when_target_picked', async () => {
    renderWithQuery(<QuickInstallMenu skillId={42} versionId={99} />)

    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))
    fireEvent.click(await screen.findByText('Claude Code · 当前项目'))

    await waitFor(() => {
      expect(installApi.createInstallBundle).toHaveBeenCalledWith(
        [{ skillId: 42, versionId: 99 }],
        'claude',
        'project',
      )
    })
  })

  it('should_show_expiry_notice_when_command_rendered', async () => {
    // 有效期要显式告诉人:旧命令在群里流传半年后还能装到过期版本,正是它要防的事。
    renderWithQuery(<QuickInstallMenu skillId={1} versionId={10} />)

    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))
    fireEvent.click(await screen.findByText('Claude Code · 全局'))

    expect(await screen.findByText(/之后失效/)).toBeInTheDocument()
  })
})
