import type { Mock } from 'vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createUser, disableUser, enableUser, fetchUsers } from './users'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('users 资源函数', () => {
  let fetchMock: Mock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('should_request_user_list_with_pagination_when_fetchUsers_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { items: [], total: 0, page: 1, size: 20 }))

    await fetchUsers({ page: 2, size: 50, provider: 'LOCAL' })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const parsed = new URL(url)
    expect(init.method).toBe('GET')
    expect(parsed.pathname).toBe('/skillhub/api/v1/users')
    expect(parsed.searchParams.get('page')).toBe('2')
    expect(parsed.searchParams.get('size')).toBe('50')
    expect(parsed.searchParams.get('provider')).toBe('LOCAL')
  })

  it('should_omit_provider_param_when_fetchUsers_called_without_filter', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { items: [], total: 0, page: 1, size: 20 }))

    await fetchUsers()

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).searchParams.has('provider')).toBe(false)
  })

  it('should_post_new_user_fields_when_createUser_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 5 }))

    await createUser({ displayName: '王五', login: 'wangwu', password: 'secret123', role: 'ADMIN' })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/users')
    expect(JSON.parse(init.body as string)).toEqual({
      displayName: '王五',
      login: 'wangwu',
      password: 'secret123',
      role: 'ADMIN',
    })
  })

  it('should_post_to_disable_endpoint_when_disableUser_called', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await disableUser(5)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/users/5/disable')
    expect(init.method).toBe('POST')
  })

  it('should_post_to_enable_endpoint_when_enableUser_called', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await enableUser(5)

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/users/5/enable')
  })
})
