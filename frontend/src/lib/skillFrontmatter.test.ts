import { describe, expect, it } from 'vitest'
import { parseSkillFrontmatter, SKILL_DESCRIPTION_MAX_LENGTH } from './skillFrontmatter'

function expectViolationCode(result: ReturnType<typeof parseSkillFrontmatter>, code: string): void {
  expect(result.ok).toBe(false)
  if (result.ok) {
    return
  }
  expect(result.violations.some((v) => v.code === code)).toBe(true)
}

describe('parseSkillFrontmatter — 合法输入', () => {
  it('应解析最小合法 frontmatter(仅 name / description)', () => {
    const content = ['---', 'name: code-review', 'description: 代码审查', '---', '', '# 正文'].join(
      '\n',
    )

    const result = parseSkillFrontmatter(content)

    expect(result).toEqual({
      ok: true,
      data: { name: 'code-review', description: '代码审查', allowedTools: [] },
    })
  })

  it('应解析块级列表形式的 allowed-tools', () => {
    const content = [
      '---',
      'name: code-review',
      'description: 代码审查',
      'allowed-tools:',
      '  - Read',
      '  - Grep',
      '---',
    ].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data.allowedTools).toEqual(['Read', 'Grep'])
    }
  })

  it('应解析流式列表形式的 allowed-tools', () => {
    const content = [
      '---',
      'name: code-review',
      'description: 代码审查',
      'allowed-tools: [Read, Grep]',
      '---',
    ].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data.allowedTools).toEqual(['Read', 'Grep'])
    }
  })

  it('应解析逗号分隔标量形式的 allowed-tools', () => {
    const content = [
      '---',
      'name: code-review',
      'description: 代码审查',
      'allowed-tools: Read, Grep',
      '---',
    ].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data.allowedTools).toEqual(['Read', 'Grep'])
    }
  })

  it('缺少 allowed-tools 时应解析为空数组,不产生违规', () => {
    const content = ['---', 'name: code-review', 'description: 代码审查', '---'].join('\n')

    expect(parseSkillFrontmatter(content)).toEqual({
      ok: true,
      data: { name: 'code-review', description: '代码审查', allowedTools: [] },
    })
  })

  it('应去掉标量值两端的引号', () => {
    const content = ['---', 'name: "code-review"', "description: '代码审查'", '---'].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data.name).toBe('code-review')
      expect(result.data.description).toBe('代码审查')
    }
  })

  it('应容忍 CRLF 换行', () => {
    const content = ['---', 'name: code-review', 'description: 代码审查', '---'].join('\r\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
  })

  it('应剥离开头的 BOM', () => {
    const bom = String.fromCharCode(0xfeff)
    const content = `${bom}${['---', 'name: code-review', 'description: 代码审查', '---'].join('\n')}`

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
  })

  it('应忽略 frontmatter 块内的空行与注释行', () => {
    const content = [
      '---',
      '# 这是注释',
      'name: code-review',
      '',
      'description: 代码审查',
      '---',
    ].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
  })
})

describe('parseSkillFrontmatter — 结构非法', () => {
  it('应拒绝不以 --- 开头的内容', () => {
    const content = ['name: code-review', 'description: 代码审查'].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_MISSING_DELIMITER')
  })

  it('应拒绝缺少闭合 --- 的内容', () => {
    const content = ['---', 'name: code-review', 'description: 代码审查'].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_UNTERMINATED')
  })

  it('空文件应报缺少开头分隔符', () => {
    expectViolationCode(parseSkillFrontmatter(''), 'FRONTMATTER_MISSING_DELIMITER')
  })
})

describe('parseSkillFrontmatter — name 非法', () => {
  it('应拒绝缺少 name 字段', () => {
    const content = ['---', 'description: 代码审查', '---'].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_MISSING_NAME')
  })

  it('应拒绝空字符串 name', () => {
    const content = ['---', 'name: ""', 'description: 代码审查', '---'].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_MISSING_NAME')
  })

  it('应拒绝不符合 kebab-case 的 name,并带上具体原因', () => {
    const content = ['---', 'name: Code_Review', 'description: 代码审查', '---'].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(false)
    if (!result.ok) {
      const violation = result.violations.find((v) => v.code === 'FRONTMATTER_NAME_INVALID')
      expect(violation?.message).toContain('kebab-case')
    }
  })

  it('应拒绝写成列表的 name', () => {
    const content = ['---', 'name:', '  - code-review', 'description: 代码审查', '---'].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_NAME_INVALID')
  })
})

describe('parseSkillFrontmatter — description 非法', () => {
  it('应拒绝缺少 description 字段', () => {
    const content = ['---', 'name: code-review', '---'].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_MISSING_DESCRIPTION')
  })

  it(`应拒绝超过 ${SKILL_DESCRIPTION_MAX_LENGTH} 字符上限的 description`, () => {
    const content = [
      '---',
      'name: code-review',
      `description: ${'a'.repeat(SKILL_DESCRIPTION_MAX_LENGTH + 1)}`,
      '---',
    ].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_DESCRIPTION_TOO_LONG')
  })

  it(`应接受恰好达到 ${SKILL_DESCRIPTION_MAX_LENGTH} 字符上限的 description`, () => {
    const description = 'a'.repeat(SKILL_DESCRIPTION_MAX_LENGTH)
    const content = ['---', 'name: code-review', `description: ${description}`, '---'].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(true)
  })

  it('应拒绝写成列表的 description', () => {
    const content = ['---', 'name: code-review', 'description:', '  - a', '  - b', '---'].join('\n')

    expectViolationCode(parseSkillFrontmatter(content), 'FRONTMATTER_DESCRIPTION_INVALID')
  })
})

describe('parseSkillFrontmatter — 多条违规同时收集', () => {
  it('应在一次解析里收集多条独立的违规,不是只报第一条', () => {
    const content = [
      '---',
      `description: ${'a'.repeat(SKILL_DESCRIPTION_MAX_LENGTH + 1)}`,
      '---',
    ].join('\n')

    const result = parseSkillFrontmatter(content)

    expect(result.ok).toBe(false)
    if (!result.ok) {
      const codes = result.violations.map((v) => v.code)
      expect(codes).toContain('FRONTMATTER_MISSING_NAME')
      expect(codes).toContain('FRONTMATTER_DESCRIPTION_TOO_LONG')
    }
  })
})
