import { describe, expect, it } from 'vitest'
import { cx } from './classNames'

describe('cx', () => {
  it('should_join_truthy_class_names_when_mixed_with_falsy_values', () => {
    expect(cx('a', false, 'b', undefined, null, '', 'c')).toBe('a b c')
  })

  it('should_return_empty_string_when_no_class_names_are_truthy', () => {
    expect(cx(false, undefined, null)).toBe('')
  })
})
