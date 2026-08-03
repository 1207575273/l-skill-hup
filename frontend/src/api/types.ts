/**
 * 后端 DTO 的 TS 类型定义 —— 单一事实源。
 *
 * P0 阶段手写,依据 docs/20260802224934_业务建模与接口契约.md(第四章表结构、
 * 第六章接口契约、第七章前端展示约定)。**契约变了先改这里**,让 tsc 把所有受影响
 * 的地方报出来,不要散落到各个调用点各写一份。
 *
 * 将来由 `openapi-typescript` 从后端导出的 `openapi.json` 生成 `schema.d.ts` 替换
 * 本文件(依据契约文档 8.3)。生成的类型是参考答案不是圣旨:不要求一次性全量替换,
 * 哪个接口的 DTO 复杂就换成生成的,简单的继续手写。集中放在这一个文件,就是为了
 * 将来能一次性替换 —— 散落各处就换不掉了。
 *
 * 字段一律 camelCase:后端错误响应体示例(契约 6.1)与 precheck 响应示例(6.2)
 * 都是 camelCase,本文件的其余 DTO 按同一约定推断,并非全部有后端 JSON 例证,
 * 具体差异见本次任务报告。
 */

// ==================== 通用 ====================

/**
 * 分页响应体(契约 6.1)。`page` 从 1 开始,`size` 默认 20、上限 100。
 */
export interface PageResponse<T> {
  readonly items: T[]
  readonly total: number
  readonly page: number
  readonly size: number
}

export interface PageQuery {
  readonly page?: number
  readonly size?: number
}

/**
 * 错误响应体(契约 6.1)。`traceId` 必填 —— 这是排障第一入口,不许被吞掉。
 * `code` 是稳定的机器可读枚举,前端据此分支;`message` 面向人,不能拿它做逻辑判断。
 */
export interface ApiErrorBody {
  readonly code: string
  readonly message: string
  readonly traceId: string
  readonly details?: unknown
}

// ==================== 枚举(与数据库枚举列一一对应) ====================

/** skill 来源,单选互斥(建模 3.5)。 */
export type SkillSource = 'INTERNAL' | 'COMMUNITY'

/** skill 上下架状态。UNLISTED 不物理删除(建模 4.8)。 */
export type SkillStatus = 'PUBLISHED' | 'UNLISTED'

/** 认证三态,挂在版本上不挂在 skill 上(建模 3.6)。 */
export type CertificationStatus = 'NONE' | 'CERTIFIED' | 'REVOKED'

/** 标签类型:CURATED 导航分类(ADMIN 维护),FREE 自由标签(建模 3.5)。 */
export type TagKind = 'CURATED' | 'FREE'

export type UserRole = 'USER' | 'ADMIN'

export type AuthProviderCode = 'LOCAL' | 'DINGTALK'

export type ReactionType = 'LIKE' | 'DISLIKE'

/**
 * 已实测的目标客户端(建模 5.9)。这是代码里的枚举,不是自由字符串 ——
 * 未在 `GET /clients` 返回的组合,服务端会拒绝生成安装单。
 */
export type ClientCode = 'claude' | 'codex' | 'workbuddy'

/** 安装级别:global 落 `~/.<client>/skills/`,project 落 `<当前目录>/.<client>/skills/`。 */
export type ScopeCode = 'global' | 'project'

// ==================== 归属人 ====================

/**
 * 卡片底部展示为 `@展示名(工号)`(展示约定 7.1)。工号为空时只显示展示名。
 */
export interface UserSummary {
  readonly id: number
  readonly displayName: string
  readonly employeeNo: string | null
}

export interface CurrentUser {
  readonly id: number
  readonly displayName: string
  readonly employeeNo: string | null
  readonly role: UserRole
  readonly identities: ReadonlyArray<{
    readonly provider: AuthProviderCode
    /** LOCAL 时是登录名,DINGTALK 时是展示提示,不是 unionId(建模 4.4)。 */
    readonly externalId: string
  }>
}

// ==================== 标签 ====================

export interface Tag {
  readonly id: number
  readonly name: string
  readonly kind: TagKind
  readonly description: string | null
  readonly sortOrder: number
  /** 该标签下的 skill 数,GET /tags 附带,实时 GROUP BY 算出(建模表结构第 8 条)。 */
  readonly skillCount: number
}

// ==================== skill ====================

/**
 * 列表卡片所需字段,对应展示约定 7.1 的七个元素。
 * `tags` 已包含 CURATED 与 FREE,前端按 `kind` 分组渲染(展示约定 7.2)。
 */
