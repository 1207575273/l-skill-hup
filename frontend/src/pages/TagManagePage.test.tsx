import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { ApiError, setAuthToken } from '../api/client'
import * as tagsApi from '../api/tags'
import type { CurrentUser, Tag } from '../api/types'
import TagManagePage from './TagManagePage'

vi.mock('../api/auth')
vi.mock('../api/tags')

const mockedFetchCurrentUser = vi.mocked(authApi.fetchCurrentUser)
const mockedFetchTags = vi.mocked(tagsApi.fetchTags)
const mockedCreateTag = vi.mocked(tagsApi.createTag)
const mockedUpdateTag = vi.mocked(tagsApi.updateTag)
const mockedDeleteTag = vi.mocked(tagsApi.deleteTag)

function tag(overrides: Partial<Tag> = {}): Tag {
  return {
    id: 1,
    name: '代码审查',
    kind: 'CURATED',
    description: '评审清单',
    sortOrder: 20,
    skillCount: 3,
    ...overrides,
  }
}

function currentUser(role: CurrentUser['role'] = 'ADMIN'): CurrentUser {
  return {
    id: 9,
    displayName: '管理员',
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
        <TagManagePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function conflictError(code: string, details: unknown): ApiError {
  return new ApiError({ code, message: '服务端说不行', traceId: 't-1', details })
}

describe('TagManagePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    setAuthToken(null)
    mockedFetchTags.mockResolvedValue([tag()])
  })

  /**
   * 不断言"没打 /tags":那条接口是匿名可用的,列表页与筛选面板本来就在拉它,这个页面命中的多半是
   * 同一份缓存。要断言的是<b>没渲染出一张能点删除的表格</b>。
   */
  it('should_guide_to_login_when_not_logged_in', async () => {
    renderPage()

    expect(await screen.findByText('请先登录')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  /** 普通用户点进来要立刻知道自己不该在这儿,而不是看到一张能点"删除"的表格。 */
  it('should_refuse_when_logged_in_as_normal_user', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser('USER'))

    renderPage()

    expect(await screen.findByText('需要管理员权限')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('should_list_tags_when_admin', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchTags.mockResolvedValue([tag(), tag({ id: 5, name: '随手标', kind: 'FREE' })])

    renderPage()

    const table = await screen.findByRole('table')
    expect(within(table).getByText('代码审查')).toBeInTheDocument()
    expect(within(table).getByText('随手标')).toBeInTheDocument()
  })

  it('should_filter_by_kind_locally', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchTags.mockResolvedValue([tag(), tag({ id: 5, name: '随手标', kind: 'FREE' })])

    renderPage()
    await screen.findByRole('table')
    fireEvent.click(screen.getByRole('button', { name: '自由标签(FREE)' }))

    const table = screen.getByRole('table')
    expect(within(table).queryByText('代码审查')).not.toBeInTheDocument()
    expect(within(table).getByText('随手标')).toBeInTheDocument()
    // 本地过滤,不重新打库
    expect(mockedFetchTags).toHaveBeenCalledTimes(1)
  })

  it('should_create_tag_when_admin_submits_form', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedCreateTag.mockResolvedValue(tag({ id: 11, name: '性能优化' }))

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '新建分类' }))
    fireEvent.change(screen.getByLabelText('标签名'), { target: { value: '性能优化' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(mockedCreateTag).toHaveBeenCalledWith({
        name: '性能优化',
        description: undefined,
        sortOrder: 0,
      }),
    )
  })

  /**
   * 这是本页最要紧的一条交互:`CURATED -> FREE` 服务端第一次用 409 把受影响清单送回来,页面必须把
   * 清单摆出来让 ADMIN 看过,再带 `confirmed=true` 重发。漏了这一步,要么是永远转不过去,要么是
   * 悄悄替用户确认了。
   */
  it('should_show_affected_skills_then_resend_with_confirmed_when_converting_to_free', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedUpdateTag.mockRejectedValueOnce(
      conflictError('TAG_KIND_CONVERSION_NEEDS_CONFIRMATION', {
        affectedSkillCount: 2,
        affectedSkills: ['skill-a', 'skill-b'],
      }),
    )

    renderPage()
    const table = await screen.findByRole('table')
    fireEvent.click(within(table).getByRole('button', { name: '编辑' }))
    fireEvent.change(screen.getByLabelText('类型'), { target: { value: 'FREE' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('确认转成 FREE?')).toBeInTheDocument()
    expect(screen.getByText('skill-a')).toBeInTheDocument()
    expect(screen.getByText('skill-b')).toBeInTheDocument()
    expect(mockedUpdateTag).toHaveBeenCalledTimes(1)
    expect(mockedUpdateTag).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ confirmed: false }),
    )

    mockedUpdateTag.mockResolvedValueOnce(tag({ kind: 'FREE' }))
    fireEvent.click(screen.getByRole('button', { name: '我知道了,继续' }))

    await waitFor(() =>
      expect(mockedUpdateTag).toHaveBeenLastCalledWith(
        1,
        expect.objectContaining({ kind: 'FREE', confirmed: true }),
      ),
    )
  })

  /** 这个方向是硬拒绝,没有确认这条路,页面不能弹确认框把它伪装成"点一下就能过"。 */
  it('should_show_error_without_confirm_dialog_when_conversion_blocked', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchTags.mockResolvedValue([tag({ id: 5, name: '随手标', kind: 'FREE', skillCount: 1 })])
    mockedUpdateTag.mockRejectedValue(
      conflictError('TAG_KIND_CONVERSION_BLOCKED', {
        curatedLimit: 3,
        affectedSkills: ['full-skill'],
      }),
    )

    renderPage()
    const table = await screen.findByRole('table')
    fireEvent.click(within(table).getByRole('button', { name: '编辑' }))
    fireEvent.change(screen.getByLabelText('类型'), { target: { value: 'CURATED' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(screen.getByText('服务端说不行')).toBeInTheDocument())
    expect(screen.queryByText('确认转成 FREE?')).not.toBeInTheDocument()
  })

  it('should_delete_with_migration_target_when_confirmed', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchTags.mockResolvedValue([tag(), tag({ id: 2, name: '测试', skillCount: 0 })])
    mockedDeleteTag.mockResolvedValue(undefined)

    renderPage()
    await screen.findByRole('table')
    // 按行定位而不是取数组第 0 项:表里有两行都带"删除"按钮,靠下标选中的是哪一行取决于渲染顺序。
    const targetRow = screen.getByRole('row', { name: /代码审查/ })
    fireEvent.click(within(targetRow).getByRole('button', { name: '删除' }))
    fireEvent.change(screen.getByLabelText('迁移到'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(mockedDeleteTag).toHaveBeenCalledWith(1, { migrateToTagId: 2 }))
  })

  /**
   * 前端的 `skillCount` 只数已发布 skill,服务端的引用计数含已下架的。所以"按钮可点却被 409 顶回来"
   * 是正常状态,页面必须把服务端的话显示出来,不能只依赖自己的禁用判断。
   */
  it('should_surface_server_error_when_delete_rejected', async () => {
    setAuthToken('token')
    mockedFetchCurrentUser.mockResolvedValue(currentUser())
    mockedFetchTags.mockResolvedValue([tag({ skillCount: 0 })])
    mockedDeleteTag.mockRejectedValue(
      conflictError('TAG_DELETE_REQUIRES_MIGRATION', { referenceCount: 2 }),
    )

    renderPage()
    const table = await screen.findByRole('table')
    fireEvent.click(within(table).getByRole('button', { name: '删除' }))
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(screen.getByText('服务端说不行')).toBeInTheDocument())
  })
})
