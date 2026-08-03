import { useQuery } from '@tanstack/react-query'
import { skillKeys } from '../api/queryKeys'
import {
  fetchSkillDetail,
  fetchSkillFileContent,
  fetchSkillList,
  fetchSkillVersions,
} from '../api/skills'
import type { SkillListFilter } from '../api/types'

/** 文件内容在版本发布后就不再变化,给一个长 staleTime,来回点同一个文件不重复请求。 */
const SKILL_FILE_CONTENT_STALE_TIME_MS = 30 * 60_000

/**
 * skill 读侧查询钩子,对应 `api/skills.ts` 的 GET 资源函数。服务端数据一律走 TanStack Query
 * (frontend/CLAUDE.md 第三节),页面从这里取数据,不直接调 `api/`。
 */

interface QueryToggle {
  readonly enabled?: boolean
}

/** `GET /skills`,列表页筛选查询;`filter` 变化即视为不同的查询键,自动重新拉取。 */
export function useSkillList(filter: SkillListFilter = {}) {
  return useQuery({
    queryKey: skillKeys.list(filter),
    queryFn: ({ signal }) => fetchSkillList(filter, { signal }),
  })
}

/** `GET /skills/{id}`。`options.enabled` 供"id 还没解析出来"之类场景显式关闭查询。 */
export function useSkillDetail(skillId: number, options?: QueryToggle) {
  return useQuery({
    queryKey: skillKeys.detail(skillId),
    queryFn: ({ signal }) => fetchSkillDetail(skillId, { signal }),
    enabled: options?.enabled ?? true,
  })
}

/** `GET /skills/{id}/versions`,不含正文。 */
export function useSkillVersions(skillId: number, options?: QueryToggle) {
  return useQuery({
    queryKey: skillKeys.versions(skillId),
    queryFn: ({ signal }) => fetchSkillVersions(skillId, { signal }),
    enabled: options?.enabled ?? true,
  })
}

/**
 * `GET /skills/{id}/versions/{versionId}/file?path=...`,详情页文件预览。
 *
 * 没选中文件时(`path` 为 null)不发请求 —— 详情页一进来就是未选中状态,恒开会白打一次 404。
 * 内容按 (versionId, path) 缓存,来回点同一个文件不重复请求;文件内容在版本发布后不会再变,
 * 给一个较长的 staleTime。
 */
export function useSkillFileContent(
  skillId: number,
  versionId: number | null,
  path: string | null,
) {
  return useQuery({
    queryKey: skillKeys.file(skillId, versionId ?? 0, path ?? ''),
    queryFn: ({ signal }) =>
      fetchSkillFileContent(skillId, versionId as number, path as string, { signal }),
    enabled: versionId !== null && path !== null,
    staleTime: SKILL_FILE_CONTENT_STALE_TIME_MS,
  })
}
