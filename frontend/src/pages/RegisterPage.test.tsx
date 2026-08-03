import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import type { AuthProviders, LoginResult } from '../api/types'
import RegisterPage from './RegisterPage'

vi.mock('../api/auth')

const mockedFetchAuthProviders = vi.mocked(authApi.fetchAuthProviders)
const mockedRegister = vi.mocked(authApi.register)

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function providers(localRegisterEnabled: boolean): AuthProviders {
  return { providers: ['LOCAL'], localRegisterEnabled }
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
  })

  it('should_explain_when_self_registration_is_disabled', async () => {
    // 开关归服务端。关闭时不能渲染一个提交必然失败的表单 ——
    // 也不能直接跳走,那会让人以为是自己点错了链接,而真实情况是功能被关了。
    mockedFetchAuthProviders.mockResolvedValue(providers(false))

    renderPage()

    expect(await screen.findByText('注册未开放')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '注册' })).not.toBeInTheDocument()
  })

  it('should_render_form_when_self_registration_is_enabled', async () => {
    mockedFetchAuthProviders.mockResolvedValue(providers(true))

    renderPage()

    expect(await screen.findByLabelText('显示名')).toBeInTheDocument()
    expect(screen.getByLabelText('登录名')).toBeInTheDocument()
    expect(screen.getByLabelText('密码')).toBeInTheDocument()
    expect(screen.getByLabelText('确认密码')).toBeInTheDocument()
  })

  it('should_keep_submit_disabled_when_passwords_do_not_match', async () => {
    mockedFetchAuthProviders.mockResolvedValue(providers(true))

    renderPage()
    fireEvent.change(await screen.findByLabelText('显示名'), { target: { value: '测试用户' } })
    fireEvent.change(screen.getByLabelText('登录名'), { target: { value: 'tester' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'secret123' } })
    fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'secret124' } })

    expect(screen.getByRole('button', { name: '注册' })).toBeDisabled()
    expect(screen.getByText('两次输入的密码不一致')).toBeInTheDocument()
  })

  it('should_not_send_confirm_password_to_server_when_submitting', async () => {
    // 确认密码是防手滑的输入辅助,不是业务字段。发过去只会让契约多一个
    // 服务端必须重复校验、却没有业务含义的字段。
    mockedFetchAuthProviders.mockResolvedValue(providers(true))
    mockedRegister.mockResolvedValue({
      token: 'tok',
      expiresAt: '2026-09-01T00:00:00Z',
      userId: 9,
      displayName: '测试用户',
      role: 'USER',
    } as LoginResult)

    renderPage()
    fireEvent.change(await screen.findByLabelText('显示名'), { target: { value: '测试用户' } })
    fireEvent.change(screen.getByLabelText('登录名'), { target: { value: 'tester' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'secret123' } })
    fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: '注册' }))

    await waitFor(() => {
      expect(mockedRegister).toHaveBeenCalledWith({
        displayName: '测试用户',
        login: 'tester',
        password: 'secret123',
      })
    })
  })
})
