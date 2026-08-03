import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as installApi from '../api/install'
import type { InstallBundleResult } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useCreateInstallBundle } from './useInstallMutations'

vi.mock('../api/install')

const mockedCreateInstallBundle = vi.mocked(installApi.createInstallBundle)

const BUNDLE_RESULT: InstallBundleResult = {
  token: 'tok',
  expiresAt: '2026-09-01T00:00:00Z',
  client: 'claude',
  scope: 'global',
  powerShellCommand: 'irm https://x/i/tok.ps1 | iex',
  bashCommand: 'curl -fsSL https://x/i/tok.sh | sh',
}

describe('useInstallMutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_forward_items_client_and_scope_to_resource_function_when_createInstallBundle_called', async () => {
    mockedCreateInstallBundle.mockResolvedValue(BUNDLE_RESULT)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateInstallBundle(), {
      wrapper: createQueryWrapper(queryClient),
    })

    let mutationResult: InstallBundleResult | undefined
    await act(async () => {
      mutationResult = await result.current.mutateAsync({
        items: [{ skillId: 1, versionId: 10 }],
        client: 'claude',
        scope: 'global',
      })
    })

    expect(mockedCreateInstallBundle).toHaveBeenCalledWith(
      [{ skillId: 1, versionId: 10 }],
      'claude',
      'global',
    )
    expect(mutationResult).toEqual(BUNDLE_RESULT)
    // 安装单不对应任何被查询的资源,不该触发任何失效。
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
