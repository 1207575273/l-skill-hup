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
})
