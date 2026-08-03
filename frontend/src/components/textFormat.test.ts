import { describe, expect, it } from 'vitest'
import { formatDateOnly, truncateWithEllipsis } from './textFormat'

describe('truncateWithEllipsis', () => {
  it('should_return_original_text_when_text_not_longer_than_max_length', () => {
    expect(truncateWithEllipsis('短摘要', 100)).toBe('短摘要')
  })

  it('should_truncate_and_append_ellipsis_when_text_longer_than_max_length', () => {
    const text = 'a'.repeat(120)

    const result = truncateWithEllipsis(text, 100)

    expect(result).toBe(`${'a'.repeat(100)}…`)
    expect(result).toHaveLength(101)
  })
})

describe('formatDateOnly', () => {
  it('should_format_iso_datetime_to_date_only_when_input_valid', () => {
    expect(formatDateOnly('2026-08-02T10:30:00Z')).toBe('2026-08-02')
  })

  it('should_return_original_string_when_input_not_a_valid_date', () => {
    expect(formatDateOnly('not-a-date')).toBe('not-a-date')
  })
})
