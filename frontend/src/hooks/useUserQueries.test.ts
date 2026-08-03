import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResponse, UserSummary } from '../api/types'
import * as usersApi from '../api/users'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useUsers } from './useUserQueries'

vi.mock('../api/users')

const mockedFetchUsers = vi.mocked(usersApi.fetchUsers)

describe('useUserQueries', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_return_user_list_when_query_resolves', async () => {
    const page: PageResponse<UserSummary> = { items: [], total: 0, page: 1, size: 20 }
    mockedFetchUsers.mockResolvedValue(page)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useUsers({ provider: 'LOCAL' }), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(page)
    expect(mockedFetchUsers).toHaveBeenCalledWith({ provider: 'LOCAL' })
  })
})
