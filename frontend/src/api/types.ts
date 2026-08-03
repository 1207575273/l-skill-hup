/**
 * 后端 DTO 的 TS 类型定义 —— 单一事实源。
 *
 * 逐个对照 `backend/src/main/java/com/landray/skillhub/interfaces/rest/**` 下的 Controller 与
 * Request/Response record 手写(20 个端点、24 个 DTO 全部读过源码,不是看文档推断)。**契约变了先改
 * 这里**,让 tsc 把所有受影响的地方报出来,不要散落到各个调用点各写一份。
 *
 * 将来由 `openapi-typescript` 从后端导出的 `openapi.json` 生成 `schema.d.ts` 替换本文件。生成的类型
 * 是参考答案不是圣旨:不要求一次性全量替换,哪个接口的 DTO 复杂就换成生成的,简单的继续手写。集中放在
 * 这一个文件,就是为了将来能一次性替换 —— 散落各处就换不掉了。
 *
 * 字段一律 camelCase(Spring Boot 4 的 Jackson 3 默认按 record accessor 名序列化,未额外配置
 * `@JsonProperty`)。**可空性如实反映 Java 侧的装箱类型 / 校验注解**:`long`/`int`/`boolean` 原始类型
 * 永不为空;`Long`/`Integer`/`Boolean`/`String` 装箱类型只要没有 `@NotNull`/`@NotBlank` 兜底,就按可空
 * 处理。与本文件上一版(P0 手写推断版)相比的具体差异清单见本次任务报告,不在这里重复。
 */

// ==================== 通用 ====================

/**
 * 分页响应体。对应 `PagedResponse<T>`(`interfaces/rest/PagedResponse.java`)与
 * `SkillController#listSkills` 直接返回的 `PageResult<T>`(`shared/model/PageResult.java`)——
 * 两者序列化结构完全一致,前端不需要区分。`page` 从 1 开始,`size` 默认 20、上限 100
 * (`SkillApplicationService` / `UserController` 里的 `DEFAULT_SIZE` / `MAX_SIZE`)。
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
 * 错误响应体,对应 `ErrorResponse.java`。`traceId` 必填 —— 这是排障第一入口,不许被吞掉。
 * `code` 是稳定的机器可读枚举,前端据此分支;`message` 面向人,不能拿它做逻辑判断。
 * `details` 是 `Map<String, Object>`,`@JsonInclude(NON_NULL)` 意味着为空时整个字段不出现在 JSON 里。
 */
export interface ApiErrorBody {
  readonly code: string
  readonly message: string
  readonly traceId: string
  readonly details?: Record<string, unknown>
}

// ==================== 枚举(与后端 domain.model 的 Java 枚举一一对应) ====================

/** `SkillSource`(`skill.source`)。序列化用 `.name()`,大小写敏感,后端解析时不区分大小写。 */
export type SkillSource = 'INTERNAL' | 'COMMUNITY'

/** `SkillStatus`(`skill.status`)。UNLISTED 不物理删除,详情/安装单仍可直达。 */
export type SkillStatus = 'PUBLISHED' | 'UNLISTED'

/** `Certification`(挂在版本上,不挂在 skill 上)。 */
export type CertificationStatus = 'NONE' | 'CERTIFIED' | 'REVOKED'

/** `TagKind`(`tag.kind`)。CURATED 由 ADMIN 维护、每个 skill 必须 1~3 个;FREE 自由标签,至多 10 个。 */
export type TagKind = 'CURATED' | 'FREE'

/** `UserRole`(`app_user.role`)。只有两级,不做 RBAC。 */
export type UserRole = 'USER' | 'ADMIN'

/** `AppUserStatus`(`app_user.status`)。P0 不会产生 MERGED 的数据,但枚举值先建全。 */
export type AppUserStatus = 'ACTIVE' | 'DISABLED' | 'MERGED'

/** `IdentityProvider`(`user_identity.provider`)。P0 只启用 LOCAL,DINGTALK 排 P1。 */
export type AuthProviderCode = 'LOCAL' | 'DINGTALK'

