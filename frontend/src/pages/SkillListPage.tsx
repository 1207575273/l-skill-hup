import { useMemo } from 'react'
import { useSearchParams } from 'react-router'
import type { ApiError } from '../api/client'
import type { SkillListFilter, SkillSortOption, SkillSource, SkillSummary } from '../api/types'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { QuickInstallMenu } from '../components/QuickInstallMenu'
import { SkillCard } from '../components/SkillCard'
import { SkillFilterPanel } from '../components/SkillFilterPanel'
import { Spinner } from '../components/Spinner'
import { useInstallCartStore } from '../hooks/useInstallCartStore'
import { useSkillList } from '../hooks/useSkillQueries'
import { useTags } from '../hooks/useTagQueries'

/**
 * skill 列表页。展示约定见 `docs/20260802224934_业务建模与接口契约.md` 第七章,
 * 接口筛选参数见第 6.2 节的 `GET /skills`。
 *
 * **筛选状态的真相在 URL query string,不是组件 state**:刷新不丢、能把筛选结果的
 * 链接直接甩给同事——这是这个平台的核心场景之一(浏览匿名可用就是为了支撑它)。
 * 组件内只做"URL <-> `SkillListFilter`"的解析/序列化,不额外维护一份筛选状态。
 */

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 20
const MAX_PAGE_SIZE = 100
const DEFAULT_SORT: SkillSortOption = 'latest'

const SKILL_SOURCES: readonly SkillSource[] = ['INTERNAL', 'COMMUNITY']
const SKILL_SORT_OPTIONS: readonly SkillSortOption[] = ['downloads', 'likes', 'latest']

function isSkillSource(value: string | null): value is SkillSource {
  return value !== null && (SKILL_SOURCES as readonly string[]).includes(value)
}

function isSkillSortOption(value: string | null): value is SkillSortOption {
  return value !== null && (SKILL_SORT_OPTIONS as readonly string[]).includes(value)
}

