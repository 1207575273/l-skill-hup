import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as installApi from '../api/install'
import * as skillsApi from '../api/skills'
import * as tagsApi from '../api/tags'
import type { SkillSummary, SkillVersionSummary } from '../api/types'
import { useInstallCartStore } from '../hooks/useInstallCartStore'
import SkillListPage from './SkillListPage'

vi.mock('../api/skills')
vi.mock('../api/tags')
vi.mock('../api/install')

const mockedFetchSkillList = vi.mocked(skillsApi.fetchSkillList)
const mockedFetchSkillVersions = vi.mocked(skillsApi.fetchSkillVersions)
const mockedFetchTags = vi.mocked(tagsApi.fetchTags)
const mockedFetchInstallClients = vi.mocked(installApi.fetchInstallClients)
const mockedCreateInstallBundle = vi.mocked(installApi.createInstallBundle)

function buildSkill(overrides: Partial<SkillSummary> = {}): SkillSummary {
  return {
    id: 1,
    name: 'pdf-summarizer',
    displayName: 'PDF 摘要助手',
    cardExcerpt: '一个用来提炼 PDF 要点的 skill',
    certification: 'NONE',
    downloadCount: 10,
    likeCount: 2,
    favoriteCount: 1,
    updatedAt: '2026-07-20T08:00:00Z',
    owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' },
    tags: [],
    latestVersionId: 99,
    ...overrides,
  }
}

/** 测试用的地址栏探针,不属于产品代码——用来断言"筛选变更是否真的同步进了 URL"。 */
function LocationProbe() {
  const location = useLocation()
  return (
    <p>
      当前地址:{location.pathname}
      {location.search}
    </p>
  )
}