/**
 * `ClientCode.apiCode()`(`domain/model/ClientCode.java`)。**注意大小写**:接口与数据库里是小写字符串
 * (`claude`/`codex`/`workbuddy`),不是 Java 枚举常量名的大写形式。未在 `GET /clients` 返回的组合,
 * 服务端会拒绝生成安装单。
 */
export type ClientCode = 'claude' | 'codex' | 'workbuddy'

/** `InstallScope.apiCode()`。global 落 `$HOME/<skillsSubDir>`,project 落当前工作目录下 `<skillsSubDir>`。 */
export type ScopeCode = 'global' | 'project'

/** `SkillSortOption`,`GET /skills` 的 `sort` 参数值,大小写不敏感,留空按 `latest` 处理。 */
export type SkillSortOption = 'downloads' | 'likes' | 'latest'

// ==================== 归属人与用户(auth / users / identities) ====================

/**
 * skill 归属人展示信息,对应 `SkillOwnerResponse.java`。
 *
 * **字段名是 `userId`,不是 `id`**——这是本次核对发现的一处真实差异,P0 手写版当初猜成了 `id`。
 * `SkillOwnerResponse.from(owner)` 在 `owner` 为 `null` 时整体返回 `null`,因此嵌它的字段都必须是
 * `SkillOwner | null`,不能假设归属人恒存在。
 */
export interface SkillOwner {
  readonly userId: number
  readonly displayName: string
  readonly employeeNo: string | null
}

/** `GET /identities` 与 `GET /auth/me` 里的一条身份,对应 `IdentitySummaryResponse.java`。 */
export interface IdentitySummary {
  readonly id: number
  readonly provider: AuthProviderCode
  /** LOCAL 时是登录名,DINGTALK 时是展示提示,不是 unionId。 */
  readonly externalId: string
  readonly displayHint: string | null
  /** 从未登录成功过时为 `null`(`UserIdentity.newLocal` 建号时就是 `null`)。 */
  readonly lastLoginAt: string | null
  readonly createdAt: string
}

/**
 * `GET /auth/me` 响应体,对应 `MeResponse.java`。
 *
 * **不是** `SkillOwner` 的超集,字段名也不同(`id` 不是 `userId`)——两者是完全独立的两个 DTO,
 * 不要试图共用一个类型。
 */
export interface CurrentUser {
  readonly id: number
  readonly displayName: string
  readonly employeeNo: string | null
  readonly email: string | null
  readonly avatarUrl: string | null
  readonly role: UserRole
  readonly identities: IdentitySummary[]
}

/**
 * `GET /users` 列表一行(ADMIN 专属),对应 `UserSummaryResponse.java`。
 * `providers` 是这个人持有的登录方式集合,**不含** `externalId`(登录名不对外展示)。
 */
export interface UserSummary {
  readonly id: number
  readonly displayName: string
  readonly employeeNo: string | null
  readonly email: string | null
  readonly role: UserRole
  readonly status: AppUserStatus
  readonly providers: AuthProviderCode[]
  readonly createdAt: string
}

export interface UserListFilter extends PageQuery {
  /** 后端用 `@Pattern(regexp = "LOCAL|DINGTALK")` 精确匹配大写,不是大小写不敏感。 */
  readonly provider?: AuthProviderCode
}

// ==================== 标签(tags) ====================

/** `GET /tags` 的一个词条,对应 `TagResponse.java`。 */
export interface Tag {
  readonly id: number
  readonly name: string
  readonly kind: TagKind
  readonly description: string | null
  readonly sortOrder: number
  /** 实时 GROUP BY 算出的可见 skill 数,只有 `/tags` 列表带这个字段。 */
  readonly skillCount: number
}

/**
 * skill 详情/卡片上嵌套的标签行元素,对应 `SkillTagResponse.java`。
 * 与 {@link Tag} 字段几乎相同,**唯独没有 `skillCount`**——不是同一个响应体,不能共用类型。
 */
export interface SkillTag {
  readonly id: number
  readonly name: string
  readonly kind: TagKind
  readonly description: string | null
  readonly sortOrder: number
}

// ==================== skill ====================

