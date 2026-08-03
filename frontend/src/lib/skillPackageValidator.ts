/**
 * skill 包完整性校验:整合结构白名单、路径安全、体积 / 数量上限、frontmatter 解析。
 *
 * **前端校验不是安全边界。** 这里的每一条规则都对应业务建模文档
 * (`docs/20260802224934_业务建模与接口契约.md`)5.1/5.2/5.3,服务端
 * `SkillPackageValidator` 会用同一套规则完整重做一遍,不能因为这里已经校验过
 * 就在服务端省略 —— 这个项目分发的是会被 Agent 执行的内容,那个错觉的代价
 * 很高(`.claude/CLAUDE.md` 第七节)。这里存在的意义只是让作者上传前先看到问题。
 *
 * 返回结构化的 `PackageValidationReport`(通过 / 违反哪几条、具体是哪个文件),
 * 而不是一个 boolean —— 上传页要靠它告诉作者包哪里不合规。
 */
import { parseSkillFrontmatter } from './skillFrontmatter'
import { exceedsCompressionRatioLimit, SKILL_PACKAGE_LIMITS } from './skillPackageLimits'
import { assessPackagePath, pathDepth, resolvePackageRoot, stripRoot } from './skillPackagePath'
import type {
  PackageEntry,
  PackageValidationReport,
  PackageViolation,
  SkillFrontmatterData,
} from './skillPackageTypes'

const SKILL_MD_FILE_NAME = 'SKILL.md'
const README_FILE_NAME = 'README.md'
const ALLOWED_TOP_LEVEL_DIRECTORIES = new Set(['references', 'scripts', 'assets'])
const ALLOWED_TOP_LEVEL_FILES = new Set([SKILL_MD_FILE_NAME, README_FILE_NAME])

/**
 * 常见操作系统 / 工具垃圾文件,一律静默剔除,不计入任何校验。
 *
 * 依据业务建模 5.3 第 2 条:"剥掉垃圾。`__MACOSX/`、`.DS_Store`、`Thumbs.db`、
 * `.git/`、`node_modules/` 一律丢弃 —— 这些在用户机器上被打进包里几乎是必然的"。
 *
 * **这是一处解释,不是文档明确规定的前端行为**:5.3 把"结构校验"排在"剥垃圾"
 * 之前(步骤 3 早于步骤 5),严格照抄步骤编号会导致几乎每个 macOS 用户的压缩包
 * 都因为带 `.DS_Store` 被硬拒。前端只做即时反馈,不复刻服务端重新打包的完整
 * 流水线,选择把"剥垃圾"提到最前面执行,避免这种过度拦截。已在任务报告里
 * 标注为待确认项。
 */
function isKnownGarbageEntry(path: string): boolean {
  const garbageSegments = new Set(['.DS_Store', 'Thumbs.db', '__MACOSX', '.git', 'node_modules'])
  return path.split('/').some((segment) => garbageSegments.has(segment))
}

export interface ValidateSkillPackageOptions {
  /**
   * 原始压缩包的字节数(拖入 zip 形态才有意义)。提供了才会做压缩比检查——
   * 选文件夹 / 单文件两种形态在打包前不存在"压缩后大小"这个概念,不适用该检查。
   */
  readonly originalArchiveBytes?: number
}

export async function validateSkillPackage(
  entries: readonly PackageEntry[],
  options: ValidateSkillPackageOptions = {},
): Promise<PackageValidationReport> {
  const relevantEntries = entries.filter((entry) => !isKnownGarbageEntry(entry.path))
  const violations: PackageViolation[] = []

  if (relevantEntries.length > SKILL_PACKAGE_LIMITS.MAX_ENTRY_COUNT) {
    violations.push({
      code: 'ENTRY_COUNT_EXCEEDED',
      message: `包内条目数 ${relevantEntries.length} 超过上限 ${SKILL_PACKAGE_LIMITS.MAX_ENTRY_COUNT}`,
    })
  }

  const root = resolvePackageRoot(relevantEntries.map((entry) => entry.path))
  let unpackedBytes = 0
  let hasSkillMd = false
  let hasReadme = false
  let skillMdEntry: PackageEntry | null = null

  for (const entry of relevantEntries) {
    unpackedBytes += entry.size
    violations.push(...assessPackagePath(entry.path))

    if (entry.size > SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES) {
      violations.push({
        code: 'SINGLE_FILE_TOO_LARGE',
        message: `文件 ${entry.path} 大小 ${entry.size} 字节超过单文件上限 ${SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES}`,
        path: entry.path,
      })
    }

    const relativePath = stripRoot(entry.path, root)
    const depth = pathDepth(relativePath)
    if (depth > SKILL_PACKAGE_LIMITS.MAX_DIRECTORY_DEPTH) {
      violations.push({
        code: 'DIRECTORY_TOO_DEEP',
        message: `路径 ${entry.path} 目录深度 ${depth} 超过上限 ${SKILL_PACKAGE_LIMITS.MAX_DIRECTORY_DEPTH}`,
        path: entry.path,
      })
    }

    const segments = relativePath.split('/')
    const topLevelName = segments[0] ?? ''
    const isTopLevelFile = segments.length === 1
    const topLevelAllowed = isTopLevelFile
      ? ALLOWED_TOP_LEVEL_FILES.has(topLevelName)
      : ALLOWED_TOP_LEVEL_DIRECTORIES.has(topLevelName)
    if (!topLevelAllowed) {
      violations.push({
        code: 'TOP_LEVEL_NOT_ALLOWED',
        message: `顶层条目 ${topLevelName} 不在白名单(SKILL.md / README.md / references/ / scripts/ / assets/)内`,
        path: entry.path,
      })
    }

    if (isTopLevelFile && topLevelName === SKILL_MD_FILE_NAME) {
      hasSkillMd = true
      skillMdEntry = entry
    }
    if (isTopLevelFile && topLevelName === README_FILE_NAME) {
      hasReadme = true
    }
  }

  if (unpackedBytes > SKILL_PACKAGE_LIMITS.UNPACKED_TOTAL_MAX_BYTES) {
    violations.push({
      code: 'UNPACKED_TOTAL_TOO_LARGE',
      message: `解压后总大小 ${unpackedBytes} 字节超过上限 ${SKILL_PACKAGE_LIMITS.UNPACKED_TOTAL_MAX_BYTES}`,
    })
  }

  if (
    options.originalArchiveBytes !== undefined &&
    exceedsCompressionRatioLimit(unpackedBytes, options.originalArchiveBytes)
  ) {
    violations.push({
      code: 'COMPRESSION_RATIO_TOO_HIGH',
      message: `压缩比超过 ${SKILL_PACKAGE_LIMITS.MAX_COMPRESSION_RATIO}:1,疑似 zip 炸弹`,
    })
  }

  if (!hasSkillMd) {
    violations.push({
      code: 'SKILL_MD_MISSING',
      message: `包内缺少 ${SKILL_MD_FILE_NAME}`,
    })
  }

  const parsed = skillMdEntry ? await readSkillMdFrontmatter(skillMdEntry, violations) : null

  return {
    valid: violations.length === 0,
    root,
    parsed,
    fileCount: relevantEntries.length,
    unpackedBytes,
    hasReadme,
    violations,
  }
}

async function readSkillMdFrontmatter(
  entry: PackageEntry,
  violations: PackageViolation[],
): Promise<SkillFrontmatterData | null> {
  const blob = await entry.toBlob()
  const text = await blob.text()
  const result = parseSkillFrontmatter(text)
  if (!result.ok) {
    violations.push(...result.violations)
    return null
  }
  return result.data
}
