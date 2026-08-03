/**
 * skill 包浏览器端处理的共享类型。
 *
 * 集中放这里(而不是散在 directoryTree / skillFrontmatter / skillPackageValidator /
 * skillPackageBuilder 各写一份),原因与 `api/types.ts` 相同:`PackageEntry` /
 * `PackageViolation` 被这几个模块共同使用,分散定义会导致循环引用或重复声明。
 *
 * **前端校验不是安全边界。** 这里的一切校验只是为了让作者上传前就看到问题、
 * 即时反馈,不是安全边界。服务端 `SkillPackageValidator` 会用业务建模文档
 * 5.1/5.2/5.3 同一套规则完整重做一遍。任何"前端已经校验过了,服务端可以略过"
 * 的想法都是错的 —— 这个项目分发的是会被 Agent 执行的内容,那个错觉的代价
 * 很高(`.claude/CLAUDE.md` 第七节安全底线)。
 */

/**
 * 包内一个文件条目,与来源无关 —— 可能来自 `webkitdirectory` 拿到的 `File`,
 * 也可能来自 JSZip 从已有 zip 里解出的条目。
 */
export interface PackageEntry {
  /**
   * 包内相对路径,`/` 分隔。**未必已经过安全校验** —— 路径穿越 / 控制字符恰恰是
   * 校验要抓的东西,不能假设传进来的路径已经是干净的。
   */
  readonly path: string
  readonly size: number
  /** 取文件内容。File 本身已经是 Blob,可同步返回;zip 内条目需要异步解压。 */
  readonly toBlob: () => Blob | Promise<Blob>
}

export type PackageViolationCode =
  | 'ENTRY_COUNT_EXCEEDED'
  | 'SINGLE_FILE_TOO_LARGE'
  | 'UNPACKED_TOTAL_TOO_LARGE'
  | 'COMPRESSION_RATIO_TOO_HIGH'
  | 'MULTIPART_TOTAL_TOO_LARGE'
  | 'PATH_TRAVERSAL'
  | 'PATH_ABSOLUTE'
  | 'PATH_CONTROL_CHAR'
  | 'PATH_TOO_LONG'
  | 'DIRECTORY_TOO_DEEP'
  | 'TOP_LEVEL_NOT_ALLOWED'
  | 'SKILL_MD_MISSING'
  | 'FRONTMATTER_MISSING_DELIMITER'
  | 'FRONTMATTER_UNTERMINATED'
  | 'FRONTMATTER_MISSING_NAME'
  | 'FRONTMATTER_NAME_INVALID'
  | 'FRONTMATTER_MISSING_DESCRIPTION'
  | 'FRONTMATTER_DESCRIPTION_TOO_LONG'
  | 'FRONTMATTER_DESCRIPTION_INVALID'
  | 'ZIP_SIGNATURE_INVALID'
  | 'ZIP_PARSE_FAILED'

/** 单条违规。`path` 在能定位到具体文件时才给,聚合类问题(总大小、总条目数)不给。 */
export interface PackageViolation {
  readonly code: PackageViolationCode
  readonly message: string
  readonly path?: string
}

/** `SKILL.md` frontmatter 解析结果里业务真正关心的三个字段(业务建模 5.1 步骤 4)。 */
export interface SkillFrontmatterData {
  readonly name: string
  readonly description: string
  readonly allowedTools: string[]
}

/** `validateSkillPackage` 的结构化结果 —— 上传页据此告诉作者具体哪里不合规,而不是一个 boolean。 */
export interface PackageValidationReport {
  readonly valid: boolean
  /**
   * 识别出的顶层包裹目录名;条目本身就在根时为 null。
   * 对应业务建模 5.3 步骤 5"剥顶层目录"在前端预校验里的等价判断,细节见
   * `skillPackagePath.ts` 的 `resolvePackageRoot`。
   */
  readonly root: string | null
  readonly parsed: SkillFrontmatterData | null
  readonly fileCount: number
  readonly unpackedBytes: number
  readonly hasReadme: boolean
  readonly violations: PackageViolation[]
}
