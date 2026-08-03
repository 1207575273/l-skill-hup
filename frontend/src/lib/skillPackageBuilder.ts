/**
 * skill 包打包:三形态归一成 zip(业务建模 5.1)。
 *
 * - 选文件夹 -> JSZip 打包,保留用户原始相对路径,不做任何裁剪 / 改写 ——
 *   规范化、剥垃圾、改写 frontmatter 全部是服务端 5.3 的职责,前端只负责"原样打包"。
 * - 拖入 zip -> 原样透传,**不重新打包**(避免 JSZip 重新编码悄悄改变字节内容)。
 * - 单个 SKILL.md -> 解析它自己的 frontmatter 拿到 `name`,包成 `<name>/SKILL.md`。
 *
 * 三种输入前端一律归一成 zip 再上传,**服务端只认 zip 一种入口**(业务建模 5.1)。
 *
 * **前端校验不是安全边界。** 这里产出的 zip 会被服务端完整重新解压、重新校验、
 * 重新打包(业务建模 5.3),前端做这层归一只是为了让服务端只用维护一条 zip
 * 入口的代码路径。
 */
import JSZip from 'jszip'
import { flattenFilesToEntries } from './directoryTree'
import { parseSkillFrontmatter } from './skillFrontmatter'
import { SKILL_PACKAGE_LIMITS } from './skillPackageLimits'
import type { PackageEntry, PackageViolation } from './skillPackageTypes'

const SKILL_MD_FILE_NAME = 'SKILL.md'
/** zip 本地文件头魔数 "PK\x03\x04"。 */
const ZIP_SIGNATURE = [0x50, 0x4b, 0x03, 0x04]

export type SkillUploadInput =
  | { readonly kind: 'directory'; readonly files: Iterable<File> }
  | { readonly kind: 'zip'; readonly file: File }
  | { readonly kind: 'singleSkillMd'; readonly file: File }

export interface NormalizedSkillPackage {
  readonly blob: Blob
  readonly entries: PackageEntry[]
}

export type NormalizeResult =
  | { readonly ok: true; readonly package: NormalizedSkillPackage }
  | { readonly ok: false; readonly violations: PackageViolation[] }

/** 从条目集合生成 zip(选文件夹、单文件包装两种形态共用)。 */
export async function buildZipFromEntries(entries: readonly PackageEntry[]): Promise<Blob> {
  const zip = new JSZip()
  for (const entry of entries) {
    zip.file(entry.path, entry.toBlob())
  }
  return zip.generateAsync({ type: 'blob' })
}

type ExtractZipResult =
  | { readonly ok: true; readonly entries: PackageEntry[] }
  | { readonly ok: false; readonly violations: PackageViolation[] }

/**
 * 解出 zip 内的条目,用于对"拖入 zip"形态做即时反馈。
 *
 * **路径必须取 `unsafeOriginalName` 而不是 `name`。** JSZip 加载时会对条目名做
 * `utils.resolve()` 归一化 —— 对 `../../../etc/passwd` 这类路径,归一化算法在
 * 空栈上反复 `pop()` 是空操作,`..` 段被悄悄吃掉,结果变成看似人畜无害的
 * `etc/passwd`。`unsafeOriginalName` 保留归一化之前的原始条目名,只有它能让
 * 路径穿越检测(`skillPackagePath.ts` 的 `assessPackagePath`)真正生效。这是
 * 实测 jszip 3.10.1 源码(`lib/load.js` / `lib/utils.js`)得到的结论,不是猜测,
 * 也是本次实现里最值得记住的一个坑。
 *
 * 体积类限制在解压过程中实时判断,一旦超限立即中止并返回已发现的问题,不继续
 * 解压剩余条目 —— 一个精心构造的 zip 不该被完整解压进浏览器内存才发现超限
 * (业务建模 5.2:"这些限制必须在解压过程中实时生效,不能解压完再查")。
 */
