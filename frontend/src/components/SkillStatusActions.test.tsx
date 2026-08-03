import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ManagedSkill } from '../api/types'
import { SkillStatusActions } from './SkillStatusActions'

vi.mock('../api/skills')

function renderActions(status: ManagedSkill['status']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <SkillStatusActions skillId={1} status={status} />
    </QueryClientProvider>,
  )
}

describe('SkillStatusActions', () => {
  it('should_offer_relist_when_skill_is_unlisted', () => {
    renderActions('UNLISTED')

    expect(screen.getByRole('button', { name: '恢复上架' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '下架' })).not.toBeInTheDocument()
  })

  it('should_offer_unlist_when_skill_is_published', () => {
    renderActions('PUBLISHED')

    expect(screen.getByRole('button', { name: '下架' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '恢复上架' })).not.toBeInTheDocument()
  })

  it('should_render_nothing_when_status_is_unknown', () => {
    // 这条盯的是一个真实踩过的坑:服务端返回的 DTO 一度没有 status 字段,前端拿到 undefined,
    // 而当时的写法是 `if (UNLISTED) 恢复上架; else 下架` —— 结果**已下架的行也显示"下架"**。
    //
    // 按钮看起来完全正常,只是做的事跟用户要的相反,界面上没有任何异常迹象。少一个按钮能被一眼看见,
    // 反向按钮不能。所以未知状态一律不渲染。
    //
    // 用 as 绕过类型:类型系统挡得住我们自己写错,挡不住服务端少发一个字段 —— 而那正是这条要覆盖的情况。
    renderActions(undefined as unknown as ManagedSkill['status'])

    expect(screen.queryByRole('button', { name: '下架' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '恢复上架' })).not.toBeInTheDocument()
  })
})
