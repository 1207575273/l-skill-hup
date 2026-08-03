import JSZip from 'jszip'
import { describe, expect, it } from 'vitest'
import {
  buildZipFromEntries,
  extractZipEntries,
  normalizeSkillUploadToZip,
} from './skillPackageBuilder'
import { createDirectoryFile, createFile, validSkillMd } from './skillPackageFixtures.test-support'
import { SKILL_PACKAGE_LIMITS } from './skillPackageLimits'
import { assessPackagePath } from './skillPackagePath'
import type { PackageEntry } from './skillPackageTypes'

function textEntry(path: string, text: string): PackageEntry {
  const blob = new Blob([text], { type: 'text/plain' })
  return { path, size: blob.size, toBlob: () => blob }
}

describe('buildZipFromEntries', () => {
  it('应生成能被重新解出、内容一致的 zip', async () => {
    const entries = [
      textEntry('demo-skill/SKILL.md', validSkillMd()),
      textEntry('demo-skill/references/a.md', 'hello'),
    ]

    const blob = await buildZipFromEntries(entries)
    const zip = await JSZip.loadAsync(blob)
    const skillMdText = await zip.file('demo-skill/SKILL.md')?.async('string')
    const referenceText = await zip.file('demo-skill/references/a.md')?.async('string')

    expect(skillMdText).toBe(validSkillMd())
    expect(referenceText).toBe('hello')
  })
})

describe('extractZipEntries', () => {
  it('应解出合法 zip 内的全部条目,路径与内容都还原正确', async () => {
    const blob = await buildZipFromEntries([
      textEntry('demo-skill/SKILL.md', validSkillMd()),
      textEntry('demo-skill/references/a.md', 'hello'),
    ])

    const result = await extractZipEntries(blob)

    expect(result.ok).toBe(true)
    if (!result.ok) {
      return
    }
    const paths = result.entries.map((entry) => entry.path).sort()
    expect(paths).toEqual(['demo-skill/SKILL.md', 'demo-skill/references/a.md'])

    const skillMdEntry = result.entries.find((entry) => entry.path === 'demo-skill/SKILL.md')
    const blobBack = await skillMdEntry?.toBlob()
    expect(await blobBack?.text()).toBe(validSkillMd())
  })

  it('非 zip 内容应报 ZIP_PARSE_FAILED', async () => {
    const notAZip = new Blob(['这不是一个 zip 文件'], { type: 'text/plain' })

    const result = await extractZipEntries(notAZip)

    expect(result.ok).toBe(false)
    if (result.ok) {
      return
    }
    expect(result.violations[0]?.code).toBe('ZIP_PARSE_FAILED')
  })

  it('条目数超过上限应报 ENTRY_COUNT_EXCEEDED,不解压任何内容', async () => {
    const entries: PackageEntry[] = Array.from(
      { length: SKILL_PACKAGE_LIMITS.MAX_ENTRY_COUNT + 1 },
      (_, i) => textEntry(`demo-skill/references/f${i}.md`, 'x'),
    )
    const blob = await buildZipFromEntries(entries)

    const result = await extractZipEntries(blob)

    expect(result.ok).toBe(false)
    if (result.ok) {
      return
    }
    expect(result.violations[0]?.code).toBe('ENTRY_COUNT_EXCEEDED')
  })

  it('单个解压后文件超过上限应报 SINGLE_FILE_TOO_LARGE,并在解压过程中及时中止', async () => {
    const oversized = 'a'.repeat(SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES + 1)
    const blob = await buildZipFromEntries([textEntry('demo-skill/assets/big.bin', oversized)])

    const result = await extractZipEntries(blob)

    expect(result.ok).toBe(false)
    if (result.ok) {
      return
    }
    expect(result.violations[0]?.code).toBe('SINGLE_FILE_TOO_LARGE')
  }, 20_000)

  it('解压后总大小超过上限应报 UNPACKED_TOTAL_TOO_LARGE(每个文件本身仍在单文件上限内)', async () => {
    // 4 个各恰好 16MB(= 单文件上限,不超)的块 + 1 个 1 字节的块,
    // 总和 64MB + 1 字节 > 64MB 总量上限,但没有任何一个文件本身超过单文件上限——
    // 用来验证总量检查是独立生效的,不是靠单文件检查顺带覆盖。
    const chunk = 'a'.repeat(SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES)
    const entries = [
      textEntry('demo-skill/assets/0.bin', chunk),
      textEntry('demo-skill/assets/1.bin', chunk),
      textEntry('demo-skill/assets/2.bin', chunk),
      textEntry('demo-skill/assets/3.bin', chunk),
      textEntry('demo-skill/assets/4.bin', 'a'),
    ]
    const blob = await buildZipFromEntries(entries)

    const result = await extractZipEntries(blob)

    expect(result.ok).toBe(false)
    if (result.ok) {
      return
    }
    expect(result.violations[0]?.code).toBe('UNPACKED_TOTAL_TOO_LARGE')
  }, 20_000)

  it('应取 unsafeOriginalName 而不是 JSZip 归一化后的 name,让路径穿越检测不被绕过', async () => {
    // JSZip 加载时会把 "../../../etc/passwd" 归一化成看似无害的 "etc/passwd"
    // (lib/utils.js 的 resolve() 在空栈上反复 pop() 是空操作),.unsafeOriginalName
    // 保留归一化之前的原始条目名。这里直接用 JSZip 写入一个恶意路径的条目——
    // JSZip 的写入路径(object.js 的 fileAdd)不做 resolve,原样写进 zip 的
    // 中央目录,只有"加载"时才会归一化,所以能构造出这个场景。
    const maliciousZip = new JSZip()
    maliciousZip.file('../../../etc/passwd', 'evil')
    const blob = await maliciousZip.generateAsync({ type: 'blob' })

    const result = await extractZipEntries(blob)

    expect(result.ok).toBe(true)
    if (!result.ok) {
      return
    }
    expect(result.entries).toHaveLength(1)
    const entry = result.entries[0]
    expect(entry).toBeDefined()
    if (!entry) {
      return
    }
    expect(entry.path).toBe('../../../etc/passwd')

    // 端到端确认:拿到的路径真的能被路径安全检查拦下,不是只捕获了字符串。
    expect(assessPackagePath(entry.path).some((v) => v.code === 'PATH_TRAVERSAL')).toBe(true)
  })
})

