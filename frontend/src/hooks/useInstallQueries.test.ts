import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as installApi from '../api/install'
import type { ClientTarget } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useInstallClients } from './useInstallQueries'

vi.mock('../api/install')

const mockedFetchInstallClients = vi.mocked(installApi.fetchInstallClients)

describe('useInstallQueries', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_return_client_targets_when_query_resolves', async () => {
    const targets: ClientTarget[] = [
      { client: 'claude', scope: 'global', skillsSubDir: '.claude/skills' },
    ]
    mockedFetchInstallClients.mockResolvedValue(targets)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useInstallClients(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(targets)
  })
})
