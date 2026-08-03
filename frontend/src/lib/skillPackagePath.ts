/**
 * skill 包内文件路径的安全与结构判断。
 *
 * **前端校验不是安全边界**,这套判断只服务于"上传前的即时反馈"——见
 * `skillPackageTypes.ts` 头部声明,服务端会用同一套规则(业务建模 5.2/5.3)
 * 完整重做一遍。
 */
import { SKILL_PACKAGE_LIMITS } from './skillPackageLimits'
import type { PackageViolation } from './skillPackageTypes'

// biome-ignore lint/suspicious/noControlCharactersInRegex: 就是要匹配控制字符本身,拒绝含控制字符的路径
const CONTROL_CHAR_PATTERN = /[\x00-\x1f\x7f]/
const WINDOWS_DRIVE_PATTERN = /^[A-Za-z]:[/\\]/

/** 取路径第一段,用 `indexOf` 而不是 `split('/')[0]`,避免 `noUncheckedIndexedAccess` 下的非空断言。 */
function firstSegment(path: string): string {
  const slashIndex = path.indexOf('/')
  return slashIndex === -1 ? path : path.slice(0, slashIndex)
}

/**
 * 逐条判断单个包内路径是否安全:拒绝 `..`、绝对路径、控制字符,并校验长度上限
 * (`.claude/CLAUDE.md` 第七节 2 条 + 业务建模 5.2)。
 *
 * **只认精确的 `..` 路径段**,不误伤文件名里含连续点号的合法名字(如
 * `foo..bar.md`)——"拒绝含 `..` 的条目"指的是路径穿越语义,不是子串匹配。
 */
export function assessPackagePath(rawPath: string): PackageViolation[] {
  const violations: PackageViolation[] = []

  if (
    rawPath.startsWith('/') ||
    rawPath.startsWith('\\\\') ||
    WINDOWS_DRIVE_PATTERN.test(rawPath)
  ) {
    violations.push({
      code: 'PATH_ABSOLUTE',
      message: `拒绝绝对路径:${rawPath}`,
      path: rawPath,
    })
  }

  if (rawPath.split('/').includes('..')) {
    violations.push({
      code: 'PATH_TRAVERSAL',
      message: `路径包含 .. 段,存在路径穿越风险:${rawPath}`,
      path: rawPath,
    })
  }

  if (CONTROL_CHAR_PATTERN.test(rawPath)) {
    violations.push({
      code: 'PATH_CONTROL_CHAR',
      message: `路径包含控制字符:${JSON.stringify(rawPath)}`,
      path: rawPath,
    })
  }

  if (rawPath.length > SKILL_PACKAGE_LIMITS.MAX_PATH_LENGTH) {
    violations.push({
      code: 'PATH_TOO_LONG',
      message: `路径长度 ${rawPath.length} 超过上限 ${SKILL_PACKAGE_LIMITS.MAX_PATH_LENGTH}:${rawPath}`,
      path: rawPath,
    })
  }

  return violations
}

/**
 * 识别一批路径是否共享同一个顶层包裹目录。
 *
 * 对应业务建模 5.3 步骤 5"规范化文件树(剥垃圾、剥顶层目录)"——服务端在结构
 * 校验之后会剥掉这层包裹。前端预校验要给出同样宽容的判断,否则"选文件夹"上传
 * (天然带一层文件夹名,如 `my-skill/SKILL.md`)会被误判成顶层目录不合规。
 *
 * 判断规则:所有路径都至少有两段(即没有任何文件直接躺在根),且第一段完全
 * 相同,才认为存在包裹目录;否则返回 null,按"条目本身就在根"处理。
 *
 * **这是本次实现里没有直接文档依据、需要回头确认的一处解释**,细节见任务报告。
 */
export function resolvePackageRoot(paths: readonly string[]): string | null {
  if (paths.length === 0) {
    return null
  }

  const hasRootLevelEntry = paths.some((path) => !path.includes('/'))
  if (hasRootLevelEntry) {
    return null
  }

  const roots = new Set(paths.map(firstSegment))
  if (roots.size !== 1) {
    return null
  }
  for (const root of roots) {
    return root
  }
  return null // 不可达,仅为满足控制流分析
}

/** 去掉包裹目录前缀,`root` 为 null 时原样返回。 */
export function stripRoot(path: string, root: string | null): string {
  if (root === null) {
    return path
  }
  const prefix = `${root}/`
  return path.startsWith(prefix) ? path.slice(prefix.length) : path
}

/** 目录深度:相对路径里的目录段数(不含文件名本身)。 */
export function pathDepth(relativePath: string): number {
  const segments = relativePath.split('/')
  return Math.max(segments.length - 1, 0)
}
