import { describe, expect, it } from 'vitest'
import { SKILL_NAME_MAX_LENGTH, validateSkillName } from './skillName'

describe('validateSkillName', () => {
  it.each(['a', 'pdf', 'skill-hub', 'docx-to-md', 'v2-parser', 'a1-b2-c3', '123'])(
    '应接受合法 kebab-case:%s',
    (value) => {
      expect(validateSkillName(value)).toEqual({ valid: true })
    },
  )

  it.each([null, undefined, '', '   ', '\t'])('应拒绝空值:%s', (value) => {
    const result = validateSkillName(value)

    expect(result.valid).toBe(false)
    if (!result.valid) {
      expect(result.reason).toContain('不能为空')
    }
  })

  it.each([
    'PDF',
    'skill_hub',
    'skill hub',
    '-skill',
    'skill-',
    'skill--hub',
    'skill.hub',
    'skill@hub',
    '技能',
  ])('应拒绝不符合 kebab-case 的值:%s', (value) => {
    const result = validateSkillName(value)

    expect(result.valid).toBe(false)
    if (!result.valid) {
      expect(result.reason).toContain('kebab-case')
    }
  })

  it.each(['../etc/passwd', '..', 'a/../b', '/absolute/path', 'C:\\windows', 'a/b', 'a\\b'])(
    '应拒绝路径穿越样式(名称参与路径拼接,这是安全边界不是命名规范):%s',
    (value) => {
      expect(validateSkillName(value).valid).toBe(false)
    },
  )

  it('应拒绝超长名称', () => {
    const result = validateSkillName('a'.repeat(SKILL_NAME_MAX_LENGTH + 1))

    expect(result.valid).toBe(false)
    if (!result.valid) {
      expect(result.reason).toContain('长度')
    }
  })

  it('应接受恰好达到长度上限的名称', () => {
    expect(validateSkillName('a'.repeat(SKILL_NAME_MAX_LENGTH))).toEqual({ valid: true })
  })
})
