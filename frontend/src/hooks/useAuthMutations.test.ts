import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { getAuthToken, setAuthToken } from '../api/client'
import { authKeys } from '../api/queryKeys'
import type { LoginResult } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useChangePassword, useLogin, useLogout, useRegister } from './useAuthMutations'

vi.mock('../api/auth')

const mockedLogin = vi.mocked(authApi.login)
const mockedRegister = vi.mocked(authApi.register)
const mockedLogout = vi.mocked(authApi.logout)
const mockedChangePassword = vi.mocked(authApi.changePassword)

const LOGIN_RESULT: LoginResult = {
  token: 'tok-123',
  expiresAt: '2026-09-01T00:00:00Z',
  userId: 1,
  displayName: '张三',
  role: 'USER',
}

describe('useAuthMutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAuthToken(null)
  })

  afterEach(() => {
    window.localStorage.clear()
  })

  it('should_store_token_and_invalidate_me_and_identities_when_login_succeeds', async () => {
    mockedLogin.mockResolvedValue(LOGIN_RESULT)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useLogin(), { wrapper: createQueryWrapper(queryClient) })

    await act(async () => {
      await result.current.mutateAsync({ login: 'zhangsan', password: 'secret123' })
    })

    expect(getAuthToken()).toBe('tok-123')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: authKeys.me })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: authKeys.identities })
  })

  it('should_store_token_and_invalidate_me_and_identities_when_register_succeeds', async () => {
    mockedRegister.mockResolvedValue(LOGIN_RESULT)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useRegister(), { wrapper: createQueryWrapper(queryClient) })

    await act(async () => {
      await result.current.mutateAsync({
        displayName: '张三',
        login: 'zhangsan',
        password: 'secret123',
      })
    })

    expect(getAuthToken()).toBe('tok-123')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: authKeys.me })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: authKeys.identities })
  })

  it('should_clear_token_and_clear_all_cache_when_logout_succeeds', async () => {
    setAuthToken('tok-123')
    mockedLogout.mockResolvedValue(undefined)
    const queryClient = createTestQueryClient()
    const clearSpy = vi.spyOn(queryClient, 'clear')

    const { result } = renderHook(() => useLogout(), { wrapper: createQueryWrapper(queryClient) })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(getAuthToken()).toBeNull()
    expect(clearSpy).toHaveBeenCalled()
  })

  it('should_resolve_without_invalidating_any_query_when_changePassword_succeeds', async () => {
    mockedChangePassword.mockResolvedValue(undefined)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useChangePassword(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ oldPassword: 'old12345', newPassword: 'new12345' })
    })

    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
