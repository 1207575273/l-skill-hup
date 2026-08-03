import { API_BASE_URL, apiGet, apiPost, apiPostFormData } from './client'
import type {
  ManagedSkill,
  PageResponse,
  SkillDetail,
  SkillFileContent,
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

/**
 * `GET /skills/{id}/versions/{versionId}/file?path=...`,匿名可用,详情页文件预览。
 *
 * `path` 走 query 参数而不是拼进路径:文件路径里有 `/`,拼进去会被路由当成多级路径段,
 * 还得两边约定一套转义规则 —— query 参数天然支持任意字符,`apiGet` 会做 URL 编码。
 */
export function fetchSkillFileContent(
  skillId: number,
  versionId: number,
  path: string,
  options?: AbortableOptions,
): Promise<SkillFileContent> {
  return apiGet<SkillFileContent>(`/skills/${skillId}/versions/${versionId}/file`, {
    query: { path },
    signal: options?.signal,
  })
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
 * `GET /skills/manage`(需登录),管理视图列表:**含已下架**。
 *
 * 刻意不接受 `ownerUserId` 参数 —— 作用域由服务端按当前身份决定(自己的 / ADMIN 看全部)。
 * 前端能传的话,那个参数就成了越权入口,而且前端传什么都不影响服务端的判定,徒增误解。
 */
export interface ManagedSkillFilter {
  /** 不传表示两种状态都要 —— 这个页面的存在理由就是"下架之后还能找到它"。 */
  readonly status?: 'PUBLISHED' | 'UNLISTED'
  readonly keyword?: string
  readonly page?: number
  readonly size?: number
}

export function fetchManagedSkills(
  filter: ManagedSkillFilter = {},
  options?: AbortableOptions,
): Promise<PageResponse<ManagedSkill>> {
  return apiGet<PageResponse<ManagedSkill>>('/skills/manage', {
    query: {
      status: filter.status,
      keyword: filter.keyword,
      page: filter.page,
      size: filter.size,
    },
    signal: options?.signal,
  })
}

/**
 * `POST /skills/{id}/delete`(需登录,owner/ADMIN),**物理删除,不可逆**。
 *
 * 返回 204 无响应体 —— 资源已经不存在了,再回一份它的详情是在描述一个不存在的东西。
 */
export function deleteSkill(skillId: number): Promise<void> {
  return apiPost<void>(`/skills/${skillId}/delete`)
}

/**
 * `GET /skills/{id}/versions/{version}/download` 的纯 URL 拼装,**不在这里发请求**——下载走浏览器原生
 * `<a href>` / `window.location.assign` 触发保存,`SkillDownloadController` 直接流式写响应体,
 * fetch 拿到手还要自己重新触发一次保存,没有必要绕这一圈。
 */
export function buildSkillDownloadUrl(skillId: number, version: string): string {
  return `${API_BASE_URL}/skills/${skillId}/versions/${encodeURIComponent(version)}/download`
}
