import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { SkillSummary } from '../api/types'
import { pickAvatarColor } from './avatarColor'
import { Button } from './Button'
import { CertificationBadge } from './CertificationBadge'
import { Dropdown } from './Dropdown'
import { ChevronDownIcon } from './icons'
import { TagChip } from './TagChip'
import { formatDateOnly, truncateWithEllipsis } from './textFormat'

export interface SkillCardProps {
  readonly skill: SkillSummary
  /** 通常是 `/skills/${skill.id}`,由调用方拼,组件本身不猜路由结构。 */
  readonly detailHref: string
  /**
   * "快速接入"下拉的菜单内容(展示约定 7.4:客户端/安装级别选项来自 `GET /clients`)。
   * 本组件只负责展开/收起这个下拉,不知道也不获取选项数据 —— 那是调用方的 hook 的活。
   */
  readonly quickInstallMenu: ReactNode
}

/** 摘要截断长度(展示约定 7.1):后端已截到 200 字符,前端再截到 100 并自行加省略号。 */
const CARD_EXCERPT_MAX_LENGTH = 100
/** 标签行超过这个数量就折叠成 "+N",hover(用原生 title)展开看完整名单。 */
const MAX_VISIBLE_TAGS = 4

function sortTagsForDisplay(tags: SkillSummary['tags']): SkillSummary['tags'] {
  return [...tags].sort((a, b) => {
    if (a.kind !== b.kind) {
      return a.kind === 'CURATED' ? -1 : 1
    }
    return a.sortOrder - b.sortOrder
  })
}

/**
 * 归属人展示。`owner` 可能为 null —— 库里 `owner_user_id` 是 NOT NULL(系统内置的挂在 id=1 的 system
 * 账号上),所以 null 只可能是数据异常。**降级成"归属未知"而不是崩掉**:一条元数据缺失不该让整个列表白屏,
 * 而且这个文案本身就是异常的信号,比渲染出 `@undefined` 更容易被发现。
 */
function formatOwnerLabel(owner: SkillSummary['owner']): string {
  if (owner === null) {
    return '@归属未知'
  }
  return owner.employeeNo ? `@${owner.displayName}(${owner.employeeNo})` : `@${owner.displayName}`
}

/**
 * skill 列表卡片,信息密度按展示约定 7.1 的七要素铺齐:头像色块、中文名+英文名、
 * 认证徽章、统计行、标签行、摘要、`@归属人 + 更新日期`。hover 时露出"查看详情"与
 * "快速接入"两个操作(7.4),键盘 focus 时也会露出(`focus-within`),不是只有鼠标可用。
 */
export function SkillCard({ skill, detailHref, quickInstallMenu }: SkillCardProps) {
  const avatarInitial = skill.displayName.charAt(0) || skill.name.charAt(0)
  const avatarColor = pickAvatarColor(skill.name)
  const sortedTags = sortTagsForDisplay(skill.tags)
  const visibleTags = sortedTags.slice(0, MAX_VISIBLE_TAGS)
  const overflowTags = sortedTags.slice(MAX_VISIBLE_TAGS)
  const excerpt = truncateWithEllipsis(skill.cardExcerpt, CARD_EXCERPT_MAX_LENGTH)

  return (
    <article className="group flex flex-col gap-3 rounded-card border border-gray-200 bg-white p-4 transition-shadow hover:shadow-md dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-control text-sm font-semibold text-white"
            style={{ backgroundColor: avatarColor }}
          >
            {avatarInitial}
          </span>
          <div>
            <h3 className="text-base font-semibold text-gray-900 dark:text-gray-100">
              {skill.displayName}
            </h3>
            <p className="text-xs text-gray-500 dark:text-gray-400">{skill.name}</p>
          </div>
        </div>
        <CertificationBadge certification={skill.certification} />
      </div>

      <p className="text-sm text-gray-600 dark:text-gray-300">{excerpt}</p>

      <ul aria-label="标签" className="flex flex-wrap items-center gap-1.5">
        {visibleTags.map((tag) => (
          <li key={tag.id}>
            <TagChip tag={tag} />
          </li>
        ))}
        {overflowTags.length > 0 && (
          <li
            className="text-xs text-gray-400 dark:text-gray-500"
            title={overflowTags.map((tag) => tag.name).join('、')}
          >
            +{overflowTags.length}
          </li>
        )}
      </ul>

      <p className="flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
        <span>下载 {skill.downloadCount}</span>
        <span>赞 {skill.likeCount}</span>
        <span>收藏 {skill.favoriteCount}</span>
      </p>

      <div className="flex items-center justify-between gap-2 border-t border-gray-100 pt-3 dark:border-gray-800">
        <p className="text-xs text-gray-500 dark:text-gray-400">
          {formatOwnerLabel(skill.owner)} · {formatDateOnly(skill.updatedAt)}
        </p>
        <div className="flex items-center gap-2 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
          <Link
            to={detailHref}
            className="inline-flex h-8 items-center rounded-control border border-gray-300 px-3 text-sm text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
          >
            查看详情
          </Link>
          <Dropdown
            renderTrigger={({ onClick, open }) => (
              <Button
                variant="primary"
                size="sm"
                iconRight={<ChevronDownIcon className="h-4 w-4" />}
                onClick={onClick}
                aria-haspopup="menu"
                aria-expanded={open}
              >
                快速接入
              </Button>
            )}
          >
            {quickInstallMenu}
          </Dropdown>
        </div>
      </div>
    </article>
  )
}
