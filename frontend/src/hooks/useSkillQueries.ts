import { useQuery } from '@tanstack/react-query'
import { skillKeys } from '../api/queryKeys'
import { fetchSkillDetail, fetchSkillList, fetchSkillVersions } from '../api/skills'
import type { SkillListFilter } from '../api/types'

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
