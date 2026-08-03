import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { SkillSummary } from '../api/types'
import { SkillCard } from './SkillCard'

function buildSkill(overrides: Partial<SkillSummary> = {}): SkillSummary {
  return {
    // 字段严格按 SkillSummary(即后端 SkillSummaryResponse)。**列表项不是详情的子集**:
    // 没有 description / source / status / dislikeCount / commentCount,也没有版本号字段。
    // 这里的 ...overrides 展开会关掉顶层的多余属性检查,所以多写的字段 tsc 抓不到 —— 手写时要对着
    // backend/.../skill/SkillSummaryResponse.java 核,不能凭"详情有所以列表也该有"来推。
    id: 42,
    name: 'pdf-summarizer',
    displayName: 'PDF 摘要助手',
    cardExcerpt: 'a'.repeat(120),
    downloadCount: 88,
    likeCount: 12,
    favoriteCount: 5,
    owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' },
    tags: [
      { id: 1, name: '效率工具', kind: 'CURATED', description: null, sortOrder: 1 },
      { id: 2, name: 'pdf', kind: 'FREE', description: null, sortOrder: 1 },
      { id: 3, name: '文档', kind: 'FREE', description: null, sortOrder: 2 },
      { id: 4, name: '摘要', kind: 'FREE', description: null, sortOrder: 3 },
      { id: 5, name: '办公', kind: 'FREE', description: null, sortOrder: 4 },
    ],
    certification: 'CERTIFIED',
    updatedAt: '2026-07-20T08:00:00Z',
    ...overrides,
  }
}

function renderCard(skill: SkillSummary) {
  return render(
    <MemoryRouter>
      <SkillCard
        skill={skill}
        detailHref={`/skills/${skill.id}`}
        quickInstallMenu={<button type="button">claude · global</button>}
      />
    </MemoryRouter>,
  )
}

describe('SkillCard', () => {
  it('should_render_display_name_and_kebab_case_name_when_rendered', () => {
    renderCard(buildSkill())

    expect(screen.getByRole('heading', { name: 'PDF 摘要助手' })).toBeInTheDocument()
    expect(screen.getByText('pdf-summarizer')).toBeInTheDocument()
  })

  it('should_render_certification_badge_when_certified', () => {
    renderCard(buildSkill({ certification: 'CERTIFIED' }))

    expect(screen.getByText('平台认证')).toBeInTheDocument()
  })

  it('should_render_no_badge_when_certification_none', () => {
    renderCard(buildSkill({ certification: 'NONE' }))

    expect(screen.queryByText('平台认证')).not.toBeInTheDocument()
    expect(screen.queryByText('认证已撤销')).not.toBeInTheDocument()
  })

  it('should_render_stats_row_when_rendered', () => {
    renderCard(buildSkill())

    expect(screen.getByText('下载 88')).toBeInTheDocument()
    expect(screen.getByText('赞 12')).toBeInTheDocument()
    expect(screen.getByText('收藏 5')).toBeInTheDocument()
  })

  it('should_truncate_card_excerpt_and_append_ellipsis_when_excerpt_longer_than_limit', () => {
    renderCard(buildSkill({ cardExcerpt: 'a'.repeat(120) }))

    expect(screen.getByText(`${'a'.repeat(100)}…`)).toBeInTheDocument()
  })

  it('should_show_first_four_tags_and_overflow_count_when_more_than_four_tags', () => {
    renderCard(buildSkill())

    expect(screen.getByText('效率工具')).toBeInTheDocument()
    expect(screen.getByText('#pdf')).toBeInTheDocument()
    expect(screen.getByText('#文档')).toBeInTheDocument()
    expect(screen.getByText('#摘要')).toBeInTheDocument()
    expect(screen.queryByText('#办公')).not.toBeInTheDocument()
    expect(screen.getByText('+1')).toHaveAttribute('title', '办公')
  })

  it('should_show_employee_no_when_owner_has_one', () => {
    renderCard(buildSkill({ owner: { userId: 1, displayName: '张三', employeeNo: 'E1024' } }))

    expect(screen.getByText(/@张三\(E1024\)/)).toBeInTheDocument()
  })

  it('should_omit_employee_no_when_owner_has_none', () => {
    renderCard(buildSkill({ owner: { userId: 1, displayName: '张三', employeeNo: null } }))

    expect(screen.getByText(/^@张三 ·/)).toBeInTheDocument()
  })

  it('should_degrade_to_unknown_owner_when_owner_is_null', () => {
    // 后端 SkillOwnerResponse.from 对 null owner 返回 null,所以这个形态在类型上是可达的。
    // 库里 owner_user_id 是 NOT NULL,所以真出现只可能是数据异常 —— 卡片要降级不要崩,
    // 一条元数据缺失不该让整个列表白屏。
    renderCard(buildSkill({ owner: null }))

    expect(screen.getByText(/@归属未知/)).toBeInTheDocument()
  })

  it('should_show_date_only_when_rendered', () => {
    renderCard(buildSkill({ updatedAt: '2026-07-20T08:00:00Z' }))

    expect(screen.getByText(/2026-07-20/)).toBeInTheDocument()
  })

  it('should_link_to_detail_href_when_view_detail_clicked', () => {
    renderCard(buildSkill({ id: 42 }))

    expect(screen.getByRole('link', { name: '查看详情' })).toHaveAttribute('href', '/skills/42')
  })

  it('should_show_injected_quick_install_menu_when_trigger_clicked', () => {
    renderCard(buildSkill())

    fireEvent.click(screen.getByRole('button', { name: /快速接入/ }))

    expect(screen.getByRole('button', { name: 'claude · global' })).toBeInTheDocument()
  })
})
