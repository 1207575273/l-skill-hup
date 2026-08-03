import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import {
  useInstallCartCount,
  useInstallCartStore,
  useIsSkillInInstallCart,
} from './useInstallCartStore'

const ITEM_A = { skillId: 1, versionId: 10, skillDisplayName: 'PDF 转换' }
const ITEM_B = { skillId: 2, versionId: 20, skillDisplayName: '代码审查' }

describe('useInstallCartStore', () => {
  beforeEach(() => {
    useInstallCartStore.setState({ items: [], client: 'claude', scope: 'global' })
  })

  it('should_be_empty_when_store_initialized', () => {
    expect(useInstallCartStore.getState().items).toEqual([])
    expect(useInstallCartStore.getState().client).toBe('claude')
    expect(useInstallCartStore.getState().scope).toBe('global')
  })

  it('should_add_item_when_addItem_called', () => {
    act(() => useInstallCartStore.getState().addItem(ITEM_A))

    expect(useInstallCartStore.getState().items).toEqual([ITEM_A])
  })

  it('should_not_duplicate_item_when_addItem_called_twice_with_same_skillId', () => {
    act(() => {
      useInstallCartStore.getState().addItem(ITEM_A)
      useInstallCartStore.getState().addItem(ITEM_A)
    })

    expect(useInstallCartStore.getState().items).toHaveLength(1)
  })

  it('should_remove_item_when_removeItem_called', () => {
    act(() => {
      useInstallCartStore.getState().addItem(ITEM_A)
      useInstallCartStore.getState().addItem(ITEM_B)
      useInstallCartStore.getState().removeItem(ITEM_A.skillId)
    })

    expect(useInstallCartStore.getState().items).toEqual([ITEM_B])
  })

  it('should_toggle_item_in_and_out_when_toggleItem_called_twice', () => {
    act(() => useInstallCartStore.getState().toggleItem(ITEM_A))
    expect(useInstallCartStore.getState().items).toEqual([ITEM_A])

    act(() => useInstallCartStore.getState().toggleItem(ITEM_A))
    expect(useInstallCartStore.getState().items).toEqual([])
  })

  it('should_clear_all_items_when_clear_called', () => {
    act(() => {
      useInstallCartStore.getState().addItem(ITEM_A)
      useInstallCartStore.getState().addItem(ITEM_B)
      useInstallCartStore.getState().clear()
    })

    expect(useInstallCartStore.getState().items).toEqual([])
  })

  it('should_update_client_and_scope_when_setters_called', () => {
    act(() => {
      useInstallCartStore.getState().setClient('codex')
      useInstallCartStore.getState().setScope('project')
    })

    expect(useInstallCartStore.getState().client).toBe('codex')
    expect(useInstallCartStore.getState().scope).toBe('project')
  })

  it('should_report_selected_count_when_useInstallCartCount_used', () => {
    const { result } = renderHook(() => useInstallCartCount())
    expect(result.current).toBe(0)

    act(() => useInstallCartStore.getState().addItem(ITEM_A))
    expect(result.current).toBe(1)
  })

  it('should_report_selection_state_when_useIsSkillInInstallCart_used', () => {
    const { result } = renderHook(() => useIsSkillInInstallCart(ITEM_A.skillId))
    expect(result.current).toBe(false)

    act(() => useInstallCartStore.getState().addItem(ITEM_A))
    expect(result.current).toBe(true)
  })
})
