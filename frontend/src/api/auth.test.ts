import type { Mock } from 'vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  changePassword,
  fetchAuthProviders,
  fetchCurrentUser,
  fetchIdentities,
  login,
  logout,
  register,
} from './auth'
import { setAuthToken } from './client'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('auth 资源函数', () => {
  let fetchMock: Mock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    setAuthToken(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('should_post_credentials_when_login_called', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, {
        token: 'tok',
        expiresAt: '2026-09-01T00:00:00Z',
        userId: 1,
        displayName: '张三',
        role: 'USER',
      }),
    )

    const result = await login({ login: 'zhangsan', password: 'secret123' })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/auth/login')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ login: 'zhangsan', password: 'secret123' })
    expect(result.userId).toBe(1)
    // 资源函数不该有 setAuthToken 副作用,登录后不应自动带上 Authorization 头。
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined()
  })

  it('should_post_registration_fields_when_register_called', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, {
        token: 'tok',
        expiresAt: '2026-09-01T00:00:00Z',
        userId: 2,
        displayName: '李四',
        role: 'USER',
      }),
    )

    await register({ displayName: '李四', login: 'lisi', password: 'secret123' })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/auth/register')
    expect(JSON.parse(init.body as string)).toEqual({
      displayName: '李四',
      login: 'lisi',
      password: 'secret123',
    })
  })

  it('should_post_to_logout_endpoint_with_bearer_token_when_logout_called', async () => {
    setAuthToken('tok')
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await logout()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/auth/logout')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tok')
  })

  it('should_request_current_user_when_fetchCurrentUser_called', async () => {
    setAuthToken('tok')
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 1, displayName: '张三' }))

    await fetchCurrentUser()

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/auth/me')
  })

  it('should_request_providers_endpoint_when_fetchAuthProviders_called', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, { providers: ['LOCAL'], localRegisterEnabled: false }),
    )

    const result = await fetchAuthProviders()

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/auth/providers')
    expect(result.localRegisterEnabled).toBe(false)
  })

  it('should_request_identities_endpoint_when_fetchIdentities_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []))

    await fetchIdentities()

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/identities')
  })

  it('should_post_old_and_new_password_when_changePassword_called', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await changePassword({ oldPassword: 'old12345', newPassword: 'new12345' })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/identities/change-password')
    expect(JSON.parse(init.body as string)).toEqual({
      oldPassword: 'old12345',
      newPassword: 'new12345',
    })
  })
})
