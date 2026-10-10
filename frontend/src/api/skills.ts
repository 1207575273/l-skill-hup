import { API_BASE_URL, apiGet, apiPost, apiPostFormData } from './client'
import type {
  CertifyVersionRequest,
  ManagedSkill,
  PageResponse,
  SkillCommentItem,
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

/** `GET /skills/{id}/comments`,匿名可用。顶级分页,回复随顶级返回。 */
export function fetchSkillComments(
  skillId: number,
  params: { readonly page?: number; readonly size?: number } = {},
  options?: AbortableOptions,
): Promise<PageResponse<SkillCommentItem>> {
  return apiGet<PageResponse<SkillCommentItem>>(`/skills/${skillId}/comments`, {
    query: { page: params.page, size: params.size },
    signal: options?.signal,
  })
}

export interface AddCommentPayload {
  /** 留空发顶级评论;非空表示回复,且只能回复顶级评论(服务端会校验)。 */
  readonly parentId?: number
  readonly content: string
}

/** `POST /skills/{id}/comments`(需登录),发表评论或回复。 */
export function addSkillComment(
  skillId: number,
  payload: AddCommentPayload,
): Promise<SkillCommentItem> {
  return apiPost<SkillCommentItem>(`/skills/${skillId}/comments`, payload)
}

/** `POST /comments/{id}/delete`(需登录,作者本人 / ADMIN),软删除。返回 204 无响应体。 */
export function deleteSkillComment(commentId: number): Promise<void> {
  return apiPost<void>(`/comments/${commentId}/delete`)
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
  /**
   * 自称的归属人姓名,**仅匿名上传时可填**。已登录还带它后端报 400(DECLARED_OWNER_NOT_ALLOWED)。
   *
   * 留空即不声明,此时这个 skill 处于无主状态,等人登录后认领。
   */
  readonly declaredOwnerName?: string
  /** 自称的工号,可选。同名同姓在公司规模上是必然事件,只给姓名等于没给线索。 */
  readonly declaredOwnerEmployeeNo?: string
}

/**
 * `POST /skills`,发布新 skill(同时建首个版本)。**匿名可用**。
 *
 * 匿名发布的包归属落到内置 system、处于待认领状态;登录后由 `claimSkill` 认领。
 * 匿名时不能带 freeTagNames(后端报 ANONYMOUS_FREE_TAG_NOT_ALLOWED):自由标签是全局词表,
 * 不交给未经身份认定的请求。
 */
export function publishSkill(payload: PublishSkillPayload): Promise<SkillDetail> {
  const formData = new FormData()
  formData.append('file', payload.packageFile)
  formData.append('version', payload.version)
  if (payload.displayName) {
    formData.append('displayName', payload.displayName)
  }
  formData.append('source', payload.source)
  // 声明归属只在匿名上传时有意义;已登录还带它后端会报 400(归属人就是你本人)。
  // 空字符串不发:后端用 DeclaredOwner.ofNullable 把空白归一成"没有声明",前端不必重复这个判断,
  // 但也不该把一个空串当成"填了"发过去。
  if (payload.declaredOwnerName !== undefined && payload.declaredOwnerName.trim() !== '') {
    formData.append('declaredOwnerName', payload.declaredOwnerName.trim())
  }
  if (
    payload.declaredOwnerEmployeeNo !== undefined &&
    payload.declaredOwnerEmployeeNo.trim() !== ''
  ) {
    formData.append('declaredOwnerEmployeeNo', payload.declaredOwnerEmployeeNo.trim())
  }
  for (const tagId of payload.curatedTagIds ?? []) {
    formData.append('curatedTagIds', String(tagId))
  }
  for (const tagName of payload.freeTagNames ?? []) {
    formData.append('freeTagNames', tagName)
  }
  return apiPostFormData<SkillDetail>('/skills', formData)
}

/**
 * `POST /skills/{id}/claim`(需登录),认领一个无主 skill。
 *
 * 先到先得:并发时后端由 SQL 的比较并交换仲裁,抢输的一方收到 409 SKILL_ALREADY_CLAIMED。
 * 认领成功后归属落到当前登录人,此后发新版本 / 改元数据 / 下架 / 删除才轮得到你。
 */
export function claimSkill(skillId: number): Promise<SkillDetail> {
  return apiPost<SkillDetail>(`/skills/${skillId}/claim`, undefined)
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
/**
 * `POST /skills/{id}/versions/{version}/certify`(ADMIN),认证或撤销某个版本。
 *
 * 路径里的 version 必须 encode:版本号带点,虽然 SemVer 本身不含 URL 保留字符,但这个值是从服务端拿到的
 * 字符串,不该假定它永远干净。
 */
export function certifySkillVersion(
  skillId: number,
  version: string,
  request: CertifyVersionRequest,
): Promise<SkillVersionSummary> {
  return apiPost<SkillVersionSummary>(
    `/skills/${skillId}/versions/${encodeURIComponent(version)}/certify`,
    request,
  )
}

export function buildSkillDownloadUrl(skillId: number, version: string): string {
  return `${API_BASE_URL}/skills/${skillId}/versions/${encodeURIComponent(version)}/download`
}
