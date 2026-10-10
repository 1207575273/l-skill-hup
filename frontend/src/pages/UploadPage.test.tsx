import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import JSZip from 'jszip'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { setAuthToken } from '../api/client'
import * as skillsApi from '../api/skills'
import * as tagsApi from '../api/tags'
import type {
  CurrentUser,
  SkillDetail,
  SkillPrecheckExisting,
  SkillPrecheckResult,
  Tag,
} from '../api/types'
import { createDirectoryFile, validSkillMd } from '../lib/skillPackageFixtures.test-support'
import UploadPage from './UploadPage'

vi.mock('../api/auth')
vi.mock('../api/skills')
vi.mock('../api/tags')

const navigateMock = vi.fn()
vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>()
  return { ...actual, useNavigate: () => navigateMock }
})

const mockedFetchCurrentUser = vi.mocked(authApi.fetchCurrentUser)
const mockedFetchTags = vi.mocked(tagsApi.fetchTags)
const mockedPrecheck = vi.mocked(skillsApi.precheckSkillPackage)
const mockedPublishSkill = vi.mocked(skillsApi.publishSkill)
const mockedPublishSkillVersion = vi.mocked(skillsApi.publishSkillVersion)

const CURRENT_USER: CurrentUser = {
  id: 1,
  displayName: '李四',
  employeeNo: null,
  email: null,
  avatarUrl: null,
  role: 'USER',
  identities: [],
}

const CURATED_TAGS: Tag[] = [
  { id: 1, name: '代码审查', kind: 'CURATED', description: null, sortOrder: 10, skillCount: 0 },
]

const VALID_PRECHECK: SkillPrecheckResult = {
  valid: true,
  parsed: { name: 'demo-skill', description: '示例 skill', allowedTools: [] },
  contentDigest: 'digest',
  fileCount: 1,
  unpackedBytes: 100,
  hasReadme: false,
  identical: null,
  duplicatedName: false,
  existing: null,
  suggestedName: null,
  violations: [],
}