function renderPage(initialEntries: string[] = ['/']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <SkillListPage />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SkillListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedFetchTags.mockResolvedValue([])
  })

  it('should_show_loading_state_when_list_query_pending', () => {
    mockedFetchSkillList.mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(screen.getByText('正在加载 skill 列表')).toBeInTheDocument()
  })

  it('should_show_empty_state_when_list_has_no_items', async () => {
    mockedFetchSkillList.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })

    renderPage()

    expect(await screen.findByText('没有找到匹配的 skill')).toBeInTheDocument()
  })

  it('should_show_error_banner_with_trace_id_when_request_fails', async () => {
    mockedFetchSkillList.mockRejectedValue(
      new ApiError({ code: 'INTERNAL_ERROR', message: '服务器开小差了', traceId: 'trace-abc123' }),
    )

    renderPage()

    expect(await screen.findByText('服务器开小差了')).toBeInTheDocument()
    expect(screen.getByText('trace-abc123')).toBeInTheDocument()
  })

  it('should_render_skill_cards_when_list_resolves', async () => {
    mockedFetchSkillList.mockResolvedValue({ items: [buildSkill()], total: 1, page: 1, size: 20 })

    renderPage()

    expect(await screen.findByRole('heading', { name: 'PDF 摘要助手' })).toBeInTheDocument()
  })

  it('should_sync_source_filter_to_url_and_refetch_when_source_selected', async () => {
    mockedFetchSkillList.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })

    renderPage()
    await screen.findByText('没有找到匹配的 skill')

    fireEvent.change(screen.getByLabelText('来源'), { target: { value: 'INTERNAL' } })

    await waitFor(() => {
      expect(mockedFetchSkillList).toHaveBeenCalledWith(
        expect.objectContaining({ source: 'INTERNAL', page: 1 }),
        expect.anything(),
      )
    })
    expect(screen.getByText(/source=INTERNAL/)).toBeInTheDocument()
  })

  it('should_reset_page_to_one_when_filter_changed_from_page_two', async () => {
    mockedFetchSkillList.mockResolvedValue({ items: [], total: 0, page: 2, size: 20 })

    renderPage(['/?page=2'])
    await screen.findByText('没有找到匹配的 skill')

    fireEvent.change(screen.getByLabelText('关键词'), { target: { value: 'pdf' } })

    await waitFor(() => {
      expect(screen.getByText(/keyword=pdf/)).toBeInTheDocument()
    })
    expect(screen.queryByText(/page=2/)).not.toBeInTheDocument()
  })

  it('should_toggle_tag_into_url_and_filter_when_tag_chip_clicked', async () => {
    mockedFetchTags.mockResolvedValue([
      { id: 9, name: '代码审查', kind: 'CURATED', description: null, sortOrder: 1, skillCount: 1 },
    ])
    mockedFetchSkillList.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '代码审查' }))

    await waitFor(() => {
      expect(mockedFetchSkillList).toHaveBeenCalledWith(
        expect.objectContaining({ tagIds: [9] }),
        expect.anything(),
      )
    })
    expect(screen.getByText(/tagIds=9/)).toBeInTheDocument()
  })

  it('should_put_skill_into_install_cart_when_add_button_clicked', async () => {
    // 这条盯的是一个真实漏掉的接缝:安装单 store 早就写好了 addItem/toggleItem,
    // 但整个前端一个调用点都没有 —— 安装单页做完了消费端,生产端谁都没做,
    // 于是安装单永远是空的。页面各自测各自的时候看不出来,因为没人负责这条连线。
    useInstallCartStore.getState().clear()
    mockedFetchSkillList.mockResolvedValue({
      items: [buildSkill({ id: 42, displayName: 'PDF 摘要助手', latestVersionId: 99 })],
      total: 1,
      page: 1,
      size: 20,
    })

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '加入安装单' }))

    expect(useInstallCartStore.getState().items).toEqual([
      { skillId: 42, versionId: 99, skillDisplayName: 'PDF 摘要助手' },
    ])
    // 加入后按钮翻成"已加入",再点一次移出 —— 同一个按钮双向操作,不另开一个"移除"入口。
    fireEvent.click(await screen.findByRole('button', { name: '已加入安装单' }))
    expect(useInstallCartStore.getState().items).toHaveLength(0)
  })

  it('should_ignore_add_to_cart_when_skill_has_no_version', async () => {
    // latestVersionId 为 null 说明这个 skill 一个版本都没有,安装单里放它毫无意义 ——
    // 生成安装命令时必须有 versionId。静默忽略,不塞假 id。
    useInstallCartStore.getState().clear()
    mockedFetchSkillList.mockResolvedValue({
      items: [buildSkill({ id: 7, latestVersionId: null })],
      total: 1,
      page: 1,
      size: 20,
    })

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '加入安装单' }))

    expect(useInstallCartStore.getState().items).toHaveLength(0)
  })

  it('should_resolve_latest_version_id_and_request_bundle_when_quick_install_target_picked', async () => {
    mockedFetchSkillList.mockResolvedValue({
      items: [buildSkill({ id: 42 })],
      total: 1,
      page: 1,
      size: 20,
    })
    const versions: SkillVersionSummary[] = [
      {
        id: 99,
        version: '1.1.0',
        sizeBytes: 100,
        fileCount: 3,
        certification: 'NONE',
        certifiedByUserId: null,
        certifiedAt: null,
        certifyNote: null,
        createdByUserId: 1,
        createdAt: '2026-07-20T08:00:00Z',
      },
    ]
    mockedFetchSkillVersions.mockResolvedValue(versions)
    mockedFetchInstallClients.mockResolvedValue([
      { client: 'claude', scope: 'global', skillsSubDir: '.claude/skills' },
    ])
    mockedCreateInstallBundle.mockResolvedValue({
      token: 'tok',
      expiresAt: '2026-08-01T00:00:00Z',
      client: 'claude',
      scope: 'global',
      powerShellCommand: 'irm x | iex',
      bashCommand: 'curl x | sh',
    })

    renderPage()

    // 只点一次:QuickInstallMenu 自带下拉,SkillCard 不再包第二层。
    // 版本 id 由它展开时自己查(列表项里没有版本 id),不需要页面先取好再传进去。
    fireEvent.click(await screen.findByRole('button', { name: '快速接入' }))

    fireEvent.click(await screen.findByText('Claude Code · 全局'))

    await waitFor(() => {
      expect(mockedCreateInstallBundle).toHaveBeenCalledWith(
        [{ skillId: 42, versionId: 99 }],
        'claude',
        'global',
      )
    })
    expect(mockedFetchSkillVersions).toHaveBeenCalledWith(
      42,
      expect.objectContaining({ signal: expect.anything() }),
    )
  })
})
