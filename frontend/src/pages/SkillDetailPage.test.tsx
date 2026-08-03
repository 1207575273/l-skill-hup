import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { ApiError, setAuthToken } from '../api/client'
import * as installApi from '../api/install'
import * as skillsApi from '../api/skills'
import * as tagsApi from '../api/tags'
import type {
  CurrentUser,
  SkillDetail,
  SkillVersionDetail,
  SkillVersionSummary,
} from '../api/types'
import SkillDetailPage from './SkillDetailPage'

vi.mock('../api/skills')
vi.mock('../api/auth')
vi.mock('../api/tags')
vi.mock('../api/install')

function buildLatestVersion(overrides: Partial<SkillVersionDetail> = {}): SkillVersionDetail {
  return {
    id: 20,
    version: '1.2.0',
    sizeBytes: 4096,
    fileCount: 6,
    files: [
      { path: 'SKILL.md', sizeBytes: 1024 },
      { path: 'references/guide.md', sizeBytes: 2048 },
    ],
    allowedTools: ['Read', 'Grep'],
    skillMdContent: '# SKILL.md 正文',
    readmeContent: null,
    changelog: null,
    certification: 'NONE',
    certifiedByUserId: null,
    certifiedAt: null,
    certifyNote: null,
    createdByUserId: 1,
    createdAt: '2026-07-20T08:00:00Z',
    ...overrides,
  }
}

function buildSkill(overrides: Partial<SkillDetail> = {}): SkillDetail {
  return {
    id: 42,
    name: 'code-review-helper',
    displayName: '代码审查助手',
    description: '一个用于代码审查的 skill',
    source: 'INTERNAL',
    status: 'PUBLISHED',
    cardExcerpt: '摘要',
    downloadCount: 10,
    likeCount: 3,
    dislikeCount: 0,
    favoriteCount: 2,
    commentCount: 1,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-20T08:00:00Z',
    owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' },
    tags: [
      { id: 1, name: '代码审查', kind: 'CURATED', description: null, sortOrder: 1 },
      { id: 2, name: 'pdf', kind: 'FREE', description: null, sortOrder: 1 },
    ],
    latestVersion: buildLatestVersion(),
    ...overrides,
  }
}

function buildVersionSummary(overrides: Partial<SkillVersionSummary> = {}): SkillVersionSummary {
  return {
    id: 20,
    version: '1.2.0',
    sizeBytes: 4096,
    fileCount: 6,
    certification: 'NONE',
    certifiedByUserId: null,
    certifiedAt: null,
    certifyNote: null,
    createdByUserId: 1,
    createdAt: '2026-07-20T08:00:00Z',
    ...overrides,
  }
}

function buildCurrentUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    id: 900,
    displayName: '李四',
    employeeNo: 'E2048',
    email: null,
    avatarUrl: null,
    role: 'USER',
    identities: [],
    ...overrides,
  }
}

