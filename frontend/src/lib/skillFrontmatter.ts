/**
 * `SKILL.md` frontmatter 的最小化解析器。
 *
 * 这不是通用 YAML 解析器 —— 只覆盖 Agent Skills frontmatter 实际会用到的三个键
 * (`name` / `description` / `allowed-tools`)以及它们在实践中出现的几种写法
 * (标量、带引号标量、块级列表、流式列表 `[a, b]`、逗号分隔标量)。真要支持任意
 * YAML 应该换一个 YAML 库,但对这三个键来说是杀鸡用牛刀,而且没有额外依赖的收益
 * (frontend/CLAUDE.md:不引任何新的第三方依赖)。已知不支持 YAML 块标量
 * (`description: |` / `description: >`)——Agent Skills 规范里 description 约定
 * 为单行,块标量属于反常写法。
 *
 * **前端校验不是安全边界。** 这里解析出的结果只用于即时反馈,服务端会用同一套
 * 规则完整重做一遍(见 `skillPackageTypes.ts` 头部声明)。
 */
import { validateSkillName } from './skillName'
import type {
  PackageViolation,
  PackageViolationCode,
  SkillFrontmatterData,
} from './skillPackageTypes'

/** description 上限。业务建模 4.8:`skill.description` 收紧为 `VARCHAR(512)`,超长在校验阶段拒绝。 */
export const SKILL_DESCRIPTION_MAX_LENGTH = 512

const FRONTMATTER_DELIMITER = '---'

export type FrontmatterParseResult =
  | { readonly ok: true; readonly data: SkillFrontmatterData }
  | { readonly ok: false; readonly violations: PackageViolation[] }

type FieldValue =
  | { readonly kind: 'scalar'; readonly value: string }
  | { readonly kind: 'list'; readonly value: string[] }

export function parseSkillFrontmatter(rawContent: string): FrontmatterParseResult {
  const lines = stripBom(rawContent).split(/\r\n|\r|\n/)

  if (lines[0]?.trim() !== FRONTMATTER_DELIMITER) {
    return fail('FRONTMATTER_MISSING_DELIMITER', 'SKILL.md 必须以 --- 开头声明 frontmatter')
  }

  const closingIndex = lines.findIndex(
    (line, index) => index > 0 && line.trim() === FRONTMATTER_DELIMITER,
  )
  if (closingIndex === -1) {
    return fail('FRONTMATTER_UNTERMINATED', 'frontmatter 缺少闭合的 ---,未找到结束分隔符')
  }

  const fields = parseTopLevelFields(lines.slice(1, closingIndex))
  const violations: PackageViolation[] = []

  const name = readScalarField(fields, 'name', violations, {
    missingCode: 'FRONTMATTER_MISSING_NAME',
    missingMessage: 'frontmatter 缺少必填字段 name',
    invalidCode: 'FRONTMATTER_NAME_INVALID',
    invalidTypeMessage: 'name 必须是单个字符串,不能写成列表',
  })
  if (name !== null) {
    const nameCheck = validateSkillName(name)
    if (!nameCheck.valid) {
      violations.push({
        code: 'FRONTMATTER_NAME_INVALID',
        message: `name 不合法:${nameCheck.reason}`,
      })
    }
  }

  const description = readScalarField(fields, 'description', violations, {
    missingCode: 'FRONTMATTER_MISSING_DESCRIPTION',
    missingMessage: 'frontmatter 缺少必填字段 description',
    invalidCode: 'FRONTMATTER_DESCRIPTION_INVALID',
    invalidTypeMessage: 'description 必须是单个字符串,不能写成列表',
  })
  if (description !== null && description.length > SKILL_DESCRIPTION_MAX_LENGTH) {
    violations.push({
      code: 'FRONTMATTER_DESCRIPTION_TOO_LONG',
      message: `description 长度 ${description.length} 超过上限 ${SKILL_DESCRIPTION_MAX_LENGTH}`,
    })
  }

  const allowedTools = readAllowedTools(fields)

  if (violations.length > 0 || name === null || description === null) {
    return { ok: false, violations }
  }
  return { ok: true, data: { name, description, allowedTools } }
}

