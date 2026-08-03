import { describe, expect, it } from 'vitest'
import { assessPackagePath, pathDepth, resolvePackageRoot, stripRoot } from './skillPackagePath'

// 控制字符 / 反斜杠一律用 String.fromCharCode 现造,不在源码里直接写转义序列——
// 避免测试文件本身混进不可见字节,读起来也更明确写的是哪个字符。
const NUL = String.fromCharCode(0)
const BACKSLASH = String.fromCharCode(92)

describe('assessPackagePath', () => {
  it('应接受合法的相对路径,不产生任何违规', () => {
    expect(assessPackagePath('demo-skill/SKILL.md')).toEqual([])
  })

  it.each(['../etc/passwd', 'a/../b', 'a/b/..', '..'])('应拒绝包含 .. 路径段的路径:%s', (path) => {
    const violations = assessPackagePath(path)
    expect(violations.some((v) => v.code === 'PATH_TRAVERSAL')).toBe(true)
  })

  it('不应误伤文件名里含连续点号的合法名字(.. 是子串不是独立路径段)', () => {
    const violations = assessPackagePath('demo-skill/references/foo..bar.md')
    expect(violations.some((v) => v.code === 'PATH_TRAVERSAL')).toBe(false)
  })

  it('应拒绝以 / 开头的绝对路径', () => {
    const violations = assessPackagePath('/etc/passwd')
    expect(violations.some((v) => v.code === 'PATH_ABSOLUTE')).toBe(true)
  })

  it('应拒绝 Windows 盘符绝对路径', () => {
    const path = `C:${BACKSLASH}windows${BACKSLASH}system32`
    const violations = assessPackagePath(path)
    expect(violations.some((v) => v.code === 'PATH_ABSOLUTE')).toBe(true)
  })

  it('应拒绝 UNC 绝对路径', () => {
    const path = `${BACKSLASH}${BACKSLASH}server${BACKSLASH}share`
    const violations = assessPackagePath(path)
    expect(violations.some((v) => v.code === 'PATH_ABSOLUTE')).toBe(true)
  })

  it('应拒绝包含控制字符的路径', () => {
    const violations = assessPackagePath(`demo-skill/SKILL${NUL}.md`)
    expect(violations.some((v) => v.code === 'PATH_CONTROL_CHAR')).toBe(true)
  })

  it('应拒绝超过长度上限的路径', () => {
    const longPath = `demo-skill/references/${'a'.repeat(250)}.md`
    expect(longPath.length).toBeGreaterThan(255)
    const violations = assessPackagePath(longPath)
    expect(violations.some((v) => v.code === 'PATH_TOO_LONG')).toBe(true)
  })

  it('应接受恰好达到长度上限(255)的路径', () => {
    const path = `a${'a'.repeat(253)}b` // 255 个字符
    expect(path.length).toBe(255)
    const violations = assessPackagePath(path)
    expect(violations.some((v) => v.code === 'PATH_TOO_LONG')).toBe(false)
  })

  it('单条违规应同时给出机器可读的 code 与人可读的 message', () => {
    const violations = assessPackagePath('../evil')
    const traversal = violations.find((v) => v.code === 'PATH_TRAVERSAL')
    expect(traversal?.message).toContain('..')
    expect(traversal?.path).toBe('../evil')
  })
})

describe('resolvePackageRoot', () => {
  it('应识别所有路径共享的顶层包裹目录', () => {
    expect(resolvePackageRoot(['demo-skill/SKILL.md', 'demo-skill/references/a.md'])).toBe(
      'demo-skill',
    )
  })

  it('存在直接躺在根的条目时应判定没有包裹目录', () => {
    expect(resolvePackageRoot(['SKILL.md', 'references/a.md'])).toBeNull()
  })

  it('顶层目录名不一致时应判定没有包裹目录', () => {
    expect(resolvePackageRoot(['foo/SKILL.md', 'bar/README.md'])).toBeNull()
  })

  it('空输入应返回 null', () => {
    expect(resolvePackageRoot([])).toBeNull()
  })
})

describe('stripRoot', () => {
  it('root 非空时应去掉包裹目录前缀', () => {
    expect(stripRoot('demo-skill/SKILL.md', 'demo-skill')).toBe('SKILL.md')
  })

  it('root 为 null 时应原样返回', () => {
    expect(stripRoot('SKILL.md', null)).toBe('SKILL.md')
  })

  it('路径不以给定 root 为前缀时应原样返回(防御性场景)', () => {
    expect(stripRoot('other/SKILL.md', 'demo-skill')).toBe('other/SKILL.md')
  })
})

describe('pathDepth', () => {
  it('顶层文件深度应为 0', () => {
    expect(pathDepth('SKILL.md')).toBe(0)
  })

  it('嵌套路径应按目录段数计算深度,不含文件名本身', () => {
    expect(pathDepth('references/a/b/c/file.md')).toBe(4)
  })
})
