import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { setAuthToken } from '../api/client'
import type { AuthProviders, CurrentUser, IdentitySummary } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useAuthProviders, useCurrentUser, useIdentities } from './useAuthQueries'

vi.mock('../api/auth')

const mockedFetchCurrentUser = vi.mocked(authApi.fetchCurrentUser)
const mockedFetchAuthProviders = vi.mocked(authApi.fetchAuthProviders)
const mockedFetchIdentities = vi.mocked(authApi.fetchIdentities)

describe('useAuthQueries', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAuthToken(null)
  })

  afterEach(() => {
    window.localStorage.clear()
  })

  it('should_not_fetch_current_user_when_no_token_stored', () => {
    const queryClient = createTestQueryClient()

    renderHook(() => useCurrentUser(), { wrapper: createQueryWrapper(queryClient) })

    expect(mockedFetchCurrentUser).not.toHaveBeenCalled()
  })

  it('should_fetch_current_user_when_token_stored', async () => {
    setAuthToken('tok')
    const user = { id: 1 } as CurrentUser
    mockedFetchCurrentUser.mockResolvedValue(user)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useCurrentUser(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(user)
  })

  it('should_fetch_providers_regardless_of_token_when_useAuthProviders_used', async () => {
    const providers: AuthProviders = { providers: ['LOCAL'], localRegisterEnabled: false }
    mockedFetchAuthProviders.mockResolvedValue(providers)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useAuthProviders(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(providers)
  })

  it('should_not_fetch_identities_when_no_token_stored', () => {
    const queryClient = createTestQueryClient()

    renderHook(() => useIdentities(), { wrapper: createQueryWrapper(queryClient) })

    expect(mockedFetchIdentities).not.toHaveBeenCalled()
  })

  it('should_fetch_identities_when_token_stored', async () => {
    setAuthToken('tok')
    const identities: IdentitySummary[] = []
    mockedFetchIdentities.mockResolvedValue(identities)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useIdentities(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(identities)
  })
})
