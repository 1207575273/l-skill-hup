import { describe, expect, it } from 'vitest'
import {
  createSizedEntry,
  createTextEntry,
  validSkillMd,
} from './skillPackageFixtures.test-support'
import { SKILL_PACKAGE_LIMITS } from './skillPackageLimits'
import type { PackageEntry } from './skillPackageTypes'
import { validateSkillPackage } from './skillPackageValidator'

describe('validateSkillPackage — 合法包', () => {
  it('应通过一个结构完整的最小合法包', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createTextEntry('demo-skill/references/a.md', 'a'),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(true)
    expect(report.violations).toEqual([])
    expect(report.root).toBe('demo-skill')
    expect(report.parsed).toEqual({
      name: 'demo-skill',
      description: '一个用于测试的示例 skill',
      allowedTools: [],
    })
    expect(report.fileCount).toBe(2)
    expect(report.hasReadme).toBe(false)
  })

  it('SKILL.md 直接躺在根(未选文件夹形态)时 root 应为 null', async () => {
    const entries = [createTextEntry('SKILL.md', validSkillMd())]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(true)
    expect(report.root).toBeNull()
  })

  it('应识别顶层 README.md', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createTextEntry('demo-skill/README.md', '# readme'),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.hasReadme).toBe(true)
  })

  it.each(['references', 'scripts', 'assets'])('应接受白名单目录 %s 下的文件', async (dir) => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createTextEntry(`demo-skill/${dir}/x.md`, 'x'),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(true)
  })

  it('已知的操作系统垃圾文件应被静默剔除,不计入 fileCount 也不产生违规', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createTextEntry('demo-skill/.DS_Store', 'junk'),
      createTextEntry('__MACOSX/demo-skill/._SKILL.md', 'junk'),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(true)
    expect(report.fileCount).toBe(1)
  })
})

describe('validateSkillPackage — 结构违规', () => {
  it('缺少 SKILL.md 应报 SKILL_MD_MISSING', async () => {
    const entries = [createTextEntry('demo-skill/README.md', '# readme')]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'SKILL_MD_MISSING')).toBe(true)
  })

  it('空包应报 SKILL_MD_MISSING', async () => {
    const report = await validateSkillPackage([])

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'SKILL_MD_MISSING')).toBe(true)
  })

  it('真实 skill 常见的顶层文件与目录应当放行', async () => {
    // 这些条目取自 GitHub 上一个真实的 skill 包。它们曾被顶层白名单全部拒掉,
    // 而官方规范只要求 SKILL.md 存在,其余是任意 supporting files。
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createTextEntry('demo-skill/LICENSE', 'MIT'),
      createTextEntry('demo-skill/README.zh-CN.md', '# 中文说明'),
      createTextEntry('demo-skill/docs/readme/hero.png', 'x'),
      createTextEntry('demo-skill/templates/deck.html', '<html></html>'),
      createTextEntry('demo-skill/examples/demo/index.html', '<html></html>'),
      createTextEntry('demo-skill/.clawscan-allow', 'x'),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(true)
    expect(report.violations).toHaveLength(0)
  })

  it('路径穿越条目应报 PATH_TRAVERSAL(整合 skillPackagePath 的判断)', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createTextEntry('demo-skill/references/../../etc/passwd', 'evil'),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'PATH_TRAVERSAL')).toBe(true)
  })

  it('目录深度超过上限应报 DIRECTORY_TOO_DEEP', async () => {
    const deepPath = `demo-skill/references/${Array.from({ length: SKILL_PACKAGE_LIMITS.MAX_DIRECTORY_DEPTH }, (_, i) => `d${i}`).join('/')}/file.md`
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createTextEntry(deepPath, 'x'),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'DIRECTORY_TOO_DEEP')).toBe(true)
  })

  it('SKILL.md 自身 frontmatter 非法时,违规应并入总报告', async () => {
    const entries = [createTextEntry('demo-skill/SKILL.md', '不是合法 frontmatter')]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(false)
    expect(report.parsed).toBeNull()
    expect(report.violations.some((v) => v.code === 'FRONTMATTER_MISSING_DELIMITER')).toBe(true)
  })
})

describe('validateSkillPackage — 体积与数量上限', () => {
  it('条目数超过上限应报 ENTRY_COUNT_EXCEEDED', async () => {
    const skillMd = createTextEntry('demo-skill/SKILL.md', validSkillMd())
    const filler: PackageEntry[] = Array.from(
      { length: SKILL_PACKAGE_LIMITS.MAX_ENTRY_COUNT },
      (_, i) => createSizedEntry(`demo-skill/references/f${i}.md`, 1),
    )

    const report = await validateSkillPackage([skillMd, ...filler])

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'ENTRY_COUNT_EXCEEDED')).toBe(true)
  })

  it('单个文件超过上限应报 SINGLE_FILE_TOO_LARGE', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createSizedEntry('demo-skill/assets/big.bin', SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES + 1),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'SINGLE_FILE_TOO_LARGE')).toBe(true)
  })

  it('恰好达到单文件上限应接受', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createSizedEntry('demo-skill/assets/big.bin', SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.violations.some((v) => v.code === 'SINGLE_FILE_TOO_LARGE')).toBe(false)
  })

  it('解压后总大小超过上限应报 UNPACKED_TOTAL_TOO_LARGE(每个文件本身仍在单文件上限内)', async () => {
    // 5 个各 13MB(< 16MB 单文件上限)的文件,总和 65MB > 64MB 总量上限 ——
    // 只想触发总量检查,不想让单文件检查一起响,两个上限的用例要能独立断言。
    const chunkBytes = 13 * 1024 * 1024
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      ...Array.from({ length: 5 }, (_, i) =>
        createSizedEntry(`demo-skill/assets/${i}.bin`, chunkBytes),
      ),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'UNPACKED_TOTAL_TOO_LARGE')).toBe(true)
    expect(report.violations.some((v) => v.code === 'SINGLE_FILE_TOO_LARGE')).toBe(false)
  })

  it('压缩比超过上限时应报 COMPRESSION_RATIO_TOO_HIGH(需显式提供原始压缩包大小)', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createSizedEntry('demo-skill/assets/a.bin', 1000),
    ]

    const report = await validateSkillPackage(entries, { originalArchiveBytes: 1 })

    expect(report.valid).toBe(false)
    expect(report.violations.some((v) => v.code === 'COMPRESSION_RATIO_TOO_HIGH')).toBe(true)
  })

  it('未提供 originalArchiveBytes 时不做压缩比检查(选文件夹 / 单文件形态)', async () => {
    const entries = [
      createTextEntry('demo-skill/SKILL.md', validSkillMd()),
      createSizedEntry('demo-skill/assets/a.bin', 1000),
    ]

    const report = await validateSkillPackage(entries)

    expect(report.violations.some((v) => v.code === 'COMPRESSION_RATIO_TOO_HIGH')).toBe(false)
  })
})
