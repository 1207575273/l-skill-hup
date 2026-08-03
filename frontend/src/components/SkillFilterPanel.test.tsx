import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Tag } from '../api/types'
import { SkillFilterPanel, type SkillFilterPanelProps } from './SkillFilterPanel'

const CURATED_TAGS: Tag[] = [
  { id: 1, name: '代码审查', kind: 'CURATED', description: null, sortOrder: 1, skillCount: 2 },
]
const FREE_TAGS: Tag[] = [
  { id: 2, name: 'pdf', kind: 'FREE', description: null, sortOrder: 1, skillCount: 5 },
]

function renderPanel(overrides: Partial<SkillFilterPanelProps> = {}) {
  const props: SkillFilterPanelProps = {
    keyword: '',
    source: '',
    sort: 'latest',
    certifiedOnly: false,
    curatedTags: CURATED_TAGS,
    freeTags: FREE_TAGS,
    selectedTagIds: [],
    onKeywordChange: vi.fn(),
    onSourceChange: vi.fn(),
    onSortChange: vi.fn(),
    onCertifiedOnlyChange: vi.fn(),
    onToggleTag: vi.fn(),
    onReset: vi.fn(),
    ...overrides,
  }
  render(<SkillFilterPanel {...props} />)
  return props
}

describe('SkillFilterPanel', () => {
  it('should_call_onKeywordChange_when_keyword_input_changed', () => {
    const props = renderPanel()

    fireEvent.change(screen.getByLabelText('关键词'), { target: { value: 'pdf' } })

    expect(props.onKeywordChange).toHaveBeenCalledWith('pdf')
  })

  it('should_call_onSourceChange_when_source_selected', () => {
    const props = renderPanel()

    fireEvent.change(screen.getByLabelText('来源'), { target: { value: 'INTERNAL' } })

    expect(props.onSourceChange).toHaveBeenCalledWith('INTERNAL')
  })

  it('should_call_onCertifiedOnlyChange_when_checkbox_toggled', () => {
    const props = renderPanel()

    fireEvent.click(screen.getByRole('checkbox', { name: '仅看已认证' }))

    expect(props.onCertifiedOnlyChange).toHaveBeenCalledWith(true)
  })

  it('should_call_onToggleTag_when_tag_chip_clicked', () => {
    const props = renderPanel()

    fireEvent.click(screen.getByRole('button', { name: '代码审查' }))

    expect(props.onToggleTag).toHaveBeenCalledWith(1)
  })

  it('should_hide_reset_button_when_no_filter_is_active', () => {
    renderPanel()

    expect(screen.queryByRole('button', { name: '清空筛选条件' })).not.toBeInTheDocument()
  })

  it('should_show_reset_button_when_keyword_is_set', () => {
    renderPanel({ keyword: 'pdf' })

    expect(screen.getByRole('button', { name: '清空筛选条件' })).toBeInTheDocument()
  })

  it('should_call_onReset_when_reset_button_clicked', () => {
    const props = renderPanel({ certifiedOnly: true })

    fireEvent.click(screen.getByRole('button', { name: '清空筛选条件' }))

    expect(props.onReset).toHaveBeenCalled()
  })
})
