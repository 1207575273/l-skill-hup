import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { userKeys } from '../api/queryKeys'
import type { UserSummary } from '../api/types'
import * as usersApi from '../api/users'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useCreateUser, useDisableUser, useEnableUser } from './useUserMutations'

vi.mock('../api/users')

const mockedCreateUser = vi.mocked(usersApi.createUser)
const mockedDisableUser = vi.mocked(usersApi.disableUser)
const mockedEnableUser = vi.mocked(usersApi.enableUser)

const NEW_USER = { id: 5 } as UserSummary

describe('useUserMutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_invalidate_user_lists_when_createUser_succeeds', async () => {
    mockedCreateUser.mockResolvedValue(NEW_USER)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateUser(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({
        displayName: '王五',
        login: 'wangwu',
        password: 'secret123',
      })
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: userKeys.lists() })
  })

  it('should_invalidate_user_lists_when_disableUser_succeeds', async () => {
    mockedDisableUser.mockResolvedValue(undefined)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useDisableUser(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync(5)
    })

    expect(mockedDisableUser).toHaveBeenCalledWith(5)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: userKeys.lists() })
  })

  it('should_invalidate_user_lists_when_enableUser_succeeds', async () => {
    mockedEnableUser.mockResolvedValue(undefined)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useEnableUser(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync(5)
    })

    expect(mockedEnableUser).toHaveBeenCalledWith(5)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: userKeys.lists() })
  })
})