function renderPage(path = '/skills/42') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/skills/:id" element={<SkillDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SkillDetailPage', () => {
  beforeEach(() => {
    setAuthToken('test-token')
    vi.mocked(tagsApi.fetchTags).mockResolvedValue([])
    vi.mocked(installApi.fetchInstallClients).mockResolvedValue([
      { client: 'claude', scope: 'global', skillsSubDir: '.claude/skills' },
    ])
    vi.mocked(installApi.createInstallBundle).mockResolvedValue({
      token: 'bundle-token',
      expiresAt: '2026-09-01T00:00:00Z',
      client: 'claude',
      scope: 'global',
      powerShellCommand: 'irm http://x/skillhub/i/bundle-token.ps1 | iex',
      bashCommand: 'curl -fsSL http://x/skillhub/i/bundle-token.sh | bash',
    })
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(buildCurrentUser())
  })

  afterEach(() => {
    setAuthToken(null)
    vi.clearAllMocks()
  })

  it('should_show_loading_spinner_when_skill_query_pending', () => {
    vi.mocked(skillsApi.fetchSkillDetail).mockReturnValue(new Promise(() => {}))
    vi.mocked(skillsApi.fetchSkillVersions).mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(screen.getByRole('status')).toHaveTextContent('正在加载 skill 详情')
  })

  it('should_show_invalid_id_message_when_route_param_is_not_numeric', () => {
    renderPage('/skills/abc')

    expect(screen.getByText('无效的 skill 编号')).toBeInTheDocument()
    expect(skillsApi.fetchSkillDetail).not.toHaveBeenCalled()
  })

  it('should_show_not_found_message_when_server_returns_skill_not_found', async () => {
    vi.mocked(skillsApi.fetchSkillDetail).mockRejectedValue(
      new ApiError({ code: 'SKILL_NOT_FOUND', message: 'skill 不存在', traceId: 'trace-1' }),
    )
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('没有找到这个 skill')).toBeInTheDocument()
  })

  it('should_show_error_banner_with_retry_when_request_fails_for_other_reason', async () => {
    vi.mocked(skillsApi.fetchSkillDetail).mockRejectedValue(
      new ApiError({ code: 'UNKNOWN_ERROR', message: '服务器开小差了', traceId: 'trace-2' }),
    )
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('服务器开小差了')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument()
  })

  it('should_prefer_readme_over_skill_md_when_readme_present', async () => {
    const skill = buildSkill({
      latestVersion: buildLatestVersion({
        readmeContent: '# README 正文',
        skillMdContent: '# SKILL 正文',
      }),
    })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([buildVersionSummary()])

    renderPage()

    expect(await screen.findByText('当前显示:README.md')).toBeInTheDocument()
    // 正文走 Markdown 渲染,`# README 正文` 会变成 <h1>README 正文</h1>,
    // 不再有字面量 "# README 正文" 这个文本节点。
    expect(screen.getByRole('heading', { name: 'README 正文' })).toBeInTheDocument()
  })

  it('should_fallback_to_skill_md_when_readme_missing', async () => {
    const skill = buildSkill({
      latestVersion: buildLatestVersion({ readmeContent: null, skillMdContent: '# SKILL 正文' }),
    })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([buildVersionSummary()])

    renderPage()

    expect(await screen.findByText('当前显示:SKILL.md(未提供 README.md)')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'SKILL 正文' })).toBeInTheDocument()
  })

  it('should_not_crash_and_hide_quick_install_when_latest_version_is_null', async () => {
    const skill = buildSkill({ latestVersion: null })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('暂无可用版本')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '快速接入' })).not.toBeInTheDocument()
  })

  it('should_show_certify_note_when_latest_version_revoked', async () => {
    const note = '包含未脱敏的内网地址'
    const skill = buildSkill({
      latestVersion: buildLatestVersion({ certification: 'REVOKED', certifyNote: note }),
    })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([
      buildVersionSummary({ certification: 'REVOKED', certifyNote: note }),
    ])

    renderPage()

    expect((await screen.findAllByText(note)).length).toBeGreaterThan(0)
  })

  it('should_hide_management_section_when_current_user_is_not_owner_or_admin', async () => {
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(
      buildCurrentUser({ id: 999, role: 'USER' }),
    )
    const skill = buildSkill()
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([buildVersionSummary()])

    renderPage()

    await screen.findByText(skill.displayName)
    expect(screen.queryByText('管理操作')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑元数据' })).not.toBeInTheDocument()
  })

  it('should_show_management_section_when_current_user_is_owner', async () => {
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(buildCurrentUser({ id: 1, role: 'USER' }))
    const skill = buildSkill({ owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' } })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([buildVersionSummary()])

    renderPage()

    expect(await screen.findByText('管理操作')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '下架此 skill' })).toBeInTheDocument()
  })

  it('should_show_management_section_when_current_user_is_admin_even_if_not_owner', async () => {
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(
      buildCurrentUser({ id: 999, role: 'ADMIN' }),
    )
    const skill = buildSkill({ owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' } })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([buildVersionSummary()])

    renderPage()

    expect(await screen.findByText('管理操作')).toBeInTheDocument()
  })

  it('should_call_unlist_when_owner_clicks_down_button_on_published_skill', async () => {
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(buildCurrentUser({ id: 1, role: 'USER' }))
    vi.mocked(skillsApi.unlistSkill).mockResolvedValue(buildSkill({ status: 'UNLISTED' }))
    const skill = buildSkill({
      status: 'PUBLISHED',
      owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' },
    })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([buildVersionSummary()])

    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '下架此 skill' }))

    await waitFor(() => {
      expect(skillsApi.unlistSkill).toHaveBeenCalledWith(42)
    })
  })

  it('should_render_version_list_with_per_version_certification', async () => {
    const skill = buildSkill()
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([
      buildVersionSummary({ id: 20, version: '1.2.0', certification: 'CERTIFIED' }),
      buildVersionSummary({ id: 10, version: '1.0.0', certification: 'NONE' }),
    ])

    renderPage()

    expect(await screen.findByText('v1.2.0')).toBeInTheDocument()
    expect(screen.getByText('v1.0.0')).toBeInTheDocument()
    expect(screen.getByText('平台认证')).toBeInTheDocument()
  })

  it('should_use_latest_version_by_default_when_generating_install_bundle', async () => {
    const skill = buildSkill({ id: 42, latestVersion: buildLatestVersion({ id: 20 }) })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([
      buildVersionSummary({ id: 20, version: '1.2.0' }),
      buildVersionSummary({ id: 10, version: '1.0.0' }),
    ])

    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '快速接入' }))
    fireEvent.click(await screen.findByText(/Claude Code/))

    await waitFor(() => {
      expect(installApi.createInstallBundle).toHaveBeenCalledWith(
        [{ skillId: 42, versionId: 20 }],
        'claude',
        'global',
      )
    })
  })

  it('should_generate_bundle_for_selected_historical_version_when_switching_selection', async () => {
    const skill = buildSkill({ id: 42, latestVersion: buildLatestVersion({ id: 20 }) })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([
      buildVersionSummary({ id: 20, version: '1.2.0' }),
      buildVersionSummary({ id: 10, version: '1.0.0' }),
    ])

    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '选择版本 1.0.0' }))
    fireEvent.click(screen.getByRole('button', { name: '快速接入' }))
    fireEvent.click(await screen.findByText(/Claude Code/))

    await waitFor(() => {
      expect(installApi.createInstallBundle).toHaveBeenCalledWith(
        [{ skillId: 42, versionId: 10 }],
        'claude',
        'global',
      )
    })
  })

  it('should_open_edit_modal_and_submit_updated_metadata_when_owner_saves_changes', async () => {
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(buildCurrentUser({ id: 1, role: 'USER' }))
    vi.mocked(tagsApi.fetchTags).mockResolvedValue([
      { id: 1, name: '代码审查', kind: 'CURATED', description: null, sortOrder: 1, skillCount: 3 },
    ])
    vi.mocked(skillsApi.updateSkillMetadata).mockResolvedValue(buildSkill())
    const skill = buildSkill({ owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' } })
    vi.mocked(skillsApi.fetchSkillDetail).mockResolvedValue(skill)
    vi.mocked(skillsApi.fetchSkillVersions).mockResolvedValue([buildVersionSummary()])

    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '编辑元数据' }))
    fireEvent.click(await screen.findByRole('button', { name: '保存修改' }))

    await waitFor(() => {
      expect(skillsApi.updateSkillMetadata).toHaveBeenCalledWith(42, {
        displayName: '代码审查助手',
        source: 'INTERNAL',
        curatedTagIds: [1],
        freeTagNames: ['pdf'],
      })
    })
  })
})