/**
 * `GET /skills` 列表卡片,对应 `SkillSummaryResponse.java`。
 *
 * **与详情响应体是两个完全独立的结构,不是彼此的子集/超集**(与 P0 手写版的假设不同)。列表里
 * **没有 `description` / `source` / `status` / `dislikeCount` / `commentCount`**,也**没有版本号**——
 * `certification` 是唯一暴露的版本相关字段,取自 `skill.latestVersion()`,`latestVersion == null` 时
 * 服务端已回落成 `'NONE'`(见 `SkillSummaryResponse.from`),前端不需要也不能自己再判断一次。
 */
export interface SkillSummary {
  readonly id: number
  readonly name: string
  readonly displayName: string
  readonly cardExcerpt: string
  readonly certification: CertificationStatus
  readonly downloadCount: number
  readonly likeCount: number
  readonly favoriteCount: number
  readonly updatedAt: string
  readonly owner: SkillOwner | null
  readonly tags: SkillTag[]
  /**
   * 最新版本的 id,没有任何版本时为 `null`。
   *
   * 列表页"加入安装单"需要它 —— 安装单的每一项都要 `versionId`。后端本来就已经拿着
   * `latestVersion` 在算 `certification`,顺带返回这个 id 零查询成本;不给的话前端每点一次
   * 都要再发一个请求去查版本列表。
   */
  readonly latestVersionId: number | null
}

/**
 * `GET /skills/manage` 的行。**与 `SkillSummary` 是两个类型,不要合并**:
 * 管理视图含已下架,所以 `status` 是它的主信息;公开列表恒为 `PUBLISHED`,带上那个字段只是噪音。
 * 字段也刻意更少 —— 管理页是操作表格,不渲染卡片,不需要摘要、标签、点赞收藏数。
 */
export interface ManagedSkill {
  readonly id: number
  readonly name: string
  readonly displayName: string
  readonly status: 'PUBLISHED' | 'UNLISTED'
  readonly certification: CertificationStatus
  readonly downloadCount: number
  readonly updatedAt: string
  readonly latestVersionId: number | null
}

/**
 * `GET /skills/{id}/versions` 的一个版本条目,对应 `SkillVersionResponse.java`。**不含正文**
 * (`skillMdContent` / `readmeContent` / `changelog` / `allowedTools` 只有详情场景才 SELECT)。
 */
export interface SkillVersionSummary {
  readonly id: number
  readonly version: string
  readonly sizeBytes: number
  readonly fileCount: number
  readonly certification: CertificationStatus
  readonly certifiedByUserId: number | null
  readonly certifiedAt: string | null
  readonly certifyNote: string | null
  readonly createdByUserId: number | null
  readonly createdAt: string
}

/**
 * `GET /skills/{id}` 详情里的最新版本(含正文),对应 `SkillVersionDetailResponse.java`。
 *
 * `readmeContent` 非空则优先渲染它,否则渲染 `skillMdContent`——两个字段原样回传,由前端按此规则
 * 二选一渲染,服务端不预先合并成一个字段。
 */
export interface SkillVersionDetail {
  readonly id: number
  readonly version: string
  readonly sizeBytes: number
  readonly fileCount: number
  readonly allowedTools: string[]
  readonly skillMdContent: string
  readonly readmeContent: string | null
  readonly changelog: string | null
  readonly certification: CertificationStatus
  readonly certifiedByUserId: number | null
  readonly certifiedAt: string | null
  readonly certifyNote: string | null
  readonly createdByUserId: number | null
  readonly createdAt: string
  /**
   * 制品里的文件清单,按 path 字典序。用于详情页把 skill 展开成文件树。
   *
   * `path` 是**剥壳后的相对路径**(不含顶层包裹目录),例如 `SKILL.md`、`references/guide.md`。
   * 后端在响应时临时打开制品 zip 读中央目录得到,不解压内容;制品缺失或损坏时返回空数组
   * 而不是让整个详情接口失败 —— 详情页的主体是元数据,文件清单只是附加信息。
   */
  readonly files: SkillVersionFile[]
}

/** 文件清单的一项。 */
export interface SkillVersionFile {
  readonly path: string
  readonly sizeBytes: number
}

