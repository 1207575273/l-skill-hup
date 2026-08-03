import { apiGet } from './client'
import type { Tag, TagKind } from './types'

/** tag 资源函数,对应 `interfaces/rest/tag/TagController.java`。只读,匿名可用,不分页。 */

/** `GET /tags`,`kind` 缺省时返回 CURATED + FREE 全量。 */
export function fetchTags(kind?: TagKind): Promise<Tag[]> {
  return apiGet<Tag[]>('/tags', { query: { kind } })
}