const EXISTING_SKILL: SkillPrecheckExisting = {
  skillId: 42,
  name: 'demo-skill',
  ownerDisplayName: '张三',
  ownerEmployeeNo: null,
  latestVersion: '1.2.0',
  canPublishNewVersion: true,
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <UploadPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function buildValidZipFile(): Promise<File> {
  const zip = new JSZip()
  zip.file('SKILL.md', validSkillMd({ name: 'demo-skill' }))
  const blob = await zip.generateAsync({ type: 'blob' })
  return new File([blob], 'demo-skill.zip', { type: 'application/zip' })
}

async function selectValidSingleSkillMdFile() {
  const file = new File([validSkillMd({ name: 'demo-skill' })], 'SKILL.md')
  fireEvent.change(await screen.findByLabelText('选择单个 SKILL.md'), { target: { files: [file] } })
  await screen.findByLabelText('版本号')
}

/** 走完"选文件 -> 填版本号 -> 选一个分类标签 -> 点发布"的通用前置流程。 */
async function completeFormAndSubmit() {
  await selectValidSingleSkillMdFile()
  fireEvent.change(screen.getByLabelText('版本号'), { target: { value: '1.0.0' } })
  fireEvent.click(await screen.findByRole('checkbox', { name: '代码审查' }))
  fireEvent.click(screen.getByRole('button', { name: '发布' }))
}

describe('UploadPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAuthToken(null)
    mockedFetchTags.mockResolvedValue(CURATED_TAGS)
  })

  afterEach(() => {
    window.localStorage.clear()
  })

  it('should_allow_upload_when_not_authenticated', async () => {
    // 上传已放开匿名。这条用例替掉了原来的"未登录显示登录提示" —— 那条断言的是旧契约。
    renderPage()

    expect(screen.queryByText('上传 skill 需要先登录')).not.toBeInTheDocument()
    expect(await screen.findByLabelText('选择文件夹')).toBeInTheDocument()
  })

  it('should_show_declared_owner_fields_when_not_authenticated', async () => {
    renderPage()
    await selectValidSingleSkillMdFile()

    // 匿名时才出现:填的是"自称归属",界面必须说清它不会被核实。
    expect(screen.getByLabelText('归属人姓名(可选)')).toBeInTheDocument()
    expect(screen.getByLabelText('归属人工号(可选)')).toBeInTheDocument()
    expect(screen.getByText(/不会被核实/)).toBeInTheDocument()
  })

  it('should_hide_declared_owner_fields_when_authenticated', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    renderPage()
    await selectValidSingleSkillMdFile()

    // 已登录时归属人就是本人,带声明归属后端会报 400,所以字段根本不该出现。
    expect(screen.queryByLabelText('归属人姓名(可选)')).not.toBeInTheDocument()
  })

  it('should_replace_free_tag_input_with_hint_when_not_authenticated', async () => {
    renderPage()
    await selectValidSingleSkillMdFile()

    // 自由标签是全局词表,匿名不能新建(后端也会拒)。界面上换成说明而不是留个会报错的输入框。
    expect(screen.getByText(/自由标签要登录后才能打/)).toBeInTheDocument()
  })

  it('should_send_declared_owner_and_no_free_tags_when_publishing_anonymously', async () => {
    mockedPrecheck.mockResolvedValue(VALID_PRECHECK)
    mockedPublishSkill.mockResolvedValue({ id: 11 } as SkillDetail)
    renderPage()

    await selectValidSingleSkillMdFile()
    fireEvent.change(screen.getByLabelText('版本号'), { target: { value: '1.0.0' } })
    fireEvent.change(screen.getByLabelText('归属人姓名(可选)'), { target: { value: '  张三  ' } })
    fireEvent.change(screen.getByLabelText('归属人工号(可选)'), { target: { value: '80380486' } })
    fireEvent.click(await screen.findByRole('checkbox', { name: '代码审查' }))
    fireEvent.click(screen.getByRole('button', { name: '发布' }))

    await waitFor(() => expect(mockedPublishSkill).toHaveBeenCalledTimes(1))
    expect(mockedPublishSkill.mock.calls[0]?.[0]).toMatchObject({
      declaredOwnerName: '  张三  ',
      declaredOwnerEmployeeNo: '80380486',
      // 匿名不发自由标签 —— 不依赖"界面没展示所以状态一定是空"这个假设。
      freeTagNames: [],
    })
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/skills/11'))
  })

  it('should_normalize_directory_selection_to_zip_when_folder_selected', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    renderPage()

    const file = createDirectoryFile('demo-skill/SKILL.md', validSkillMd({ name: 'demo-skill' }))
    fireEvent.change(await screen.findByLabelText('选择文件夹'), { target: { files: [file] } })

    expect(await screen.findByText('demo-skill')).toBeInTheDocument()
    expect(screen.getByLabelText('版本号')).toBeInTheDocument()
  })

  it('should_normalize_dropped_zip_to_zip_when_zip_file_dropped', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    renderPage()

    const file = await buildValidZipFile()
    fireEvent.drop(await screen.findByText('拖入 zip,或点击选择'), {
      dataTransfer: { files: [file] },
    })

    expect(await screen.findByText('demo-skill')).toBeInTheDocument()
    expect(screen.getByLabelText('版本号')).toBeInTheDocument()
  })

  it('should_normalize_single_skill_md_to_zip_when_md_file_selected', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    renderPage()

    await selectValidSingleSkillMdFile()

    expect(screen.getByText('demo-skill')).toBeInTheDocument()
  })

  it('should_list_local_violations_when_single_file_is_not_valid_frontmatter', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    renderPage()

    const file = new File(['不是合法的 frontmatter'], 'SKILL.md')
    fireEvent.change(await screen.findByLabelText('选择单个 SKILL.md'), {
      target: { files: [file] },
    })

    expect(await screen.findByText('本地校验未通过')).toBeInTheDocument()
    expect(screen.getByText('FRONTMATTER_MISSING_DELIMITER')).toBeInTheDocument()
    expect(screen.queryByLabelText('版本号')).not.toBeInTheDocument()
  })

  it('should_publish_new_skill_when_precheck_reports_no_conflict', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    mockedPrecheck.mockResolvedValue(VALID_PRECHECK)
    mockedPublishSkill.mockResolvedValue({ id: 7 } as SkillDetail)
    renderPage()

    await completeFormAndSubmit()

    await waitFor(() => expect(mockedPublishSkill).toHaveBeenCalledTimes(1))
    expect(mockedPublishSkill.mock.calls[0]?.[0]).toMatchObject({
      version: '1.0.0',
      source: 'INTERNAL',
      curatedTagIds: [1],
    })
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/skills/7'))
  })

  it('should_publish_without_selecting_any_category', async () => {
    // 分类曾经必选,已放宽(业务建模 5.7):上传的人往往还没想好归到哪一类,
    // 被拦在门外只能随便挑一个凑数,而随手凑的分类比没有分类更糟。
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    mockedPrecheck.mockResolvedValue(VALID_PRECHECK)
    mockedPublishSkill.mockResolvedValue({ id: 8 } as SkillDetail)
    renderPage()

    await selectValidSingleSkillMdFile()
    fireEvent.change(screen.getByLabelText('版本号'), { target: { value: '1.0.0' } })
    // 刻意不点任何分类
    fireEvent.click(screen.getByRole('button', { name: '发布' }))

    await waitFor(() => expect(mockedPublishSkill).toHaveBeenCalledTimes(1))
    expect(mockedPublishSkill.mock.calls[0]?.[0]).toMatchObject({ curatedTagIds: [] })
  })

  it('should_show_server_violations_when_precheck_reports_them', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    mockedPrecheck.mockResolvedValue({
      ...VALID_PRECHECK,
      valid: false,
      parsed: null,
      violations: [{ code: 'SKILL_MD_MISSING', message: '包内缺少 SKILL.md' }],
    })
    renderPage()

    await completeFormAndSubmit()

    expect(await screen.findByText('服务端校验未通过')).toBeInTheDocument()
    expect(screen.getByText('SKILL_MD_MISSING')).toBeInTheDocument()
    expect(mockedPublishSkill).not.toHaveBeenCalled()
  })

  it('should_show_identical_notice_when_precheck_reports_identical_match', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    mockedPrecheck.mockResolvedValue({
      ...VALID_PRECHECK,
      identical: { skillId: 5, version: '1.0.0' },
    })
    renderPage()

    await completeFormAndSubmit()

    expect(await screen.findByText(/完全相同的包已存在/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /skill #5/ })).toHaveAttribute('href', '/skills/5')
    expect(mockedPublishSkill).not.toHaveBeenCalled()
  })

  it('should_publish_renamed_new_skill_when_rename_chosen_in_duplicate_dialog', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    mockedPrecheck.mockResolvedValue({
      ...VALID_PRECHECK,
      duplicatedName: true,
      existing: EXISTING_SKILL,
      suggestedName: 'demo-skill-a1b2c3',
    })
    mockedPublishSkill.mockResolvedValue({ id: 99 } as SkillDetail)
    renderPage()

    await completeFormAndSubmit()

    fireEvent.click(await screen.findByRole('button', { name: /改名新建/ }))

    await waitFor(() => expect(mockedPublishSkill).toHaveBeenCalledTimes(1))
    expect(mockedPublishSkillVersion).not.toHaveBeenCalled()
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/skills/99'))
  })

  it('should_publish_new_version_when_new_version_chosen_in_duplicate_dialog', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    mockedPrecheck.mockResolvedValue({
      ...VALID_PRECHECK,
      duplicatedName: true,
      existing: EXISTING_SKILL,
      suggestedName: 'demo-skill-a1b2c3',
    })
    mockedPublishSkillVersion.mockResolvedValue({ id: 42 } as SkillDetail)
    renderPage()

    await completeFormAndSubmit()

    fireEvent.click(await screen.findByRole('button', { name: '作为新版本发布' }))

    await waitFor(() =>
      expect(mockedPublishSkillVersion).toHaveBeenCalledWith(
        42,
        expect.objectContaining({ version: '1.0.0' }),
      ),
    )
    expect(mockedPublishSkill).not.toHaveBeenCalled()
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/skills/42'))
  })

  it('should_hide_new_version_option_when_canPublishNewVersion_is_false', async () => {
    setAuthToken('tok')
    mockedFetchCurrentUser.mockResolvedValue(CURRENT_USER)
    mockedPrecheck.mockResolvedValue({
      ...VALID_PRECHECK,
      duplicatedName: true,
      existing: { ...EXISTING_SKILL, canPublishNewVersion: false },
      suggestedName: 'demo-skill-a1b2c3',
    })
    renderPage()

    await completeFormAndSubmit()

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '作为新版本发布' })).not.toBeInTheDocument()
  })
})