/**
 * `GET /skills/{id}/versions/{versionId}/file?path=...` 的响应,详情页文件预览用。
 *
 * `previewable` 为 false 时 `content` 是 null,原因在 `notPreviewableReason` 里:
 * `BINARY`(内容含 NUL 字节)或 `TOO_LARGE`(超过服务端阈值)。**判定在服务端做** ——
 * 只有它拿得到字节,前端连内容都没有,没法自己判断。
 */
export interface SkillFileContent {
  readonly path: string
  readonly sizeBytes: number
  readonly previewable: boolean
  readonly content: string | null
  readonly notPreviewableReason: 'BINARY' | 'TOO_LARGE' | null
}

/**
 * `GET /skills/{id}` 详情响应,对应 `SkillDetailResponse.java`。
 *
 * 刻意**没有** `myReaction` / `myFavorited` 字段——那是 P2 收藏/点赞点踩上线后才会存在的内容,当前
 * 后端响应体里根本不返回这两个键(不是"恒为 null",而是压根没有这个字段)。P0 手写版把这两个字段
 * 当作"恒为 null / false"写进类型是错的,已删除。
 *
 * `certification` 不是本响应体的顶层字段——认证状态挂在 `latestVersion.certification` 上;
 * `latestVersion` 可能为 `null`(`SkillVersionDetailResponse.from` 显式处理了这个分支),前端要判断
 * "未认证"时应读 `latestVersion?.certification ?? 'NONE'`,与列表页 `SkillSummary.certification`
 * 的语义对齐。
 */
export interface SkillDetail {
  readonly id: number
  readonly name: string
  readonly displayName: string
  readonly description: string
  readonly source: SkillSource
  readonly status: SkillStatus
  readonly cardExcerpt: string
  readonly downloadCount: number
  readonly likeCount: number
  readonly dislikeCount: number
  readonly favoriteCount: number
  readonly commentCount: number
  readonly createdAt: string
  readonly updatedAt: string
  readonly owner: SkillOwner | null
  readonly tags: SkillTag[]
  readonly latestVersion: SkillVersionDetail | null
}

/** `GET /skills` 查询参数,对应 `SkillController#listSkills` 的方法签名。 */
export interface SkillListFilter extends PageQuery {
  readonly source?: SkillSource
  /** 多值 AND,后端用 `GROUP BY ... HAVING count(*) = n` 实现,序列化成重复的 `tagIds=1&tagIds=2`。 */
  readonly tagIds?: number[]
  readonly certifiedOnly?: boolean
  readonly keyword?: string
  readonly ownerUserId?: number
  readonly sort?: SkillSortOption
}

// ==================== 上传(precheck / publish) ====================

/**
 * `POST /skills/precheck` 响应体里 `existing` 字段,重名时给出的决策依据,对应
 * `PrecheckResponse.ExistingSkillInfo`。
 */
export interface SkillPrecheckExisting {
  readonly skillId: number
  readonly name: string
  readonly ownerDisplayName: string
  readonly ownerEmployeeNo: string | null
  readonly latestVersion: string
  /**
   * 服务端算好的权限位,前端只用它决定要不要显示"作为新版本"按钮,
   * **真正的拦截在服务端**,不能自己判断权限。
   */
  readonly canPublishNewVersion: boolean
}

/** `contentDigest` 命中已有版本时的定位信息,对应 `PrecheckResponse.IdenticalMatch`。 */
export interface SkillPrecheckIdenticalMatch {
  readonly skillId: number
  readonly version: string
}

/**
 * 单条校验失败原因,对应 `PrecheckResponse.Violation`。**不是字符串数组**——P0 手写版猜成了
 * `string[]`,实际后端返回的是 `{code, message}` 对象数组,前端可以按 `code` 分支处理不同违规类型。
 */
export interface SkillPrecheckViolation {
  readonly code: string
  readonly message: string
}

/**
 * `POST /skills/precheck` 响应体,对应 `PrecheckResponse.java`。
 *
 * `valid=false` 时 `parsed` / `contentDigest` / `fileCount` / `unpackedBytes` / `hasReadme` /
 * `identical` / `existing` / `suggestedName` 全部为 `null`(`PrecheckResult.invalid` 的构造方式),
 * `violations` 恒非空。这几个字段因此按 Java 侧的装箱类型(`Integer`/`Long`/`Boolean`)如实标成可空,
 * 不是"理论上不会是 null 但保守写了 null"。
 */
