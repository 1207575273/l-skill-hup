import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken } from '../api/client'
import * as skillsApi from '../api/skills'
import type { CurrentUser, PageResponse, SkillCommentItem } from '../api/types'
import { SkillCommentSection } from './SkillCommentSection'

vi.mock('../api/skills')

const mockedFetchComments = vi.mocked(skillsApi.fetchSkillComments)
const mockedAddComment = vi.mocked(skillsApi.addSkillComment)
const mockedDeleteComment = vi.mocked(skillsApi.deleteSkillComment)

function comment(overrides: Partial<SkillCommentItem> = {}): SkillCommentItem {
  return {
    id: 1,
    parentId: null,
    author: { userId: 2, displayName: '张三', employeeNo: '80380486' },
    content: '这个 skill 很好用',
    deleted: false,
    createdAt: '2026-08-01T10:30:00Z',
    replies: [],
    ...overrides,
  }
}

function page(items: SkillCommentItem[]): PageResponse<SkillCommentItem> {
  return { items, total: items.length, page: 1, size: 20 }
}

function currentUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    id: 2,
    displayName: '张三',
    employeeNo: null,
    email: null,
    avatarUrl: null,
    role: 'USER',
    identities: [],
    ...overrides,
  }
}

function renderSection(user: CurrentUser | undefined) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SkillCommentSection skillId={1} currentUser={user} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SkillCommentSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    setAuthToken(null)
  })

  it('should_render_comments_as_plain_text_not_markdown', async () => {
    // 业务建模 5.11:渲染用户内容是 XSS 的主要入口,而这个平台在分发会被 Agent 执行的东西。
    // 正文里的 Markdown/HTML 必须原样显示,不能被解析。
    mockedFetchComments.mockResolvedValue(page([comment({ content: '**加粗** 和 <b>标签</b>' })]))

    renderSection(undefined)

    const body = await screen.findByText('**加粗** 和 <b>标签</b>')
    expect(body).toBeInTheDocument()
    // 真的被解析的话这里会查到一个 <b> 元素
    expect(body.querySelector('b')).toBeNull()
  })

  it('should_guide_to_login_when_not_logged_in', async () => {
    mockedFetchComments.mockResolvedValue(page([]))

    renderSection(undefined)

    expect(await screen.findByRole('link', { name: '登录' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '发表' })).not.toBeInTheDocument()
  })

  it('should_show_placeholder_and_keep_replies_when_comment_deleted', async () => {
    // 软删除存在的唯一理由就是不让回复悬空 —— 占位在,回复照常显示。
    mockedFetchComments.mockResolvedValue(
      page([
        comment({
          deleted: true,
          content: null,
          replies: [comment({ id: 2, parentId: 1, content: '回复还在' })],
        }),
      ]),
    )

    renderSection(undefined)

    expect(await screen.findByText('该评论已删除')).toBeInTheDocument()
    expect(screen.getByText('回复还在')).toBeInTheDocument()
  })

  it('should_not_offer_reply_on_deleted_comment', async () => {
    // 与服务端的校验一致:已删除的评论不能再被回复。界面上做不出来的操作,就不会有人去试。
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([comment({ deleted: true, content: null })]))

    renderSection(currentUser())

    await screen.findByText('该评论已删除')
    expect(screen.queryByRole('button', { name: '回复' })).not.toBeInTheDocument()
  })

  it('should_send_parentId_when_replying', async () => {
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([comment({ id: 7 })]))
    mockedAddComment.mockResolvedValue(comment({ id: 9, parentId: 7 }))

    renderSection(currentUser())
    fireEvent.click(await screen.findByRole('button', { name: '回复' }))
    fireEvent.change(screen.getByPlaceholderText('回复这条评论'), {
      target: { value: '我也觉得' },
    })
    // 顶级发表框与回复框同时在场,两个"发表"按钮,取后一个(回复框)
    const submitButtons = screen.getAllByRole('button', { name: '发表' })
    fireEvent.click(submitButtons[submitButtons.length - 1] as HTMLElement)

    await waitFor(() => {
      expect(mockedAddComment).toHaveBeenCalledWith(1, { parentId: 7, content: '我也觉得' })
    })
  })

  it('should_omit_parentId_when_posting_top_level_comment', async () => {
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([]))
    mockedAddComment.mockResolvedValue(comment())

    renderSection(currentUser())
    fireEvent.change(await screen.findByPlaceholderText('说点什么'), {
      target: { value: '第一条' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发表' }))

    await waitFor(() => {
      expect(mockedAddComment).toHaveBeenCalledWith(1, { parentId: undefined, content: '第一条' })
    })
  })

  it('should_keep_submit_disabled_when_content_blank', async () => {
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([]))

    renderSection(currentUser())
    fireEvent.change(await screen.findByPlaceholderText('说点什么'), { target: { value: '   ' } })

    expect(screen.getByRole('button', { name: '发表' })).toBeDisabled()
  })

  it('should_keep_submit_disabled_when_content_exceeds_limit', async () => {
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([]))

    renderSection(currentUser())
    fireEvent.change(await screen.findByPlaceholderText('说点什么'), {
      target: { value: '字'.repeat(2001) },
    })

    expect(screen.getByRole('button', { name: '发表' })).toBeDisabled()
  })

  it('should_offer_delete_on_own_comment', async () => {
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([comment({ id: 7 })]))
    mockedDeleteComment.mockResolvedValue(undefined)

    renderSection(currentUser({ id: 2 }))
    fireEvent.click(await screen.findByRole('button', { name: '删除' }))

    await waitFor(() => expect(mockedDeleteComment).toHaveBeenCalledWith(7))
  })

  it('should_not_offer_delete_on_someone_elses_comment', async () => {
    // 权限与服务端一致:作者本人或 ADMIN。skill 的 owner 不在其列 ——
    // 让被评论者能删评论,会把"我的地盘我说了算"变成删除异见的手段。
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([comment({ id: 7 })]))

    renderSection(currentUser({ id: 999 }))

    await screen.findByText('这个 skill 很好用')
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument()
  })

  it('should_offer_delete_to_admin_on_any_comment', async () => {
    setAuthToken('tok')
    mockedFetchComments.mockResolvedValue(page([comment({ id: 7 })]))

    renderSection(currentUser({ id: 999, role: 'ADMIN' }))

    expect(await screen.findByRole('button', { name: '删除' })).toBeInTheDocument()
  })
})
