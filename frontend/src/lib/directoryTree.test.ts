import { describe, expect, it } from 'vitest'
import { buildDirectoryTree, flattenFilesToEntries } from './directoryTree'
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