describe('normalizeSkillUploadToZip — 选文件夹形态', () => {
  it('应保留用户原始相对路径,原样打包成 zip', async () => {
    const files = [
      createDirectoryFile('demo-skill/SKILL.md', validSkillMd()),
      createDirectoryFile('demo-skill/references/a.md', 'hello'),
    ]

    const result = await normalizeSkillUploadToZip({ kind: 'directory', files })

    expect(result.ok).toBe(true)
    if (!result.ok) {
      return
    }
    const paths = result.package.entries.map((entry) => entry.path).sort()
    expect(paths).toEqual(['demo-skill/SKILL.md', 'demo-skill/references/a.md'])

    const zip = await JSZip.loadAsync(result.package.blob)
    expect(await zip.file('demo-skill/references/a.md')?.async('string')).toBe('hello')
  })
})

describe('normalizeSkillUploadToZip — 拖入 zip 形态', () => {
  it('应原样透传原始字节,不重新编码', async () => {
    const originalBlob = await buildZipFromEntries([
      textEntry('demo-skill/SKILL.md', validSkillMd()),
    ])
    const zipFile = new File([originalBlob], 'demo-skill.zip', { type: 'application/zip' })

    const result = await normalizeSkillUploadToZip({ kind: 'zip', file: zipFile })

    expect(result.ok).toBe(true)
    if (!result.ok) {
      return
    }
    expect(result.package.blob).toBe(zipFile)
    expect(result.package.entries.map((entry) => entry.path)).toEqual(['demo-skill/SKILL.md'])
  })

  it('文件头魔数不匹配时应报 ZIP_SIGNATURE_INVALID,不尝试解析', async () => {
    const notAZip = createFile('demo-skill.zip', '这不是 zip,只是改了扩展名')

    const result = await normalizeSkillUploadToZip({ kind: 'zip', file: notAZip })

    expect(result.ok).toBe(false)
    if (result.ok) {
      return
    }
    expect(result.violations[0]?.code).toBe('ZIP_SIGNATURE_INVALID')
  })
})

describe('normalizeSkillUploadToZip — 单个 SKILL.md 形态', () => {
  it('应解析自身 frontmatter 的 name,包成 <name>/SKILL.md', async () => {
    const file = createFile('SKILL.md', validSkillMd({ name: 'solo-skill' }))

    const result = await normalizeSkillUploadToZip({ kind: 'singleSkillMd', file })

    expect(result.ok).toBe(true)
    if (!result.ok) {
      return
    }
    expect(result.package.entries.map((entry) => entry.path)).toEqual(['solo-skill/SKILL.md'])

    const zip = await JSZip.loadAsync(result.package.blob)
    expect(await zip.file('solo-skill/SKILL.md')?.async('string')).toBe(
      validSkillMd({ name: 'solo-skill' }),
    )
  })

  it('frontmatter 非法时应直接返回违规,不产出 zip', async () => {
    const file = createFile('SKILL.md', '不是合法 frontmatter')

    const result = await normalizeSkillUploadToZip({ kind: 'singleSkillMd', file })

    expect(result.ok).toBe(false)
    if (result.ok) {
      return
    }
    expect(result.violations.some((v) => v.code === 'FRONTMATTER_MISSING_DELIMITER')).toBe(true)
  })
})
