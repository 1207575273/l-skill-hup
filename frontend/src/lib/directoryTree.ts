/**
 * 目录读取:把 `<input type="file" webkitdirectory>` 拿到的扁平文件集合还原成
 * 目录树,并转成校验 / 打包共用的 `PackageEntry[]`。
 *
 * 参数类型用 `Iterable<File>` 而不是 DOM 的 `FileList`——两者结构兼容
 * (`FileList` 实现了 `Symbol.iterator`),真实调用点直接传 `input.files` 即可;
 * 用更宽的类型是为了测试时能传普通数组,不必在 jsdom 里伪造一个 `FileList`
 * (jsdom 没有提供 `FileList` 的公开构造函数)。
 *
 * **前端校验不是安全边界。** 这里只做展示与拍平,真正的路径安全判断在
 * `skillPackagePath.ts`,由 `skillPackageValidator.ts` 统一调用。
 */
import type { PackageEntry } from './skillPackageTypes'

export type DirectoryTreeNode =
  | { readonly type: 'file'; readonly name: string; readonly path: string; readonly size: number }
  | {
      readonly type: 'directory'
      readonly name: string
      readonly path: string
      readonly children: DirectoryTreeNode[]
    }

/**
 * 还原目录树。返回顶层节点数组(选文件夹场景通常只有一个目录节点,即用户选中的
 * 那个文件夹),不假设浏览器一定只给出单一根;空输入返回空数组,不抛异常。
 */
export function buildDirectoryTree(files: Iterable<File>): DirectoryTreeNode[] {
  const root: DirectoryTreeNode[] = []

  for (const file of files) {
    const segments = relativePathOf(file)
      .split('/')
      .filter((segment) => segment !== '')
    insertPath(root, segments, file.size, '')
  }

  sortTree(root)
  return root
}

/**
 * 由"路径 + 大小"的扁平清单还原目录树。
 *
 * 与 {@link buildDirectoryTree} 的区别只在输入来源:那个吃浏览器的 `File` 对象(上传页选文件夹),
 * 这个吃后端返回的版本文件清单(详情页展开 skill 的完整形态)。两者共用同一套插入与排序逻辑,
 * 因此渲染出来的顺序也一致 —— 上传前预览到的结构,和发布后详情页看到的结构不会长得不一样。
 */
export function buildTreeFromPaths(
  entries: readonly { readonly path: string; readonly sizeBytes: number }[],
): DirectoryTreeNode[] {
  const root: DirectoryTreeNode[] = []

  for (const entry of entries) {
    const segments = entry.path.split('/').filter((segment) => segment !== '')
    insertPath(root, segments, entry.sizeBytes, '')
  }

  sortTree(root)
  return root
}

/** 拍平成校验 / 打包共用的 `PackageEntry[]`,路径取自 `webkitRelativePath`(缺失时退化为文件名)。 */
export function flattenFilesToEntries(files: Iterable<File>): PackageEntry[] {
  const entries: PackageEntry[] = []
  for (const file of files) {
    entries.push({
      path: relativePathOf(file),
      size: file.size,
      toBlob: () => file,
    })
  }
  return entries
}

function relativePathOf(file: File): string {
  // 不能只判断 `!== ''`:DOM 类型声明 `webkitRelativePath` 恒为 string,但 jsdom
  // 压根没实现这个属性,运行时拿到的是 `undefined` 而不是规范里说的空字符串 ——
  // 两种"没有相对路径"的运行时形态都要接住,否则测试环境和真实浏览器行为不一致。
  const relativePath: unknown = file.webkitRelativePath
  return typeof relativePath === 'string' && relativePath !== '' ? relativePath : file.name
}

function insertPath(
  siblings: DirectoryTreeNode[],
  segments: string[],
  size: number,
  parentPath: string,
): void {
  const [name, ...rest] = segments
  if (name === undefined) {
    return
  }
  const path = parentPath === '' ? name : `${parentPath}/${name}`

  if (rest.length === 0) {
    siblings.push({ type: 'file', name, path, size })
    return
  }

  const existing = siblings.find(
    (node): node is Extract<DirectoryTreeNode, { type: 'directory' }> =>
      node.type === 'directory' && node.name === name,
  )
  const directory = existing ?? { type: 'directory' as const, name, path, children: [] }
  if (!existing) {
    siblings.push(directory)
  }
  insertPath(directory.children, rest, size, path)
}

/** 目录优先、同类型按名称排序,保证渲染结果与测试断言都是确定的。 */
function sortTree(nodes: DirectoryTreeNode[]): void {
  nodes.sort(compareNodes)
  for (const node of nodes) {
    if (node.type === 'directory') {
      sortTree(node.children)
    }
  }
}

function compareNodes(a: DirectoryTreeNode, b: DirectoryTreeNode): number {
  if (a.type !== b.type) {
    return a.type === 'directory' ? -1 : 1
  }
  return a.name.localeCompare(b.name)
}
