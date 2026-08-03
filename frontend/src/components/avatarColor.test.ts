import { describe, expect, it } from 'vitest'
import { pickAvatarColor } from './avatarColor'

describe('pickAvatarColor', () => {
  it('should_return_same_color_when_called_twice_with_same_name', () => {
    expect(pickAvatarColor('pdf-summarizer')).toBe(pickAvatarColor('pdf-summarizer'))
  })

  it('should_return_a_css_variable_reference_when_given_any_name', () => {
    expect(pickAvatarColor('code-review')).toMatch(/^var\(--color-avatar-\d\)$/)
  })

  it('should_return_different_colors_when_names_differ', () => {
    // 不要求全局无冲突(调色板只有 8 色),但常见的几个不同名字应该落在不同格子上,
    // 证明确实按内容分布而不是恒定返回同一个值。
    const colors = new Set(
      ['code-review', 'pdf-summarizer', 'excel-formula', 'log-analyzer'].map(pickAvatarColor),
    )
    expect(colors.size).toBeGreaterThan(1)
  })
})
