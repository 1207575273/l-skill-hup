import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { skillKeys, tagKeys } from '../api/queryKeys'
import * as skillsApi from '../api/skills'
import type { SkillDetail, SkillPrecheckResult, SkillVersionSummary } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from './queryTestUtils.test-support'
import {
  useCertifyVersion,
  usePrecheckSkillPackage,
  usePublishSkill,
  usePublishSkillVersion,
  useRelistSkill,
  useUnlistSkill,
  useUpdateSkillMetadata,
} from './useSkillMutations'

vi.mock('../api/skills')

const mockedPrecheck = vi.mocked(skillsApi.precheckSkillPackage)
const mockedPublishSkill = vi.mocked(skillsApi.publishSkill)
const mockedPublishSkillVersion = vi.mocked(skillsApi.publishSkillVersion)
const mockedUpdateSkillMetadata = vi.mocked(skillsApi.updateSkillMetadata)
const mockedUnlistSkill = vi.mocked(skillsApi.unlistSkill)
const mockedRelistSkill = vi.mocked(skillsApi.relistSkill)
const mockedCertifySkillVersion = vi.mocked(skillsApi.certifySkillVersion)

const SKILL_DETAIL = { id: 1 } as SkillDetail

const PRECHECK_RESULT: SkillPrecheckResult = {
  valid: true,
  parsed: null,
  contentDigest: null,
  fileCount: null,
  unpackedBytes: null,
  hasReadme: null,
  identical: null,
  duplicatedName: false,
  existing: null,
  suggestedName: null,
  violations: [],
}

describe('useSkillMutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should_not_invalidate_any_query_when_precheck_succeeds', async () => {
    mockedPrecheck.mockResolvedValue(PRECHECK_RESULT)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => usePrecheckSkillPackage(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync(new Blob(['zip']))
    })

    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('should_cache_detail_and_invalidate_lists_and_tags_when_publishSkill_succeeds', async () => {
    mockedPublishSkill.mockResolvedValue(SKILL_DETAIL)
    const queryClient = createTestQueryClient()
    const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData')
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => usePublishSkill(), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({
        packageFile: new Blob(['zip']),
        version: '1.0.0',
        source: 'INTERNAL',
      })
    })

    expect(setQueryDataSpy).toHaveBeenCalledWith(skillKeys.detail(1), SKILL_DETAIL)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.lists() })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: tagKeys.all })
  })

  it('should_invalidate_versions_and_lists_when_publishSkillVersion_succeeds', async () => {
    mockedPublishSkillVersion.mockResolvedValue(SKILL_DETAIL)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => usePublishSkillVersion(1), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ packageFile: new Blob(['zip']), version: '2.0.0' })
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.versions(1) })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.lists() })
  })

  it('should_invalidate_lists_and_tags_when_updateSkillMetadata_succeeds', async () => {
    mockedUpdateSkillMetadata.mockResolvedValue(SKILL_DETAIL)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUpdateSkillMetadata(1), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({
        displayName: '新展示名',
        source: 'INTERNAL',
        curatedTagIds: [1],
      })
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.lists() })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: tagKeys.all })
  })

  it('should_invalidate_lists_when_unlistSkill_succeeds', async () => {
    mockedUnlistSkill.mockResolvedValue(SKILL_DETAIL)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUnlistSkill(1), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.lists() })
  })

  it('should_invalidate_lists_when_relistSkill_succeeds', async () => {
    mockedRelistSkill.mockResolvedValue(SKILL_DETAIL)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useRelistSkill(1), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.lists() })
  })

  /**
   * 认证挂在版本上,但影响面比"版本列表"大得多:详情页顶部的徽章取自 latestVersion、列表卡片带徽章、
   * certifiedOnly 筛选的结果集会变、管理表格有 certification 列。只失效 versions 是最自然也最错的写法 ——
   * 版本列表当场刷新了,页面顶部的徽章却还挂着旧状态,而这种不一致没有任何报错。
   */
  it('should_invalidate_versions_detail_lists_and_managed_when_certifyVersion_succeeds', async () => {
    mockedCertifySkillVersion.mockResolvedValue({} as SkillVersionSummary)
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCertifyVersion(1), {
      wrapper: createQueryWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({
        version: '1.0.0',
        request: { certification: 'REVOKED', note: '有问题' },
      })
    })

    expect(mockedCertifySkillVersion).toHaveBeenCalledWith(1, '1.0.0', {
      certification: 'REVOKED',
      note: '有问题',
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.versions(1) })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.detail(1) })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.lists() })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: skillKeys.managedAll() })
  })
})