function parsePositiveInt(value: string | null, fallback: number): number {
  if (value === null) {
    return fallback
  }
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function parseTagIds(searchParams: URLSearchParams): number[] {
  return searchParams
    .getAll('tagIds')
    .map((raw) => Number.parseInt(raw, 10))
    .filter((id) => Number.isFinite(id) && id > 0)
}

/** URL query string -> `SkillListFilter`。缺省值不写进返回对象里没意义的字段,统一走 `undefined`。 */
function parseFilterFromSearchParams(searchParams: URLSearchParams): SkillListFilter {
  const tagIds = parseTagIds(searchParams)
  const rawSource = searchParams.get('source')
  const rawSort = searchParams.get('sort')
  const rawKeyword = searchParams.get('keyword')

  return {
    source: isSkillSource(rawSource) ? rawSource : undefined,
    tagIds: tagIds.length > 0 ? tagIds : undefined,
    certifiedOnly: searchParams.get('certifiedOnly') === 'true' || undefined,
    keyword: rawKeyword && rawKeyword.trim() !== '' ? rawKeyword : undefined,
    sort: isSkillSortOption(rawSort) ? rawSort : undefined,
    page: parsePositiveInt(searchParams.get('page'), DEFAULT_PAGE),
    size: clamp(parsePositiveInt(searchParams.get('size'), DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE),
  }
}

/**
 * `SkillListFilter` -> URL query string。只写非默认值,保证无筛选时地址栏是干净的 `/`,
 * 分享出去的链接里每一个参数都代表一条真实生效的筛选条件。
 */
function buildSearchParams(filter: SkillListFilter): URLSearchParams {
  const params = new URLSearchParams()

  if (filter.source) {
    params.set('source', filter.source)
  }
  for (const tagId of filter.tagIds ?? []) {
    params.append('tagIds', String(tagId))
  }
  if (filter.certifiedOnly) {
    params.set('certifiedOnly', 'true')
  }
  if (filter.keyword) {
    params.set('keyword', filter.keyword)
  }
  if (filter.sort && filter.sort !== DEFAULT_SORT) {
    params.set('sort', filter.sort)
  }
  if (filter.page && filter.page > DEFAULT_PAGE) {
    params.set('page', String(filter.page))
  }
  if (filter.size && filter.size !== DEFAULT_PAGE_SIZE) {
    params.set('size', String(filter.size))
  }

  return params
}

export default function SkillListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = useMemo(() => parseFilterFromSearchParams(searchParams), [searchParams])

  const skillListQuery = useSkillList(filter)
  const tagsQuery = useTags()

  // 只订阅 items,不整个 store 取出来——否则切客户端 / 安装级别也会让整页列表重渲染
  // (frontend/CLAUDE.md 第三节的 selector 约束)。
  const cartItems = useInstallCartStore((state) => state.items)
  const toggleCartItem = useInstallCartStore((state) => state.toggleItem)
  const cartSkillIds = useMemo(() => new Set(cartItems.map((item) => item.skillId)), [cartItems])

  const curatedTags = useMemo(
    () => tagsQuery.data?.filter((tag) => tag.kind === 'CURATED') ?? [],
    [tagsQuery.data],
  )
  const freeTags = useMemo(
    () => tagsQuery.data?.filter((tag) => tag.kind === 'FREE') ?? [],
    [tagsQuery.data],
  )

  /** 任何筛选条件变化都把 page 归 1——上一批筛选结果的第 3 页对新条件通常没有意义。 */
  function applyFilter(patch: Partial<SkillListFilter>) {
    setSearchParams(buildSearchParams({ ...filter, ...patch, page: DEFAULT_PAGE }), {
      replace: true,
    })
  }

  function handleToggleTag(tagId: number) {
    const current = filter.tagIds ?? []
    const next = current.includes(tagId)
      ? current.filter((id) => id !== tagId)
      : [...current, tagId]
    applyFilter({ tagIds: next.length > 0 ? next : undefined })
  }

  function handlePageChange(page: number) {
    // 翻页保留在浏览历史里(可用浏览器"后退"回到上一页),不用 replace。
    setSearchParams(buildSearchParams({ ...filter, page }))
  }

  function handleReset() {
    setSearchParams(new URLSearchParams(), { replace: true })
  }

  /**
   * 加入 / 移出安装单。
   *
   * <p>没有任何版本的 skill(`latestVersionId` 为 null)直接忽略 —— 安装单的每一项都要求
   * `versionId`,拿不到就没法生成安装命令。这种数据在正常流程里不该出现(发布必然带一个版本),
   * 但列表响应的类型允许它,静默忽略比塞一个假 id 进去安全。
   */
  function handleToggleInstallCart(skill: SkillSummary) {
    if (skill.latestVersionId === null) {
      return
    }
    toggleCartItem({
      skillId: skill.id,
      versionId: skill.latestVersionId,
      skillDisplayName: skill.displayName,
    })
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">浏览 skill</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400">
          按分类、标签、来源筛选,或直接搜索关键词。
        </p>
      </div>

      <SkillFilterPanel
        keyword={filter.keyword ?? ''}
        source={filter.source ?? ''}
        sort={filter.sort ?? DEFAULT_SORT}
        certifiedOnly={filter.certifiedOnly ?? false}
        curatedTags={curatedTags}
        freeTags={freeTags}
        selectedTagIds={filter.tagIds ?? []}
        onKeywordChange={(keyword) => applyFilter({ keyword: keyword || undefined })}
        onSourceChange={(source) => applyFilter({ source: source || undefined })}
        onSortChange={(sort) => applyFilter({ sort })}
        onCertifiedOnlyChange={(certifiedOnly) =>
          applyFilter({ certifiedOnly: certifiedOnly || undefined })
        }
        onToggleTag={handleToggleTag}
        onReset={handleReset}
      />

      {skillListQuery.isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" label="正在加载 skill 列表" />
        </div>
      )}

      {skillListQuery.isError && (
        <ErrorBanner
          error={skillListQuery.error as ApiError}
          onRetry={() => skillListQuery.refetch()}
        />
      )}

      {skillListQuery.isSuccess && skillListQuery.data.items.length === 0 && (
        <EmptyState
          title="没有找到匹配的 skill"
          description="试试调整筛选条件,或换个关键词搜索"
          action={
            <Button variant="secondary" size="sm" onClick={handleReset}>
              清空筛选条件
            </Button>
          }
        />
      )}

      {skillListQuery.isSuccess && skillListQuery.data.items.length > 0 && (
        <>
          <ul
            aria-label="skill 列表"
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
          >
            {skillListQuery.data.items.map((skill) => (
              // `h-full` 要一路传到 li:grid 的直接子项是 li,卡片的 h-full 参照的是 li 的高度,
              // li 自己不撑满的话,卡片再写 h-full 也只是撑满一个矮盒子。
              <li key={skill.id} className="h-full">
                <SkillCard
                  skill={skill}
                  detailHref={`/skills/${skill.id}`}
                  quickInstallMenu={<QuickInstallMenu skillId={skill.id} />}
                  inInstallCart={cartSkillIds.has(skill.id)}
                  onToggleInstallCart={() => handleToggleInstallCart(skill)}
                />
              </li>
            ))}
          </ul>
          <Pagination
            page={skillListQuery.data.page}
            size={skillListQuery.data.size}
            total={skillListQuery.data.total}
            onPageChange={handlePageChange}
          />
        </>
      )}
    </div>
  )
}
