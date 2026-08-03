import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as installApi from '../api/install'
import { useInstallCartStore } from '../hooks/useInstallCartStore'
import InstallPage from './InstallPage'

vi.mock('../api/install')

const CART_ITEM = { skillId: 1, versionId: 10, skillDisplayName: '代码审查助手' }

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <InstallPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('InstallPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useInstallCartStore.setState({ items: [], client: 'claude', scope: 'global' })
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

  it('should_show_empty_state_with_link_back_to_list_when_cart_is_empty', () => {
    renderPage()

    expect(screen.getByText('安装单是空的')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '回到列表页' })).toHaveAttribute('href', '/')
  })

  it('should_list_cart_items_when_cart_has_items', () => {
    useInstallCartStore.setState({ items: [CART_ITEM], client: 'claude', scope: 'global' })

    renderPage()

    expect(screen.getByText('代码审查助手')).toBeInTheDocument()
    expect(screen.queryByText('安装单是空的')).not.toBeInTheDocument()
  })

  it('should_remove_item_from_cart_when_remove_button_clicked', () => {
    useInstallCartStore.setState({ items: [CART_ITEM], client: 'claude', scope: 'global' })

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: '移除' }))

    expect(useInstallCartStore.getState().items).toEqual([])
  })

  it('should_show_both_commands_and_expiry_notice_when_bundle_generated', async () => {
    useInstallCartStore.setState({ items: [CART_ITEM], client: 'claude', scope: 'global' })

    renderPage()
    await screen.findByLabelText('安装客户端与级别')
    fireEvent.click(screen.getByRole('button', { name: '生成安装命令' }))

    await waitFor(() => {
      expect(screen.getByText('irm http://x/skillhub/i/abc123.ps1 | iex')).toBeInTheDocument()
    })
    expect(screen.getByText('curl -fsSL http://x/skillhub/i/abc123.sh | bash')).toBeInTheDocument()
    // 有效期必须显式可见:旧命令在群里流传半年后还能装到过期版本,正是它要防的事。
    expect(screen.getByText(/之后失效/)).toBeInTheDocument()
  })

  it('should_request_bundle_with_selected_client_and_scope_when_generate_clicked', async () => {
    useInstallCartStore.setState({ items: [CART_ITEM], client: 'claude', scope: 'project' })

    renderPage()
    await screen.findByLabelText('安装客户端与级别')
    fireEvent.click(screen.getByRole('button', { name: '生成安装命令' }))

    await waitFor(() => {
      expect(installApi.createInstallBundle).toHaveBeenCalledWith(
        [{ skillId: CART_ITEM.skillId, versionId: CART_ITEM.versionId }],
        'claude',
        'project',
      )
    })
  })
})