export async function extractZipEntries(zipSource: Blob): Promise<ExtractZipResult> {
  let zip: JSZip
  try {
    zip = await JSZip.loadAsync(zipSource)
  } catch (cause) {
    return {
      ok: false,
      violations: [
        {
          code: 'ZIP_PARSE_FAILED',
          message: `zip 解析失败:${cause instanceof Error ? cause.message : String(cause)}`,
        },
      ],
    }
  }

  const fileEntries = Object.values(zip.files).filter((entry) => !entry.dir)
  if (fileEntries.length > SKILL_PACKAGE_LIMITS.MAX_ENTRY_COUNT) {
    return {
      ok: false,
      violations: [
        {
          code: 'ENTRY_COUNT_EXCEEDED',
          message: `zip 内条目数 ${fileEntries.length} 超过上限 ${SKILL_PACKAGE_LIMITS.MAX_ENTRY_COUNT}`,
        },
      ],
    }
  }

  const entries: PackageEntry[] = []
  let unpackedBytes = 0

  for (const fileEntry of fileEntries) {
    const path = fileEntry.unsafeOriginalName ?? fileEntry.name
    const bytes = await fileEntry.async('uint8array')
    unpackedBytes += bytes.length

    if (bytes.length > SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES) {
      return {
        ok: false,
        violations: [
          {
            code: 'SINGLE_FILE_TOO_LARGE',
            message: `文件 ${path} 大小 ${bytes.length} 字节超过单文件上限 ${SKILL_PACKAGE_LIMITS.SINGLE_FILE_MAX_BYTES}`,
            path,
          },
        ],
      }
    }
    if (unpackedBytes > SKILL_PACKAGE_LIMITS.UNPACKED_TOTAL_MAX_BYTES) {
      return {
        ok: false,
        violations: [
          {
            code: 'UNPACKED_TOTAL_TOO_LARGE',
            message: `解压后总大小 ${unpackedBytes} 字节超过上限 ${SKILL_PACKAGE_LIMITS.UNPACKED_TOTAL_MAX_BYTES}`,
          },
        ],
      }
    }

    // `Uint8Array.from` 分配一段新的 ArrayBuffer,满足 BlobPart 要求的 `ArrayBufferView<ArrayBuffer>`——
    // JSZip 的 `async('uint8array')` 返回类型里 buffer 是 `ArrayBufferLike`(含 SharedArrayBuffer 可能性),
    // 与 `Blob` 构造函数的类型不直接兼容。
    const blob = new Blob([Uint8Array.from(bytes)])
    entries.push({ path, size: bytes.length, toBlob: () => blob })
  }

  return { ok: true, entries }
}

/** 读取文件头 4 字节判断是否为 zip 本地文件头魔数,避免把非 zip 文件硬塞进 JSZip 得到一个不知所云的报错。 */
async function looksLikeZip(file: File): Promise<boolean> {
  const header = new Uint8Array(await file.slice(0, 4).arrayBuffer())
  return ZIP_SIGNATURE.every((byte, index) => header.at(index) === byte)
}

async function wrapSingleSkillMdFile(file: File): Promise<NormalizeResult> {
  const text = await file.text()
  const parsed = parseSkillFrontmatter(text)
  if (!parsed.ok) {
    return { ok: false, violations: parsed.violations }
  }

  const entries: PackageEntry[] = [
    { path: `${parsed.data.name}/${SKILL_MD_FILE_NAME}`, size: file.size, toBlob: () => file },
  ]
  const blob = await buildZipFromEntries(entries)
  return { ok: true, package: { blob, entries } }
}

function assertUnreachable(value: never): never {
  throw new Error(`未覆盖的上传输入形态:${JSON.stringify(value)}`)
}

/** 三形态归一的统一入口,细节见文件头部注释。 */
export async function normalizeSkillUploadToZip(input: SkillUploadInput): Promise<NormalizeResult> {
  switch (input.kind) {
    case 'directory': {
      const entries = flattenFilesToEntries(input.files)
      const blob = await buildZipFromEntries(entries)
      return { ok: true, package: { blob, entries } }
    }
    case 'zip': {
      const isZip = await looksLikeZip(input.file)
      if (!isZip) {
        return {
          ok: false,
          violations: [
            {
              code: 'ZIP_SIGNATURE_INVALID',
              message: `${input.file.name} 不是合法的 zip 文件(文件头魔数不匹配)`,
            },
          ],
        }
      }
      const extracted = await extractZipEntries(input.file)
      if (!extracted.ok) {
        return extracted
      }
      // "拖入 zip -> 原样":上传的字节必须是用户拖入的原始文件,不经过 JSZip 重新编码。
      return { ok: true, package: { blob: input.file, entries: extracted.entries } }
    }
    case 'singleSkillMd':
      return wrapSingleSkillMdFile(input.file)
    default:
      return assertUnreachable(input)
  }
}
