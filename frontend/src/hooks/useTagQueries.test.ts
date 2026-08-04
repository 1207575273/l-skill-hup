import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as tagsApi from '../api/tags'
import type { Tag } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useTagSuggestions, useTags } from './useTagQueries'

vi.mock('../api/tags')

const mockedFetchTags = vi.mocked(tagsApi.fetchTags)
const mockedSuggestTags = vi.mocked(tagsApi.suggestTags)

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

  // 一个字符的联想会把几乎所有词条捞回来,既没帮助又白打一次库。文档 5.7 说的也是"敲第二个字"。
  it('should_not_request_when_suggest_keyword_shorter_than_two_chars', async () => {
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useTagSuggestions('a'), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.fetchStatus).toBe('idle'))
    expect(mockedSuggestTags).not.toHaveBeenCalled()
  })

  it('should_request_with_trimmed_keyword_when_long_enough', async () => {
    mockedSuggestTags.mockResolvedValue([])
    const queryClient = createTestQueryClient()

    renderHook(() => useTagSuggestions('  code  '), { wrapper: createQueryWrapper(queryClient) })

    await waitFor(() => expect(mockedSuggestTags).toHaveBeenCalledWith('code', undefined))
  })

  /** 键里带上 kind:同一个关键词筛 CURATED 与筛 FREE 是两份结果,共用键会互相顶掉。 */
  it('should_cache_separately_when_same_keyword_with_different_kind', async () => {
    mockedSuggestTags.mockResolvedValue([])
    const queryClient = createTestQueryClient()

    renderHook(() => useTagSuggestions('code', 'CURATED'), {
      wrapper: createQueryWrapper(queryClient),
    })
    renderHook(() => useTagSuggestions('code', 'FREE'), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(mockedSuggestTags).toHaveBeenCalledWith('code', 'CURATED'))
    await waitFor(() => expect(mockedSuggestTags).toHaveBeenCalledWith('code', 'FREE'))
  })
})
