import { describe, expect, it } from 'vitest'
import type { SkillTag } from '../api/types'
import {
  formatFileSize,
  formatSkillOwnerLabel,
  parseFreeTagNamesInput,
  parseSkillIdParam,
  sortSkillTagsForDisplay,
} from './skillDetailFormat'

describe('parseSkillIdParam', () => {
  it('should_return_number_when_param_is_positive_integer_string', () => {
    expect(parseSkillIdParam('42')).toBe(42)
  })

  it('should_return_null_when_param_is_undefined', () => {
    expect(parseSkillIdParam(undefined)).toBeNull()
  })

  it('should_return_null_when_param_is_not_numeric', () => {
    expect(parseSkillIdParam('abc')).toBeNull()
  })

  it('should_return_null_when_param_is_zero_or_negative', () => {
    expect(parseSkillIdParam('0')).toBeNull()
    expect(parseSkillIdParam('-1')).toBeNull()
  })

  it('should_return_null_when_param_is_not_an_integer', () => {
    expect(parseSkillIdParam('1.5')).toBeNull()
  })
})

describe('formatSkillOwnerLabel', () => {
  it('should_include_employee_no_when_owner_has_one', () => {
    expect(formatSkillOwnerLabel({ userId: 1, displayName: '张三', employeeNo: 'E1024' })).toBe(
      '@张三(E1024)',
    )
  })

  it('should_omit_employee_no_when_owner_has_none', () => {
    expect(formatSkillOwnerLabel({ userId: 1, displayName: '张三', employeeNo: null })).toBe(
      '@张三',
    )
  })

  it('should_degrade_to_unknown_owner_when_owner_is_null', () => {
    expect(formatSkillOwnerLabel(null)).toBe('@归属未知')
  })
})

describe('formatFileSize', () => {
  it('should_return_zero_bytes_when_size_is_zero', () => {
    expect(formatFileSize(0)).toBe('0 B')
  })

  it('should_format_bytes_without_decimals_when_below_1024', () => {
    expect(formatFileSize(512)).toBe('512 B')
  })

  it('should_format_kilobytes_with_two_decimals_when_at_least_1024', () => {
    expect(formatFileSize(2048)).toBe('2.00 KB')
  })

  it('should_format_megabytes_when_size_large_enough', () => {
    expect(formatFileSize(5 * 1024 * 1024)).toBe('5.00 MB')
  })
})

describe('sortSkillTagsForDisplay', () => {
  const tags: SkillTag[] = [
    { id: 2, name: 'pdf', kind: 'FREE', description: null, sortOrder: 2 },
    { id: 1, name: '效率工具', kind: 'CURATED', description: null, sortOrder: 10 },
    { id: 3, name: '文档', kind: 'FREE', description: null, sortOrder: 1 },
  ]

  it('should_put_curated_tags_before_free_tags_when_sorted', () => {
    const sorted = sortSkillTagsForDisplay(tags)
    expect(sorted.map((tag) => tag.name)).toEqual(['效率工具', '文档', 'pdf'])
  })

  it('should_not_mutate_input_array_when_sorting', () => {
    const original = [...tags]
    sortSkillTagsForDisplay(tags)
    expect(tags).toEqual(original)
  })
})

describe('parseFreeTagNamesInput', () => {
  it('should_split_by_comma_and_trim_when_parsing', () => {
    expect(parseFreeTagNamesInput('pdf, 文档 ,摘要')).toEqual(['pdf', '文档', '摘要'])
  })

  it('should_split_by_chinese_comma_when_parsing', () => {
    // 用 ， 转义全角逗号(而不是直接敲字符),避免源文件里全角/半角标点被工具链归一化。
    const fullwidthComma = '，'
    const input = `pdf${fullwidthComma}文档${fullwidthComma}摘要`
    expect(parseFreeTagNamesInput(input)).toEqual(['pdf', '文档', '摘要'])
  })

  it('should_deduplicate_names_when_parsing', () => {
    expect(parseFreeTagNamesInput('pdf, pdf, 文档')).toEqual(['pdf', '文档'])
  })

  it('should_drop_empty_segments_when_parsing', () => {
    expect(parseFreeTagNamesInput('pdf,, 文档,')).toEqual(['pdf', '文档'])
  })

  it('should_return_empty_array_when_input_is_blank', () => {
    expect(parseFreeTagNamesInput('   ')).toEqual([])
  })
})
