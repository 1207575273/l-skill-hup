import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as tagsApi from '../api/tags'
import type { Tag } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useTags } from './useTagQueries'

vi.mock('../api/tags')

const mockedFetchTags = vi.mocked(tagsApi.fetchTags)

describe('useTagQueries', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_return_all_tags_when_kind_omitted', async () => {
    const tags: Tag[] = [
      { id: 1, name: '文档处理', kind: 'CURATED', description: null, sortOrder: 1, skillCount: 3 },
    ]
    mockedFetchTags.mockResolvedValue(tags)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useTags(), { wrapper: createQueryWrapper(queryClient) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(tags)
    expect(mockedFetchTags).toHaveBeenCalledWith(undefined)
  })

  it('should_pass_kind_through_when_useTags_called_with_kind', async () => {
    mockedFetchTags.mockResolvedValue([])
    const queryClient = createTestQueryClient()

    renderHook(() => useTags('FREE'), { wrapper: createQueryWrapper(queryClient) })

    await waitFor(() => expect(mockedFetchTags).toHaveBeenCalledWith('FREE'))
  })
})
