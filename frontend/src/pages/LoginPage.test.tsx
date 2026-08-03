import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { ApiError } from '../api/client'
import type { AuthProviders, LoginResult } from '../api/types'
import LoginPage from './LoginPage'

vi.mock('../api/auth')

const mockedLogin = vi.mocked(authApi.login)
const mockedFetchAuthProviders = vi.mocked(authApi.fetchAuthProviders)

const LOCAL_ONLY_PROVIDERS: AuthProviders = { providers: ['LOCAL'], localRegisterEnabled: false }
const LOGIN_RESULT: LoginResult = {
  token: 'tok-123',
  expiresAt: '2026-09-01T00:00:00Z',
  userId: 1,
  displayName: '张三',
  role: 'USER',
}

function renderPage(providers: AuthProviders = LOCAL_ONLY_PROVIDERS) {
  mockedFetchAuthProviders.mockResolvedValue(providers)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>HOME_PAGE_MARKER</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function fillAndSubmit(loginValue: string, passwordValue: string) {
  fireEvent.change(screen.getByLabelText('登录名'), { target: { value: loginValue } })
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: passwordValue } })
  fireEvent.click(screen.getByRole('button', { name: '登录' }))
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    window.localStorage.clear()
  })

  it('should_not_show_register_entry_when_localRegisterEnabled_is_false', async () => {
    renderPage({ providers: ['LOCAL'], localRegisterEnabled: false })

    await waitFor(() => expect(mockedFetchAuthProviders).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: '注册' })).not.toBeInTheDocument()
  })

  it('should_show_register_entry_when_localRegisterEnabled_is_true', async () => {
    renderPage({ providers: ['LOCAL'], localRegisterEnabled: true })

    expect(await screen.findByRole('link', { name: '注册' })).toBeInTheDocument()
  })

  it('should_not_show_dingtalk_entry_when_providers_lack_dingtalk', async () => {
    renderPage({ providers: ['LOCAL'], localRegisterEnabled: false })

    await waitFor(() => expect(mockedFetchAuthProviders).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: /钉钉/ })).not.toBeInTheDocument()
  })

  it('should_show_dingtalk_entry_when_providers_include_dingtalk', async () => {
    renderPage({ providers: ['LOCAL', 'DINGTALK'], localRegisterEnabled: false })

    expect(await screen.findByRole('link', { name: /钉钉/ })).toBeInTheDocument()
  })

  it('should_show_trace_id_when_login_fails', async () => {
    mockedLogin.mockRejectedValue(
      new ApiError({
        code: 'LOGIN_FAILED',
        message: '登录名或密码错误',
        traceId: 'trace-abc123',
      }),
    )
    renderPage()

    fillAndSubmit('zhangsan', 'wrong-password')

    expect(await screen.findByText('trace-abc123')).toBeInTheDocument()
    expect(screen.getByText('登录名或密码错误')).toBeInTheDocument()
  })

  it('should_show_distinct_lockout_message_when_account_locked', async () => {
    // 账号锁定必须让用户看出"是锁定不是密码错",不能复用登录失败的通用文案。
    mockedLogin.mockRejectedValue(
      new ApiError({
        code: 'ACCOUNT_LOCKED',
        message: '登录失败次数过多,该账号已被临时锁定,请于 2026-08-03T12:00:00Z 之后重试',
        traceId: 'trace-locked',
      }),
    )
    renderPage()

    fillAndSubmit('zhangsan', 'wrong-password')

    expect(await screen.findByText(/该账号已被临时锁定/)).toBeInTheDocument()
    expect(screen.queryByText('登录名或密码错误')).not.toBeInTheDocument()
  })

  it('should_navigate_home_when_login_succeeds', async () => {
    mockedLogin.mockResolvedValue(LOGIN_RESULT)
    renderPage()

    fillAndSubmit('zhangsan', 'secret123')

    expect(await screen.findByText('HOME_PAGE_MARKER')).toBeInTheDocument()
  })
})
