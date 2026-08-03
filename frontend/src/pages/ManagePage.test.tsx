import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { setAuthToken } from '../api/client'
import * as skillsApi from '../api/skills'
import type { CurrentUser, ManagedSkill, PageResponse } from '../api/types'
import ManagePage from './ManagePage'

vi.mock('../api/auth')
vi.mock('../api/skills')

const mockedFetchCurrentUser = vi.mocked(authApi.fetchCurrentUser)
const mockedFetchManagedSkills = vi.mocked(skillsApi.fetchManagedSkills)
const mockedDeleteSkill = vi.mocked(skillsApi.deleteSkill)

function managedSkill(overrides: Partial<ManagedSkill> = {}): ManagedSkill {
  return {
    id: 1,
    name: 'code-review',
    displayName: '代码审查',
    status: 'PUBLISHED',
    certification: 'NONE',
    downloadCount: 12,
    updatedAt: '2026-08-01T00:00:00Z',
    latestVersionId: 10,
    ...overrides,
  }
}

function page(items: ManagedSkill[]): PageResponse<ManagedSkill> {
  return { items, total: items.length, page: 1, size: 20 }
}

function currentUser(role: CurrentUser['role'] = 'USER'): CurrentUser {
  return {
    id: 2,
    displayName: '张三',
    employeeNo: null,
    email: null,
    avatarUrl: null,
    role,
    identities: [],
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ManagePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ManagePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    setAuthToken(null)
  })

  it('should_guide_to_login_when_not_logged_in', async () => {
    // 未登录时不渲染一张空表格 —— 那会让人以为"我一个 skill 都没有",
    // 而真实情况是根本没查。
    renderPage()

    expect(await screen.findByText('请先登录')).toBeInTheDocument()
    expect(mockedFetchManagedSkills).not.toHaveBeenCalled()
  })

  it('should_list_unlisted_skill_when_logged_in', async () => {
    // 这条是这个页面存在的全部理由:已下架的 skill 在浏览页里查不到,
    // 只有这里能看见并把它恢复回来。
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchManagedSkills.mockResolvedValue(
      page([managedSkill({ status: 'UNLISTED', displayName: '已下架的' })]),
    )

    renderPage()

    expect(await screen.findByText('已下架的')).toBeInTheDocument()
    // 限定在表格里查:筛选按钮也叫"已下架",全局 getByText 会同时命中两个而报"找到多个"。
    expect(within(screen.getByRole('table')).getByText('已下架')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '恢复上架' })).toBeInTheDocument()
  })

  it('should_offer_unlist_instead_of_relist_when_skill_is_published', async () => {
    // 两个动作互斥:同时摆出来会让人以为存在第三种状态。
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchManagedSkills.mockResolvedValue(page([managedSkill()]))

    renderPage()

    expect(await screen.findByRole('button', { name: '下架' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '恢复上架' })).not.toBeInTheDocument()
  })

  it('should_query_only_unlisted_when_status_filter_selected', async () => {
    // 重新上架是这个页面最主要的用途,而下架的 skill 混在全部里不好找。
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchManagedSkills.mockResolvedValue(page([managedSkill()]))

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '已下架' }))

    await waitFor(() => {
      expect(mockedFetchManagedSkills).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'UNLISTED', page: 1 }),
        expect.anything(),
      )
    })
  })

  it('should_not_send_status_when_filter_is_all', async () => {
    // "全部"在后端没有对应枚举值,约定是不传 status。传一个 'ALL' 过去会被状态解析拒掉。
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchManagedSkills.mockResolvedValue(page([managedSkill()]))

    renderPage()

    await waitFor(() => {
      expect(mockedFetchManagedSkills).toHaveBeenCalledWith(
        expect.objectContaining({ status: undefined }),
        expect.anything(),
      )
    })
  })

  it('should_search_only_on_submit_not_on_every_keystroke', async () => {
    // 边打字边发请求既浪费也会让列表闪。提交时才带上关键词。
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchManagedSkills.mockResolvedValue(page([managedSkill()]))

    renderPage()
    fireEvent.change(await screen.findByLabelText('搜索'), { target: { value: '审查' } })

    expect(mockedFetchManagedSkills).not.toHaveBeenCalledWith(
      expect.objectContaining({ keyword: '审查' }),
      expect.anything(),
    )

    fireEvent.click(screen.getByRole('button', { name: '搜索' }))

    await waitFor(() => {
      expect(mockedFetchManagedSkills).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: '审查', page: 1 }),
        expect.anything(),
      )
    })
  })

  it('should_keep_delete_disabled_until_skill_name_typed', async () => {
    // 物理删除不可逆。"再点一次确认"对连续操作的人几乎等于没有,
    // 要求把名字打出来,强制的是"看清楚你选的是哪一行"——那正是误删最常见的成因。
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchManagedSkills.mockResolvedValue(page([managedSkill()]))

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '删除' }))

    expect(screen.getByRole('button', { name: '永久删除' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('输入 code-review 以确认'), {
      target: { value: 'code-revie' },
    })
    expect(screen.getByRole('button', { name: '永久删除' })).toBeDisabled()
  })

  it('should_delete_when_skill_name_matches', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchManagedSkills.mockResolvedValue(page([managedSkill()]))
    mockedDeleteSkill.mockResolvedValue(undefined)

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '删除' }))
    fireEvent.change(screen.getByLabelText('输入 code-review 以确认'), {
      target: { value: 'code-review' },
    })
    fireEvent.click(screen.getByRole('button', { name: '永久删除' }))

    await waitFor(() => {
      expect(mockedDeleteSkill).toHaveBeenCalledWith(1)
    })
  })
})