export interface SkillSummary {
  readonly id: number
  readonly name: string
  readonly displayName: string
  readonly description: string
  readonly source: SkillSource
  readonly status: SkillStatus
  /** 卡片摘要,服务端已纯文本化并截到 200 字符;前端再截到 100 字符并自行加省略号(建模 5.6)。 */
  readonly cardExcerpt: string
  readonly downloadCount: number
  readonly likeCount: number
  readonly dislikeCount: number
  readonly favoriteCount: number
  readonly commentCount: number
  readonly owner: UserSummary
  readonly tags: Tag[]
  /** 最新版本号与其认证状态,来自 skill join latest_version(建模 4.8)。 */
  readonly latestVersion: string
  readonly certification: CertificationStatus
  /** "多久没更新"是选 skill 的关键判据,列表必须带(展示约定 7.1)。 */
  readonly updatedAt: string
}

export interface SkillVersionSummary {
  readonly id: number
  readonly version: string
  readonly certification: CertificationStatus
  readonly certifyNote: string | null
  readonly createdAt: string
}

/**
 * 详情页在列表字段基础上,补正文与当前用户的互动状态。
 * `myReaction` / `myFavorited` 在 P2(点赞/收藏上线)前恒为 null / false。
 */
export interface SkillDetail extends SkillSummary {
  readonly skillMdContent: string
  readonly readmeContent: string | null
  readonly allowedTools: string[]
  readonly fileCount: number
  readonly sizeBytes: number
  readonly createdAt: string
  readonly myReaction: ReactionType | null
  readonly myFavorited: boolean
}

export interface SkillListFilter extends PageQuery {
  readonly source?: SkillSource
  /** 多值 AND,后端用 GROUP BY ... HAVING count(*) = n 实现(接口契约 6.3)。 */
  readonly tagIds?: number[]
  readonly certifiedOnly?: boolean
  readonly keyword?: string
  readonly ownerUserId?: number
  readonly sort?: 'downloads' | 'likes' | 'latest'
}

// ==================== 上传 precheck ====================

/**
 * `existing` 字段,重名时给出的决策依据(接口契约 6.2 precheck 响应示例)。
 */
export interface SkillPrecheckExisting {
  readonly skillId: number
  readonly name: string
  readonly ownerDisplayName: string
  readonly ownerEmployeeNo: string | null
  readonly latestVersion: string
  /**
   * 服务端算好的权限位,前端只用它决定要不要显示"作为新版本"按钮,
   * **真正的拦截在服务端**,不能自己判断权限(接口契约 6.2)。
   */
  readonly canPublishNewVersion: boolean
}

/**
 * `POST /skills/precheck` 响应体,字段与结构严格对齐接口契约 6.2 的 JSON 示例。
 * `parsed` 里没有 `version` —— 版本号由上传表单提交,不从包里解析(建模 5.4)。
 */
export interface SkillPrecheckResult {
  readonly valid: boolean
  readonly parsed: {
    readonly name: string
    readonly description: string
    readonly allowedTools: string[]
  } | null
  readonly contentDigest: string
  readonly fileCount: number
  readonly unpackedBytes: number
  readonly hasReadme: boolean
  /** 非空表示 contentDigest 命中了已有版本,即"完全相同的包已存在"。 */
  readonly identical: { readonly skillId: number; readonly version: string } | null
  readonly duplicatedName: boolean
  readonly existing: SkillPrecheckExisting | null
  readonly suggestedName: string | null
  readonly violations: string[]
}

// ==================== 安装 ====================

/** `GET /clients` 返回的适配表条目,只含已实测组合(建模 5.9)。 */
export interface ClientTarget {
  readonly client: ClientCode
  readonly scope: ScopeCode
}

export interface InstallBundleItem {
  readonly skillId: number
  readonly versionId: number
}

export interface InstallBundleRequest {
  readonly items: InstallBundleItem[]
  readonly client: ClientCode
  readonly scope: ScopeCode
}

/**
 * `POST /bundles` 只返回 token 与过期时间。真正的安装脚本是独立的 GET 端点
 * `/i/{token}.ps1` / `/i/{token}.sh`(不在 `/api/v1` 下,建模 5.9),前端拿 token
 * 拼 URL,不在这里预置脚本文本。
 */
export interface InstallBundleResponse {
  readonly token: string
  readonly expiresAt: string
}

// ==================== 认证 ====================

export interface LoginRequest {
  readonly login: string
  readonly password: string
}

export interface LoginResponse {
  readonly token: string
  readonly expiresAt: string
  readonly user: CurrentUser
}

/** `GET /auth/providers`,让服务端告诉前端有哪些登录方式按钮可用(建模 6.2)。 */
export interface AuthProvidersResponse {
  readonly providers: AuthProviderCode[]
}
