import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { skillKeys, tagKeys } from '../api/queryKeys'
import * as tagsApi from '../api/tags'
import type { Tag } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useCreateTag, useDeleteTag, useUpdateTag } from './useTagMutations'

vi.mock('../api/tags')

const mockedCreateTag = vi.mocked(tagsApi.createTag)
const mockedUpdateTag = vi.mocked(tagsApi.updateTag)
const mockedDeleteTag = vi.mocked(tagsApi.deleteTag)

const SAMPLE_TAG: Tag = {
  id: 11,
  name: '性能优化',
  kind: 'CURATED',
  description: '压测与调优',
  sortOrder: 95,
  skillCount: 0,
}

describe('useTagMutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_invalidate_tags_when_create_succeeds', async () => {
    mockedCreateTag.mockResolvedValue(SAMPLE_TAG)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateTag(), {
      wrapper: createQueryWrapper(queryClient),
    })
    result.current.mutate({ name: '性能优化' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: tagKeys.all })
  })

  /**
   * 改名要连 skill 域一起失效:标签不只出现在标签列表里,skill 卡片与详情页都嵌着标签行。
   * 只失效 tagKeys 的话,列表页会一直挂着旧名字,而这种不一致没有任何报错。
   */
  it('should_invalidate_skills_too_when_update_succeeds', async () => {
    mockedUpdateTag.mockResolvedValue(SAMPLE_TAG)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUpdateTag(), {
      wrapper: createQueryWrapper(queryClient),
    })
    result.current.mutate({ id: 11, request: { name: '性能调优' } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockedUpdateTag).toHaveBeenCalledWith(11, { name: '性能调优' })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: tagKeys.all })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.all })
  })

  it('should_invalidate_skills_too_when_delete_succeeds', async () => {
    mockedDeleteTag.mockResolvedValue(undefined)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useDeleteTag(), {
      wrapper: createQueryWrapper(queryClient),
    })
    result.current.mutate({ id: 11, request: { migrateToTagId: 2 } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockedDeleteTag).toHaveBeenCalledWith(11, { migrateToTagId: 2 })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: tagKeys.all })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.all })
  })

  it('should_pass_undefined_request_when_deleting_without_migration', async () => {
    mockedDeleteTag.mockResolvedValue(undefined)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useDeleteTag(), {
      wrapper: createQueryWrapper(queryClient),
    })
    result.current.mutate({ id: 11 })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockedDeleteTag).toHaveBeenCalledWith(11, undefined)
  })
})
