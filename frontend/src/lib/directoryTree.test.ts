import { describe, expect, it } from 'vitest'
import { buildDirectoryTree, buildTreeFromPaths, flattenFilesToEntries } from './directoryTree'
import { createDirectoryFile, createFile } from './skillPackageFixtures.test-support'

describe('buildDirectoryTree', () => {
  it('空输入应返回空数组,不抛异常', () => {
    expect(buildDirectoryTree([])).toEqual([])
  })

  it('应把扁平文件集合还原成嵌套目录树', () => {
    const files = [
      createDirectoryFile('demo-skill/SKILL.md', '# demo'),
      createDirectoryFile('demo-skill/references/a.md', 'a'),
    ]

    const tree = buildDirectoryTree(files)

    expect(tree).toEqual([
      {
        type: 'directory',
        name: 'demo-skill',
        path: 'demo-skill',
        children: [
          {
            type: 'directory',
            name: 'references',
            path: 'demo-skill/references',
            children: [{ type: 'file', name: 'a.md', path: 'demo-skill/references/a.md', size: 1 }],
          },
          { type: 'file', name: 'SKILL.md', path: 'demo-skill/SKILL.md', size: 6 },
        ],
      },
    ])
  })

  it('同级节点应目录在前、文件在后,且各自按名称排序', () => {
    const files = [
      createDirectoryFile('demo-skill/scripts/run.sh', 'x'),
      createDirectoryFile('demo-skill/README.md', 'r'),
      createDirectoryFile('demo-skill/assets/logo.png', 'p'),
      createDirectoryFile('demo-skill/SKILL.md', 's'),
    ]

    const tree = buildDirectoryTree(files)
    const root = tree[0]
    expect(root?.type).toBe('directory')
    const names = root?.type === 'directory' ? root.children.map((child) => child.name) : []

    expect(names).toEqual(['assets', 'scripts', 'README.md', 'SKILL.md'])
  })

  it('缺失 webkitRelativePath 时应退化为使用文件名', () => {
    const file = createFile('SKILL.md', '# demo')

    const tree = buildDirectoryTree([file])

    expect(tree).toEqual([{ type: 'file', name: 'SKILL.md', path: 'SKILL.md', size: 6 }])
  })
})

describe('flattenFilesToEntries', () => {
  it('应按 webkitRelativePath 生成条目,size 与原文件一致', () => {
    const files = [
      createDirectoryFile('demo-skill/SKILL.md', '# demo'),
      createDirectoryFile('demo-skill/references/a.md', 'a'),
    ]

    const entries = flattenFilesToEntries(files)

    expect(entries).toEqual([
      { path: 'demo-skill/SKILL.md', size: 6, toBlob: expect.any(Function) },
      { path: 'demo-skill/references/a.md', size: 1, toBlob: expect.any(Function) },
    ])
  })

  it('toBlob 应能取回原始文件内容', async () => {
    const files = [createDirectoryFile('demo-skill/SKILL.md', '# demo content')]

    const [entry] = flattenFilesToEntries(files)
    const blob = await entry?.toBlob()
    const text = await blob?.text()

    expect(text).toBe('# demo content')
  })

  it('空输入应返回空数组', () => {
    expect(flattenFilesToEntries([])).toEqual([])
  })
})

describe('buildTreeFromPaths', () => {
  it('should_group_files_under_their_directories_when_given_flat_paths', () => {
    const tree = buildTreeFromPaths([
      { path: 'SKILL.md', sizeBytes: 100 },
      { path: 'references/guide.md', sizeBytes: 200 },
      { path: 'references/deep/inner.md', sizeBytes: 50 },
      { path: 'scripts/run.sh', sizeBytes: 30 },
    ])

    // 目录优先、同类型按名称排序,与 buildDirectoryTree 共用同一套排序
    expect(tree.map((node) => node.name)).toEqual(['references', 'scripts', 'SKILL.md'])

    const references = tree[0]
    expect(references?.type).toBe('directory')
    if (references?.type !== 'directory') {
      throw new Error('references 应当是目录节点')
    }
    expect(references.children.map((node) => node.name)).toEqual(['deep', 'guide.md'])
  })

  it('should_return_empty_array_when_manifest_is_empty', () => {
    // 存量版本没有清单信息时后端返回 [],这里不该抛异常
    expect(buildTreeFromPaths([])).toEqual([])
  })

  it('should_keep_file_size_on_leaf_nodes', () => {
    const tree = buildTreeFromPaths([{ path: 'a/b.md', sizeBytes: 4096 }])

    const directory = tree[0]
    if (directory?.type !== 'directory') {
      throw new Error('a 应当是目录节点')
    }
    const file = directory.children[0]
    expect(file).toEqual({ type: 'file', name: 'b.md', path: 'a/b.md', size: 4096 })
  })
})