export interface SkillPrecheckResult {
  readonly valid: boolean
  readonly parsed: {
    readonly name: string
    readonly description: string
    readonly allowedTools: string[]
  } | null
  readonly contentDigest: string | null
  readonly fileCount: number | null
  readonly unpackedBytes: number | null
  readonly hasReadme: boolean | null
  readonly identical: SkillPrecheckIdenticalMatch | null
  readonly duplicatedName: boolean
  readonly existing: SkillPrecheckExisting | null
  readonly suggestedName: string | null
  readonly violations: SkillPrecheckViolation[]
}

/** `POST /skills/{id}/update` 请求体,对应 `UpdateSkillRequest.java`。刻意没有 `name`——一经创建不可改名。 */
export interface UpdateSkillPayload {
  readonly displayName: string
  readonly source: SkillSource
  /** 后端 `@NotEmpty`:CURATED 标签数量必须在 1~3 之间。 */
  readonly curatedTagIds: number[]
  readonly freeTagNames?: string[]
}

// ==================== 安装(clients / bundles / 脚本) ====================

/** `GET /clients` 返回的适配表条目,对应 `ClientTargetResponse.java`。 */
export interface ClientTarget {
  readonly client: ClientCode
  readonly scope: ScopeCode
  /** 落盘子路径(如 `.claude/skills`),不含安装根目录本身。 */
  readonly skillsSubDir: string
}

export interface InstallBundleItem {
  readonly skillId: number
  readonly versionId: number
}

/** `POST /bundles` 请求体,对应 `CreateBundleRequest.java`。 */
export interface InstallBundleRequest {
  readonly items: InstallBundleItem[]
  readonly client: ClientCode
  readonly scope: ScopeCode
}

/**
 * `POST /bundles` 响应体,对应 `CreateBundleResponse.java`。
 *
 * **比 P0 手写版丰富得多**:后端已经拼好可直接复制粘贴的命令(`powerShellCommand` /
 * `bashCommand`),前端不需要自己拼 `irm ... | iex` / `curl ... | sh` 这套模板——那本来就是这个平台
 * 唯一的价值("选 skill -> 一条命令"),只值得在服务端写一遍。
 */
export interface InstallBundleResult {
  readonly token: string
  readonly expiresAt: string
  readonly client: ClientCode
  readonly scope: ScopeCode
  readonly powerShellCommand: string
  readonly bashCommand: string
}

// ==================== 认证 ====================

export interface LoginPayload {
  readonly login: string
  readonly password: string
}

export interface RegisterPayload {
  readonly displayName: string
  readonly login: string
  readonly password: string
}

/**
 * `POST /auth/login` / `POST /auth/register` 响应体,对应 `LoginResponse.java`。
 *
 * **不是**嵌套的 `{ token, expiresAt, user: CurrentUser }`——P0 手写版猜的结构是错的。实际是扁平的
 * `{ token, expiresAt, userId, displayName, role }`,**没有 `employeeNo` / `identities`**,登录后要拿
 * 完整信息仍需另外调一次 `GET /auth/me`。
 */
export interface LoginResult {
  readonly token: string
  readonly expiresAt: string
  readonly userId: number
  readonly displayName: string
  readonly role: UserRole
}

/**
 * `GET /auth/providers`,对应 `ProvidersResponse.java`。**比 P0 手写版多一个 `localRegisterEnabled`**——
 * 前端要不要显示"注册"入口由服务端告知,不该自己猜配置。
 */
export interface AuthProviders {
  readonly providers: AuthProviderCode[]
  readonly localRegisterEnabled: boolean
}

export interface ChangePasswordPayload {
  readonly oldPassword: string
  readonly newPassword: string
}

/** `POST /users` 请求体(ADMIN 专属),对应 `CreateUserRequest.java`。`role` 缺省时后端按 `USER` 处理。 */
export interface CreateUserPayload {
  readonly displayName: string
  readonly login: string
  readonly password: string
  readonly role?: UserRole
}
