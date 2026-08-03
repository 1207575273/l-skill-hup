import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { setAuthToken } from '../api/client'
import * as installApi from '../api/install'
import * as skillsApi from '../api/skills'
import * as tagsApi from '../api/tags'
import AppRouter from './router'

/**
 * 这是一组**路由落点**的冒烟测试:只验证每条路径确实挂到了对应页面、且都套在 `AppLayout` 里。
 * 页面自身的行为由各自的 `*.test.tsx` 负责,这里不重复验。
 *
 * <p>两件必须做的事,少一件测试就会以看不出原因的方式失败:
 *
 * <p>1. **包 `QueryClientProvider`**。`AppLayout` 默认的 `authSlot` 与五个页面都会调
 * `useQuery`/`useMutation`,不包就抛 `No QueryClient set`。
 *
 * <p>2. **mock 掉所有 `api/` 模块**。这些 query 的 `enabled` 恒为 `true`,不 mock 就会在
 * jsdom 里真的发 `fetch` —— 表现是随机超时和控制台噪音,而报错完全不指向真正的原因。
 *
 * <p>断言用**各页面稳定的一级标题**,不用页面正文里的具体文案:后者随需求改,一改就红一片,
 * 而这组测试关心的从来只是"路由有没有挂对"。详情页在数据未就绪时没有标题,所以退而验它的
 * 加载态文案 —— 那同样是"路由挂对了"的充分信号。
 */
vi.mock('../api/auth')
vi.mock('../api/install')
vi.mock('../api/skills')
vi.mock('../api/tags')

function renderAt(path: string) {
  vi.mocked(authApi.fetchAuthProviders).mockResolvedValue({
    providers: ['LOCAL'],
    localRegisterEnabled: false,
  })
  vi.mocked(installApi.fetchInstallClients).mockResolvedValue([])
  vi.mocked(skillsApi.fetchSkillList).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })
  vi.mocked(tagsApi.fetchTags).mockResolvedValue([])
  // 详情刻意挂起不 resolve:这组测试只想确认 /skills/:id 落到了详情页,
  // 让它停在加载态就够了,给假数据反而要维护一份与页面耦合的 fixture。
  vi.mocked(skillsApi.fetchSkillDetail).mockReturnValue(new Promise(() => {}))
  vi.mocked(skillsApi.fetchSkillVersions).mockReturnValue(new Promise(() => {}))
  // 上传页是登录才可用的动作(权限矩阵 5.16),未登录时渲染的是引导登录而不是表单。
  // 这组测试要验的是"路由落到了上传页",所以给一个已登录态,让它渲染真正的页面。
  // 注意页面读的是 getAuthToken() 而不只是 /auth/me 的结果,光 mock 查询不够。
  setAuthToken('router-test-token')
  vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({
    id: 1,
    displayName: '测试用户',
    employeeNo: null,
    email: null,
    avatarUrl: null,
    role: 'USER',
    identities: [],
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AppRouter />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AppRouter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 用 findBy 而不是 getBy:上传页要等 /auth/me 落地才渲染表单(在那之前是"加载账号信息中"),
  // 同步断言会在 promise resolve 之前就跑完。列表页不需要等,但统一成异步免得下次有人加了
  // 前置查询又踩一遍。
  it.each([
    ['/', '浏览 skill'],
    ['/upload', '发布 skill'],
  ])('should_render_page_heading_when_path_is_%s', async (path, heading) => {
    renderAt(path)

    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
  })

  it('should_render_skill_detail_page_when_path_has_id', () => {
    renderAt('/skills/42')

    expect(screen.getByText('正在加载 skill 详情')).toBeInTheDocument()
  })

  it('should_render_install_page_when_path_is_/install', () => {
    // 购物车默认是空的(Zustand store 未被其它测试污染),空态是 install 路由本身的行为。
    renderAt('/install')

    expect(screen.getByText('安装单是空的')).toBeInTheDocument()
  })

  it('should_render_login_page_when_path_is_/login', () => {
    renderAt('/login')

    expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument()
  })

  it('should_wrap_every_page_with_app_layout_when_navigating', async () => {
    renderAt('/upload')

    // 布局导航与页面内容必须同时在场,证明五个页面都套进了 AppLayout,
    // 而不是路由表被改坏、页面内容跑没了。
    expect(screen.getByRole('link', { name: 'skill-hub' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { level: 1, name: '发布 skill' })).toBeInTheDocument()
  })
})
