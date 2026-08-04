import { apiGet, apiPost } from './client'
import type { CreateTagRequest, DeleteTagRequest, Tag, TagKind, UpdateTagRequest } from './types'

/** tag 资源函数,对应 `interfaces/rest/tag/TagController.java`。读侧匿名可用,治理侧 ADMIN 专属。 */

/** `GET /tags`,`kind` 缺省时返回 CURATED + FREE 全量。 */
export function fetchTags(kind?: TagKind): Promise<Tag[]> {
  return apiGet<Tag[]>('/tags', { query: { kind } })
}

/**
 * `GET /tags/suggest`,输入联想。空关键词服务端直接返回空数组,这里不额外拦 ——
 * 拦不拦都要处理空结果,少一处分支。
 */
export function suggestTags(keyword: string, kind?: TagKind, limit?: number): Promise<Tag[]> {
  return apiGet<Tag[]>('/tags/suggest', { query: { keyword, kind, limit } })
}

/** `POST /tags`,新建 CURATED 词条(ADMIN)。 */
export function createTag(request: CreateTagRequest): Promise<Tag> {
  return apiPost<Tag>('/tags', request)
}

/** `POST /tags/{id}/update`,改名 / 描述 / 排序 / kind 转换(ADMIN)。 */
export function updateTag(id: number, request: UpdateTagRequest): Promise<Tag> {
  return apiPost<Tag>(`/tags/${id}/update`, request)
}

/** `POST /tags/{id}/delete`,返回 204 无响应体(ADMIN)。 */
export function deleteTag(id: number, request?: DeleteTagRequest): Promise<void> {
  return apiPost<void>(`/tags/${id}/delete`, request ?? {})
}
