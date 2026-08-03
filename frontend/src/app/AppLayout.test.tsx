import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AppLayout } from './AppLayout'

describe('AppLayout', () => {
  it('should_link_site_name_to_home_when_rendered', () => {
    render(
      <MemoryRouter>
        <AppLayout>内容</AppLayout>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'skill-hub' })).toHaveAttribute('href', '/')
  })

  it('should_render_browse_and_upload_nav_entries_when_rendered', () => {
    render(
      <MemoryRouter>
        <AppLayout>内容</AppLayout>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: '浏览' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: '上传' })).toHaveAttribute('href', '/upload')
  })

  it('should_render_children_in_main_content_area_when_rendered', () => {
    render(
      <MemoryRouter>
        <AppLayout>
          <p>skill 列表页占位</p>
        </AppLayout>
      </MemoryRouter>,
    )

    expect(screen.getByRole('main')).toHaveTextContent('skill 列表页占位')
  })

  it('should_render_default_login_placeholder_when_authSlot_not_provided', () => {
    render(
      <MemoryRouter>
        <AppLayout>内容</AppLayout>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: '登录' })).toHaveAttribute('href', '/login')
  })

  it('should_render_custom_authSlot_when_provided', () => {
    render(
      <MemoryRouter>
        <AppLayout authSlot={<span>@张三</span>}>内容</AppLayout>
      </MemoryRouter>,
    )

    expect(screen.getByText('@张三')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument()
  })
})
