import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as skillsApi from '../api/skills'
import type { PageResponse, SkillDetail, SkillSummary, SkillVersionSummary } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import { useSkillDetail, useSkillList, useSkillVersions } from './useSkillQueries'

vi.mock('../api/skills')

const mockedFetchSkillList = vi.mocked(skillsApi.fetchSkillList)
const mockedFetchSkillDetail = vi.mocked(skillsApi.fetchSkillDetail)
const mockedFetchSkillVersions = vi.mocked(skillsApi.fetchSkillVersions)

describe('useSkillQueries', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_return_skill_list_when_query_resolves', async () => {
    const page: PageResponse<SkillSummary> = { items: [], total: 0, page: 1, size: 20 }
    mockedFetchSkillList.mockResolvedValue(page)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useSkillList({ keyword: 'pdf' }), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(page)
    expect(mockedFetchSkillList).toHaveBeenCalledWith(
      { keyword: 'pdf' },
      expect.objectContaining({ signal: expect.anything() }),
    )
  })

  it('should_return_skill_detail_when_query_resolves', async () => {
    const detail = { id: 1 } as SkillDetail
    mockedFetchSkillDetail.mockResolvedValue(detail)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useSkillDetail(1), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(detail)
  })

  it('should_not_fetch_when_skill_detail_query_disabled', () => {
    const queryClient = createTestQueryClient()

    renderHook(() => useSkillDetail(1, { enabled: false }), {
      wrapper: createQueryWrapper(queryClient),
    })

    expect(mockedFetchSkillDetail).not.toHaveBeenCalled()
  })

  it('should_return_skill_versions_when_query_resolves', async () => {
    const versions: SkillVersionSummary[] = []
    mockedFetchSkillVersions.mockResolvedValue(versions)
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useSkillVersions(1), {
      wrapper: createQueryWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(versions)
  })

  it('should_not_fetch_when_skill_versions_query_disabled', () => {
    const queryClient = createTestQueryClient()

    renderHook(() => useSkillVersions(1, { enabled: false }), {
      wrapper: createQueryWrapper(queryClient),
    })

    expect(mockedFetchSkillVersions).not.toHaveBeenCalled()
  })
})
