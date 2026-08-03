import { API_BASE_URL, apiGet, apiPost, apiPostFormData } from './client'
import type {
  PageResponse,
  SkillDetail,
  SkillListFilter,
  SkillPrecheckResult,
  SkillSource,
  SkillSummary,
  SkillVersionSummary,
  UpdateSkillPayload,
} from './types'

/**
 * skill 资源函数,对应 `interfaces/rest/skill` 下的 `SkillController` / `SkillDownloadController`。
 */

interface AbortableOptions {
  readonly signal?: AbortSignal
}

/** `GET /skills`,列表页筛选查询。`filter` 里的可选字段缺省时不拼进 query string。 */
export function fetchSkillList(
  filter: SkillListFilter = {},
  options?: AbortableOptions,
): Promise<PageResponse<SkillSummary>> {
  return apiGet<PageResponse<SkillSummary>>('/skills', {
    query: {
      source: filter.source,
      tagIds: filter.tagIds,
      certifiedOnly: filter.certifiedOnly,
      keyword: filter.keyword,
      ownerUserId: filter.ownerUserId,
      sort: filter.sort,
      page: filter.page,
      size: filter.size,
    },
    signal: options?.signal,
  })
}

/** `GET /skills/{id}`,匿名可用。 */
export function fetchSkillDetail(
  skillId: number,
  options?: AbortableOptions,
): Promise<SkillDetail> {
  return apiGet<SkillDetail>(`/skills/${skillId}`, { signal: options?.signal })
}

/** `GET /skills/{id}/versions`,匿名可用,不含正文。 */
export function fetchSkillVersions(
  skillId: number,
  options?: AbortableOptions,
): Promise<SkillVersionSummary[]> {
  return apiGet<SkillVersionSummary[]>(`/skills/${skillId}/versions`, { signal: options?.signal })
}

/** `POST /skills/precheck`(需登录),上传前即时反馈,不落库。 */
export function precheckSkillPackage(packageFile: Blob): Promise<SkillPrecheckResult> {
  const formData = new FormData()
  formData.append('file', packageFile)
  return apiPostFormData<SkillPrecheckResult>('/skills/precheck', formData)
}

export interface PublishSkillPayload {
  readonly packageFile: Blob
  readonly version: string
  /** 留空时后端回退成解析出的 name(`NewSkillCommand` 类注释)。 */
  readonly displayName?: string
  readonly source: SkillSource
  readonly curatedTagIds?: number[]
  readonly freeTagNames?: string[]
}

/** `POST /skills`(需登录),发布新 skill(同时建首个版本)。 */
export function publishSkill(payload: PublishSkillPayload): Promise<SkillDetail> {
  const formData = new FormData()
  formData.append('file', payload.packageFile)
  formData.append('version', payload.version)
  if (payload.displayName) {
    formData.append('displayName', payload.displayName)
  }
  formData.append('source', payload.source)
  for (const tagId of payload.curatedTagIds ?? []) {
    formData.append('curatedTagIds', String(tagId))
  }
  for (const tagName of payload.freeTagNames ?? []) {
    formData.append('freeTagNames', tagName)
  }
  return apiPostFormData<SkillDetail>('/skills', formData)
}

export interface PublishSkillVersionPayload {
  readonly packageFile: Blob
  readonly version: string
}

/** `POST /skills/{id}/versions`(需登录,owner/ADMIN),给既有 skill 发新版本。 */
export function publishSkillVersion(
  skillId: number,
  payload: PublishSkillVersionPayload,
): Promise<SkillDetail> {
  const formData = new FormData()
  formData.append('file', payload.packageFile)
  formData.append('version', payload.version)
  return apiPostFormData<SkillDetail>(`/skills/${skillId}/versions`, formData)
}

/** `POST /skills/{id}/update`(需登录,owner/ADMIN),改展示名/来源/标签;`name` 不可改故不在入参里。 */
export function updateSkillMetadata(
  skillId: number,
  payload: UpdateSkillPayload,
): Promise<SkillDetail> {
  return apiPost<SkillDetail>(`/skills/${skillId}/update`, payload)
}

/** `POST /skills/{id}/unlist`(需登录,owner/ADMIN),下架。 */
export function unlistSkill(skillId: number): Promise<SkillDetail> {
  return apiPost<SkillDetail>(`/skills/${skillId}/unlist`)
}

/** `POST /skills/{id}/relist`(需登录,owner/ADMIN),恢复上架。 */
export function relistSkill(skillId: number): Promise<SkillDetail> {
  return apiPost<SkillDetail>(`/skills/${skillId}/relist`)
}

/**
 * `GET /skills/{id}/versions/{version}/download` 的纯 URL 拼装,**不在这里发请求**——下载走浏览器原生
 * `<a href>` / `window.location.assign` 触发保存,`SkillDownloadController` 直接流式写响应体,
 * fetch 拿到手还要自己重新触发一次保存,没有必要绕这一圈。
 */
export function buildSkillDownloadUrl(skillId: number, version: string): string {
  return `${API_BASE_URL}/skills/${skillId}/versions/${encodeURIComponent(version)}/download`
}
