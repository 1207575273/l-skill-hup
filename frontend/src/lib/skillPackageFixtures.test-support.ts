/**
 * skill 包相关测试的共享 fixture。**只给测试用,不被任何非测试代码引用。**
 *
 * jsdom 不实现 `File.webkitRelativePath`(`node_modules/jsdom` 源码里搜不到这个
 * 属性名),而 `directoryTree.ts` 依赖它 —— 真实浏览器里这个属性由
 * `webkitdirectory` 选择器自动写入,测试只能用 `Object.defineProperty` 手动补上。
 *
 * 文件名用 `.test-support.ts` 而不是 `.ts`,是为了在文件列表里一眼看出"这是测试
 * 基础设施,不是业务代码",同时不匹配 Vitest 默认的 `*.test.ts` 收集规则,
 * 不会被当成一个空测试文件执行。
 */
import type { PackageEntry } from './skillPackageTypes'

export function createFile(name: string, content: string, type = 'text/plain'): File {
  return new File([content], name, { type })
}

export function withRelativePath(file: File, relativePath: string): File {
  Object.defineProperty(file, 'webkitRelativePath', {
    value: relativePath,
    configurable: true,
  })
  return file
}

/** 直接指定相对路径创建一个"看起来来自 webkitdirectory"的 File。 */
export function createDirectoryFile(
  relativePath: string,
  content: string,
  type = 'text/plain',
): File {
  const segments = relativePath.split('/')
  const name = segments[segments.length - 1] ?? relativePath
  return withRelativePath(createFile(name, content, type), relativePath)
}

/** 不依赖 File / Blob 具体来源的最小 `PackageEntry`,给校验器单测直接用。 */
export function createTextEntry(path: string, text: string): PackageEntry {
  const blob = new Blob([text], { type: 'text/plain' })
  return { path, size: blob.size, toBlob: () => blob }
}

/**
 * 声明大小与实际内容脱钩的 `PackageEntry`,专给体积上限类用例用 ——
 * 校验器只读 `entry.size` 字段做判断,不需要真的分配几十 MB 内存去撑出一个
 * "超限"场景。
 */
export function createSizedEntry(path: string, size: number, text = ''): PackageEntry {
  const blob = new Blob([text], { type: 'text/plain' })
  return { path, size, toBlob: () => blob }
}

/** 合法 SKILL.md 全文,允许覆盖三个字段,省得每个用例都手写 frontmatter。 */
export function validSkillMd(
  overrides: { name?: string; description?: string; allowedTools?: string } = {},
): string {
  const name = overrides.name ?? 'demo-skill'
  const description = overrides.description ?? '一个用于测试的示例 skill'
  const allowedToolsLine =
    overrides.allowedTools !== undefined ? `\nallowed-tools: ${overrides.allowedTools}` : ''
  return `---\nname: ${name}\ndescription: ${description}${allowedToolsLine}\n---\n\n# ${name}\n`
}