function fail(code: PackageViolationCode, message: string): FrontmatterParseResult {
  return { ok: false, violations: [{ code, message }] }
}

function stripBom(text: string): string {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text
}

interface ScalarFieldSpec {
  readonly missingCode: PackageViolationCode
  readonly missingMessage: string
  readonly invalidCode: PackageViolationCode
  readonly invalidTypeMessage: string
}

function readScalarField(
  fields: ReadonlyMap<string, FieldValue>,
  key: string,
  violations: PackageViolation[],
  spec: ScalarFieldSpec,
): string | null {
  const field = fields.get(key)
  if (!field) {
    violations.push({ code: spec.missingCode, message: spec.missingMessage })
    return null
  }
  if (field.kind === 'list') {
    violations.push({ code: spec.invalidCode, message: spec.invalidTypeMessage })
    return null
  }
  if (field.value.trim() === '') {
    violations.push({ code: spec.missingCode, message: spec.missingMessage })
    return null
  }
  return field.value
}

/** `allowed-tools` 可选:key 不存在时视为空数组(未声明限制),不产生违规。 */
function readAllowedTools(fields: ReadonlyMap<string, FieldValue>): string[] {
  const field = fields.get('allowed-tools')
  if (!field) {
    return []
  }
  if (field.kind === 'list') {
    return field.value.filter((item) => item !== '')
  }
  return field.value
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item !== '')
}

/**
 * 解析 frontmatter 块里的顶层键值。支持:
 * - 单行标量:`name: foo` / `name: "foo"`
 * - 流式列表:`allowed-tools: [Read, Grep]`
 * - 块级列表:`allowed-tools:` 后跟若干 `  - Read` 行
 *
 * 只识别顶层键(正则要求行首无缩进),缩进的非列表行(如嵌套映射)一律忽略 ——
 * 这三个键在 Agent Skills 规范里都不需要嵌套结构。
 */
function parseTopLevelFields(block: readonly string[]): Map<string, FieldValue> {
  const fields = new Map<string, FieldValue>()
  let pendingKey: string | null = null
  let pendingList: string[] | null = null

  const flushPendingList = () => {
    if (pendingKey !== null && pendingList !== null) {
      fields.set(pendingKey, { kind: 'list', value: pendingList })
    }
    pendingKey = null
    pendingList = null
  }

  for (const rawLine of block) {
    const line = rawLine.trimEnd()
    const trimmed = line.trim()
    if (trimmed === '' || trimmed.startsWith('#')) {
      continue
    }

    const listItemMatch = pendingKey !== null ? /^\s*-\s?(.*)$/.exec(line) : null
    if (listItemMatch) {
      pendingList ??= []
      pendingList.push(unquote(listItemMatch[1] ?? ''))
      continue
    }

    const keyMatch = /^([A-Za-z0-9_-]+):\s*(.*)$/.exec(line)
    if (!keyMatch) {
      continue
    }

    flushPendingList()
    const key = keyMatch[1] ?? ''
    const rest = (keyMatch[2] ?? '').trim()

    if (rest === '') {
      pendingKey = key
      pendingList = null
      continue
    }

    if (rest.startsWith('[') && rest.endsWith(']')) {
      const items = rest
        .slice(1, -1)
        .split(',')
        .map((item) => unquote(item.trim()))
        .filter((item) => item !== '')
      fields.set(key, { kind: 'list', value: items })
      continue
    }

    fields.set(key, { kind: 'scalar', value: unquote(rest) })
  }

  flushPendingList()
  return fields
}

function unquote(value: string): string {
  const trimmed = value.trim()
  const first = trimmed.charAt(0)
  const last = trimmed.charAt(trimmed.length - 1)
  if (trimmed.length >= 2 && ((first === '"' && last === '"') || (first === "'" && last === "'"))) {
    return trimmed.slice(1, -1)
  }
  return trimmed
}
