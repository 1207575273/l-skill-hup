import { useState } from 'react'
import { Link } from 'react-router'
import { type ApiError, getAuthToken } from '../api/client'
import type { ManagedSkill } from '../api/types'
import { Button } from '../components/Button'
import { cx } from '../components/classNames'
import { DeleteSkillDialog } from '../components/DeleteSkillDialog'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { SkillStatusActions } from '../components/SkillStatusActions'
import { Spinner } from '../components/Spinner'
import { TextInput } from '../components/TextInput'
import { formatDateOnly } from '../components/textFormat'
import { useCurrentUser } from '../hooks/useAuthQueries'
import { useManagedSkills } from '../hooks/useSkillQueries'

const PAGE_SIZE = 20

/** `ALL` 只存在于前端:后端的约定是"不传 status 即两种都要",没有一个叫 ALL 的枚举值。 */
type StatusFilter = 'ALL' | ManagedSkill['status']

const STATUS_FILTER_OPTIONS: ReadonlyArray<{
  readonly value: StatusFilter
  readonly label: string
}> = [
  { value: 'ALL', label: '全部' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'UNLISTED', label: '已下架' },
]

/**
 * skill 管理页。
 *
 * <p><b>它解决的是一个真实的死角:下架之后 skill 从所有列表里消失,连作者自己都找不回来</b>
 * ——公开列表的 SQL 里 `status = 'PUBLISHED'` 是写死的,只剩直达链接一条路。所以管理视图走的是
 * 单独的 `GET /skills/manage`,那条接口强制登录、并且由服务端按身份收窄作用域。
 *
 * <p>做成表格而不是复用列表页的卡片:这里的任务是"找到某个 skill 并对它做处置",要的是状态一览
 * 与并排的操作按钮;卡片强调的是浏览与发现,两种目的对布局的要求正好相反。
 */
