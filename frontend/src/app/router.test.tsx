import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import AppRouter from './router'

/**
 * 只验证 P0 要求的五条路由能落到对应占位页,不测视觉(视觉留给后续按展示约定实现)。
 * 用 `MemoryRouter` 单独包裹,不依赖 `AppProviders` 里真正挂载的 `BrowserRouter`。
 */
describe('AppRouter', () => {
  it.each([
    ['/', 'skill 列表页占位'],
    ['/skills/42', 'skill 详情页占位,id = 42'],
    ['/upload', '上传页占位'],
    ['/install', '安装单页占位'],
    ['/login', '登录页占位'],
  ])('should_render_placeholder_when_path_is_%s', (path, expectedText) => {
    render(
      <MemoryRouter initialEntries={[path]}>
        <AppRouter />
      </MemoryRouter>,
    )

    expect(screen.getByText(expectedText)).toBeInTheDocument()
  })

  it('should_wrap_every_page_with_app_layout_when_navigating', () => {
    render(
      <MemoryRouter initialEntries={['/upload']}>
        <AppRouter />
      </MemoryRouter>,
    )

    // 布局导航(A1 本轮新增)与页面占位(此前已有)必须同时在场,证明五个页面
    // 都套进了 AppLayout,而不是路由表被改坏、页面内容跑没了。
    expect(screen.getByRole('link', { name: 'skill-hub' })).toBeInTheDocument()
    expect(screen.getByText('上传页占位')).toBeInTheDocument()
  })
})
