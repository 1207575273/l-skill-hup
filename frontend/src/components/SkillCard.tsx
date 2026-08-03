import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { SkillSummary } from '../api/types'
import { pickAvatarColor } from './avatarColor'
import { CertificationBadge } from './CertificationBadge'
import { TagChip } from './TagChip'
import { formatDateOnly, truncateWithEllipsis } from './textFormat'

export interface SkillCardProps {
  readonly skill: SkillSummary
  /** 通常是 `/skills/${skill.id}`,由调用方拼,组件本身不猜路由结构。 */
  readonly detailHref: string
  /**
   * "快速接入"操作(展示约定 7.4),通常传 `<QuickInstallMenu skillId={skill.id} />`。
   *
   * <p><b>整个操作由调用方提供,卡片不包下拉外壳。</b>初版是"卡片提供下拉、这里只传面板内容",
   * 结果与自带下拉的 `QuickInstallMenu` 叠成两层,用户要点两次"快速接入"才看到客户端列表 ——
   * 那正好把 7.4 想省掉的点击又加了回来。卡片对它是什么一无所知,只负责摆在操作行里。
   */
  readonly quickInstallMenu: ReactNode
  /** 这个 skill 是否已在安装单里。决定按钮文案,也决定它常显还是只在 hover 时浮出。 */
  readonly inInstallCart: boolean
  /**
   * 加入 / 移出安装单。
   *
   * <p>卡片自己不碰 Zustand:store 消费归页面层(frontend/CLAUDE.md 第二节,`components/` 只接收 props 渲染)。
   * 这样卡片在测试里不需要真的搭一个 store,给个 `vi.fn()` 就能验交互。
   */
  readonly onToggleInstallCart: () => void
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
export function SkillCard({
  skill,
  detailHref,
  quickInstallMenu,
  inInstallCart,
  onToggleInstallCart,
}: SkillCardProps) {
  const avatarInitial = skill.displayName.charAt(0) || skill.name.charAt(0)
  const avatarColor = pickAvatarColor(skill.name)
  const sortedTags = sortTagsForDisplay(skill.tags)
  const visibleTags = sortedTags.slice(0, MAX_VISIBLE_TAGS)
  const overflowTags = sortedTags.slice(MAX_VISIBLE_TAGS)
  const excerpt = truncateWithEllipsis(skill.cardExcerpt, CARD_EXCERPT_MAX_LENGTH)

  // `h-full` + 父级 grid 默认的 items-stretch 让同一行的卡片等高。
  //
  // 光靠内容自然高度做不到:摘要有一行有两行、标签有的换行有的不换,同一行卡片必然参差不齐。
  // 配合下面三处固定 —— 摘要固定两行、标签行固定一行、操作行 mt-auto 推到底部。
  // **只做等高不固定内容行数是没用的**:卡片被拉到一样高之后,内容仍停在各自的自然位置,
  // 底部操作行会散落在不同高度上,看起来比不等高还乱。
  return (
    <article className="group flex h-full flex-col gap-3 rounded-card border border-gray-200 bg-white p-4 transition-shadow hover:shadow-md dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 items-start gap-3">
          <span
            aria-hidden="true"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-control text-sm font-semibold text-white"
            style={{ backgroundColor: avatarColor }}
          >
            {avatarInitial}
          </span>
          {/* min-w-0 是必须的:flex 子项默认 min-width:auto,长英文名不会换行而是把卡片撑破 */}
          <div className="min-w-0">
            <h3 className="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
              {skill.displayName}
            </h3>
            <p className="truncate text-xs text-gray-500 dark:text-gray-400">{skill.name}</p>
          </div>
        </div>
        <CertificationBadge certification={skill.certification} />
      </div>

      {/* 固定两行高度:摘要长短不一时,卡片之间的后续内容才不会错位 */}
      <p className="line-clamp-2 min-h-10 text-sm text-gray-600 dark:text-gray-300">{excerpt}</p>

      {/* 标签只占一行,超出的已经折叠成 "+N",这里再加 overflow-hidden 兜住极端长标签 */}
      <ul aria-label="标签" className="flex h-6 flex-wrap items-center gap-1.5 overflow-hidden">
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

      {/*
        `mt-auto` 把整块推到卡片底部。等高之后如果不推,短内容卡片的操作行会浮在半空,
        一行里几张卡的按钮高度各不相同,比不等高更难看。

        归属信息与操作**分成上下两行**,不再挤在同一行左右两端。三个按钮("加入安装单"/"查看详情"/
        "快速接入")加起来接近 240px,而三列栅格下卡片内容区只有 200 出头 —— 挤一行必然溢出。
        按钮行 `flex-wrap` 兜住窄屏,`justify-end` 保证不满一行时靠右对齐。
      */}
      <div className="mt-auto flex flex-col gap-2 border-t border-gray-100 pt-3 dark:border-gray-800">
        <p className="truncate text-xs text-gray-500 dark:text-gray-400">
          {formatOwnerLabel(skill.owner)} · {formatDateOnly(skill.updatedAt)}
        </p>
        <div className="flex flex-wrap items-center justify-end gap-2">
          {/*
            已加入的按钮**放在 hover 容器外面**,常显。
            "已加入"是状态不是操作 —— 藏进 hover 里的话,用户扫一眼列表看不出自己勾了哪几个,
            只能逐个悬停去试,那正好抵消了"攒一批再一次装完"这个功能的意义。
            未加入时它跟其他操作一样躲在 hover 里,不给未选中的卡片增加视觉噪音。
          */}
          {inInstallCart ? (
            <button
              type="button"
              onClick={onToggleInstallCart}
              className="inline-flex h-8 shrink-0 items-center rounded-control border border-brand-500 bg-brand-50 px-3 text-sm text-brand-700 hover:bg-brand-100 dark:border-brand-500 dark:bg-transparent dark:text-brand-500"
            >
              已加入安装单
            </button>
          ) : null}
          <div className="flex flex-wrap items-center justify-end gap-2 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
            {inInstallCart ? null : (
              <button
                type="button"
                onClick={onToggleInstallCart}
                className="inline-flex h-8 shrink-0 items-center rounded-control border border-gray-300 px-3 text-sm text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
              >
                加入安装单
              </button>
            )}
            <Link
              to={detailHref}
              className="inline-flex h-8 shrink-0 items-center rounded-control border border-gray-300 px-3 text-sm text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
            >
              查看详情
            </Link>
            {quickInstallMenu}
          </div>
        </div>
      </div>
    </article>
  )
}