export default function ManagePage() {
  const currentUserQuery = useCurrentUser()
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  // 输入框的即时值与真正发出去的关键词分开:每敲一个字符就发一次请求,既浪费也会让列表闪。
  // 提交(回车 / 点搜索)时才把它同步过去。
  const [keywordInput, setKeywordInput] = useState('')
  const [submittedKeyword, setSubmittedKeyword] = useState('')
  const [pendingDelete, setPendingDelete] = useState<ManagedSkill | null>(null)

  // **必须先判 token,再判查询状态。** useCurrentUser 在没有 token 时 enabled: false,
  // 而被禁用的 query 在 TanStack Query 里恒为 pending —— 先判 isPending 的话,
  // 未登录用户会永远停在加载动画上,而不是被引导去登录。
  const hasToken = getAuthToken() !== null
  const isLoggedIn = hasToken && currentUserQuery.data !== undefined
  const managedQuery = useManagedSkills(
    {
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      keyword: submittedKeyword === '' ? undefined : submittedKeyword,
      page,
      size: PAGE_SIZE,
    },
    { enabled: isLoggedIn },
  )

  function applyStatusFilter(next: StatusFilter) {
    setStatusFilter(next)
    // 换筛选必须回到第 1 页:停在第 3 页而新结果只有 1 页,看到的是一片空白,
    // 而那看起来像"筛完什么都没有",不是"你还停在第 3 页"。
    setPage(1)
  }

  function submitKeyword() {
    setSubmittedKeyword(keywordInput.trim())
    setPage(1)
  }

  if (!hasToken || currentUserQuery.isError) {
    // token 缺失,与 token 存在但 /auth/me 失败(过期/失效)同样处理,不区分"两种没登录"——
    // 用户要做的下一步是一样的。
    return (
      <EmptyState
        title="请先登录"
        description="管理自己发布的 skill 需要登录。"
        action={
          <Link to="/login" className="text-brand-600 hover:underline dark:text-brand-500">
            去登录
          </Link>
        }
      />
    )
  }

  if (currentUserQuery.isPending) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" label="正在加载账号信息" />
      </div>
    )
  }

  const items = managedQuery.data?.items ?? []
  const total = managedQuery.data?.total ?? 0
  const hasNextPage = page * PAGE_SIZE < total

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">管理 skill</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {currentUserQuery.data?.role === 'ADMIN'
            ? '你是管理员,这里列出全部 skill(含已下架)。'
            : '这里列出你发布的 skill,含已下架的——它们不出现在浏览页。'}
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        {/* 一组互斥选项用 fieldset 而不是 div + role="group":后者是拿 ARIA 去补一个原生元素本来就有的语义,
            多一层需要保持同步的信息。aria-label 代替 legend —— 这里不需要可见的组标题。 */}
        <fieldset
          className="flex items-center gap-1 rounded-control border border-gray-200 p-1 dark:border-gray-700"
          aria-label="按状态筛选"
        >
          {STATUS_FILTER_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              // 用 aria-pressed 而不是只靠背景色表达选中:只有颜色的话,读屏软件读到的是三个一模一样的按钮。
              aria-pressed={statusFilter === option.value}
              onClick={() => applyStatusFilter(option.value)}
              className={cx(
                'rounded-control px-3 py-1 text-sm',
                statusFilter === option.value
                  ? 'bg-brand-600 text-white'
                  : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800',
              )}
            >
              {option.label}
            </button>
          ))}
        </fieldset>

        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            submitKeyword()
          }}
        >
          {/* 用 form + submit 而不是给输入框加 onKeyDown:回车提交是 form 的原生行为,
              自己监听按键还得处理输入法组合中的回车(中文输入时选词那一下也是回车)。 */}
          <TextInput
            label="搜索"
            placeholder="按名称或描述搜索"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
          <Button type="submit" variant="secondary" size="sm">
            搜索
          </Button>
        </form>
      </div>

      {managedQuery.isError && <ErrorBanner error={managedQuery.error as ApiError} />}

      {managedQuery.isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" label="正在加载 skill" />
        </div>
      )}

      {!managedQuery.isPending && items.length === 0 && (
        <EmptyState
          title="还没有 skill"
          description="发布一个 skill 之后,它会出现在这里。"
          action={
            <Link to="/upload" className="text-brand-600 hover:underline dark:text-brand-500">
              去发布
            </Link>
          }
        />
      )}

      {items.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-3xl border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-left text-gray-500 dark:border-gray-800 dark:text-gray-400">
                <th className="py-2 pr-4 font-medium">名称</th>
                <th className="py-2 pr-4 font-medium">状态</th>
                <th className="py-2 pr-4 font-medium">下载</th>
                <th className="py-2 pr-4 font-medium">更新时间</th>
                <th className="py-2 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((skill) => (
                <tr
                  key={skill.id}
                  className="border-b border-gray-100 last:border-b-0 dark:border-gray-800"
                >
                  <td className="py-3 pr-4">
                    <Link
                      to={`/skills/${skill.id}`}
                      className="font-medium text-gray-900 hover:text-brand-600 dark:text-gray-100 dark:hover:text-brand-500"
                    >
                      {skill.displayName}
                    </Link>
                    <p className="text-xs text-gray-500 dark:text-gray-400">{skill.name}</p>
                  </td>
                  <td className="py-3 pr-4">
                    <StatusBadge status={skill.status} />
                  </td>
                  <td className="py-3 pr-4 text-gray-600 dark:text-gray-300">
                    {skill.downloadCount}
                  </td>
                  <td className="py-3 pr-4 text-gray-600 dark:text-gray-300">
                    {formatDateOnly(skill.updatedAt)}
                  </td>
                  <td className="py-3">
                    <div className="flex items-center justify-end gap-2">
                      <SkillStatusActions skillId={skill.id} status={skill.status} />
                      <Button variant="danger" size="sm" onClick={() => setPendingDelete(skill)}>
                        删除
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(page > 1 || hasNextPage) && (
        <div className="flex items-center justify-center gap-3">
          <Button
            variant="secondary"
            size="sm"
            disabled={page <= 1}
            onClick={() => setPage((current) => current - 1)}
          >
            上一页
          </Button>
          <span className="text-sm text-gray-500 dark:text-gray-400">第 {page} 页</span>
          <Button
            variant="secondary"
            size="sm"
            disabled={!hasNextPage}
            onClick={() => setPage((current) => current + 1)}
          >
            下一页
          </Button>
        </div>
      )}

      {pendingDelete !== null && (
        <DeleteSkillDialog skill={pendingDelete} onClose={() => setPendingDelete(null)} />
      )}
    </div>
  )
}

function StatusBadge({ status }: { readonly status: ManagedSkill['status'] }) {
  if (status === 'UNLISTED') {
    return (
      <span className="rounded-control bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300">
        已下架
      </span>
    )
  }
  if (status === 'PUBLISHED') {
    // 深色态复用 gray-800 底 + brand-500 字:@theme 里没有 brand-950 / brand-400,
    // 而"缺哪个色就现场写 bg-[#...]"正是本项目禁的魔法值(frontend/CLAUDE.md 第四节)。
    return (
      <span className="rounded-control bg-brand-50 px-2 py-0.5 text-xs text-brand-700 dark:bg-gray-800 dark:text-brand-500">
        已发布
      </span>
    )
  }

  // 理由同 SkillStatusActions:认不出的值宁可显示成"未知"也不默认当成"已发布"。
  // 把未知状态渲染成一个具体状态,等于用界面替服务端做了一个它没做的断言。
  return (
    <span className="rounded-control bg-gray-100 px-2 py-0.5 text-xs text-gray-500 dark:bg-gray-800 dark:text-gray-400">
      未知
    </span>
  )
}
