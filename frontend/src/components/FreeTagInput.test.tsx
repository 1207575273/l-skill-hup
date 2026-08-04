import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as tagsApi from '../api/tags'
import type { Tag } from '../api/types'
import { createQueryWrapper, createTestQueryClient } from '../hooks/queryTestUtils.test-support'
import { currentSegment, FreeTagInput, replaceCurrentSegment } from './FreeTagInput'

vi.mock('../api/tags')

const mockedSuggestTags = vi.mocked(tagsApi.suggestTags)

function tag(id: number, name: string): Tag {
  return { id, name, kind: 'FREE', description: null, sortOrder: 0, skillCount: 2 }
}

function renderInput(value: string) {
  const onChange = vi.fn()
  const queryClient = createTestQueryClient()
  render(<FreeTagInput value={value} onChange={onChange} maxTags={10} />, {
    wrapper: createQueryWrapper(queryClient),
  })
  return onChange
}

describe('currentSegment', () => {
  it('should_return_whole_value_when_no_separator', () => {
    expect(currentSegment('code')).toBe('code')
  })

  it('should_return_last_part_when_separated', () => {
    expect(currentSegment('java, spr')).toBe('spr')
  })

  /** 中文输入法打出来的是全角逗号。只按半角切会把一整串当成一个标签,联想直接失灵。 */
  it('should_treat_fullwidth_comma_as_separator', () => {
    expect(currentSegment('测试,性能')).toBe('性能')
  })

  it('should_return_empty_when_value_ends_with_separator', () => {
    expect(currentSegment('java, ')).toBe('')
  })
})

describe('replaceCurrentSegment', () => {
  it('should_replace_only_the_last_segment', () => {
    expect(replaceCurrentSegment('java, spr', 'spring')).toBe('java, spring, ')
  })

  it('should_append_trailing_separator_so_user_can_keep_typing', () => {
    expect(replaceCurrentSegment('spr', 'spring')).toBe('spring, ')
  })
})

describe('FreeTagInput', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedSuggestTags.mockResolvedValue([])
  })

  it('should_not_suggest_when_segment_too_short', async () => {
    renderInput('a')

    await waitFor(() => expect(mockedSuggestTags).not.toHaveBeenCalled())
  })

  it('should_suggest_for_last_segment_only', async () => {
    mockedSuggestTags.mockResolvedValue([tag(1, 'spring')])

    renderInput('java, spr')

    await waitFor(() => expect(mockedSuggestTags).toHaveBeenCalledWith('spr', undefined))
    expect(await screen.findByRole('button', { name: /spring/ })).toBeInTheDocument()
  })

  it('should_replace_segment_when_suggestion_clicked', async () => {
    mockedSuggestTags.mockResolvedValue([tag(1, 'spring')])
    const onChange = renderInput('java, spr')

    fireEvent.click(await screen.findByRole('button', { name: /spring/ }))

    expect(onChange).toHaveBeenCalledWith('java, spring, ')
  })

  /** 推一个用户刚打完的词条是那行按钮里最没用的一个。 */
  it('should_hide_suggestion_that_exactly_matches_what_is_typed', async () => {
    mockedSuggestTags.mockResolvedValue([tag(1, 'spring')])

    renderInput('spring')

    await waitFor(() => expect(mockedSuggestTags).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /spring/ })).not.toBeInTheDocument()
  })
})
