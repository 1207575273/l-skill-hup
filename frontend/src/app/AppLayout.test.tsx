import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { setAuthToken } from '../api/client'
import type { CurrentUser } from '../api/types'
import { useInstallCartStore } from '../hooks/useInstallCartStore'
import { AppLayout, type AppLayoutProps } from './AppLayout'

vi.mock('../api/auth')

const CURRENT_USER: CurrentUser = {
  id: 1,
  displayName: '张三',
  employeeNo: null,
  email: null,
  avatarUrl: null,
  role: 'USER',
  identities: [],
}

/**
 * `NavAuthStatus`(`authSlot` 未传时的默认实现)内部调用 `useCurrentUser`/`useLogout`,
 * 需要 `QueryClientProvider`——这是本文件相对占位版最主要的改动,其余断言尽量保持不变。
 */
function renderLayout(children: ReactNode = <>内容</>, authSlot?: AppLayoutProps['authSlot']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AppLayout authSlot={authSlot}>{children}</AppLayout>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AppLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAuthToken(null)
    useInstallCartStore.setState({ items: [], client: 'claude', scope: 'global' })
  })

  afterEach(() => {
    window.localStorage.clear()
  })

  it('should_link_site_name_to_home_when_rendered', () => {
    renderLayout()

    expect(screen.getByRole('link', { name: 'skill-hub' })).toHaveAttribute('href', '/')
  })

  it('should_render_browse_upload_and_install_nav_entries_when_rendered', () => {
    renderLayout()

    expect(screen.getByRole('link', { name: '浏览' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: '上传' })).toHaveAttribute('href', '/upload')
    expect(screen.getByRole('link', { name: '安装单' })).toHaveAttribute('href', '/install')
  })

  it('should_render_children_in_main_content_area_when_rendered', () => {
    renderLayout(<p>skill 列表页占位</p>)

    expect(screen.getByRole('main')).toHaveTextContent('skill 列表页占位')
  })

  it('should_show_selected_count_in_install_nav_entry_when_cart_has_items', () => {
    useInstallCartStore.setState({
      items: [{ skillId: 1, versionId: 10, skillDisplayName: 'PDF 转换' }],
      client: 'claude',
      scope: 'global',
    })

    renderLayout()

    expect(screen.getByRole('link', { name: '安装单 (1)' })).toBeInTheDocument()
  })

  it('should_render_login_link_when_authSlot_not_provided_and_not_authenticated', () => {
    renderLayout()

    expect(screen.getByRole('link', { name: '登录' })).toHaveAttribute('href', '/login')
  })

  it('should_render_custom_authSlot_when_provided', () => {
    renderLayout(<>内容</>, <span>@张三</span>)

    expect(screen.getByText('@张三')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument()
  })

  it('should_render_display_name_and_logout_button_when_authenticated', async () => {
    setAuthToken('tok-123')
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(CURRENT_USER)

    renderLayout()

    expect(await screen.findByText('张三')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '登出' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument()
  })

  it('should_call_logout_when_logout_button_clicked', async () => {
    setAuthToken('tok-123')
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(CURRENT_USER)
    vi.mocked(authApi.logout).mockResolvedValue(undefined)

    renderLayout()
    fireEvent.click(await screen.findByRole('button', { name: '登出' }))

    await waitFor(() => expect(authApi.logout).toHaveBeenCalled())
  })
})
