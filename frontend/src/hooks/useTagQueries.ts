import { useQuery } from '@tanstack/react-query'
import { tagKeys } from '../api/queryKeys'
import { fetchTags } from '../api/tags'
import type { TagKind } from '../api/types'

/** 标签集合几十条量级、变化极少(`queryKeys.ts` 里 `clientKeys` 同款约定),给一个较长的 staleTime。 */
const TAG_STALE_TIME_MS = 10 * 60_000

/** `GET /tags`,`kind` 缺省时返回 CURATED + FREE 全量。 */
export function useTags(kind?: TagKind) {
  return useQuery({
    queryKey: tagKeys.list(kind),
    queryFn: () => fetchTags(kind),
    staleTime: TAG_STALE_TIME_MS,
  })
}
