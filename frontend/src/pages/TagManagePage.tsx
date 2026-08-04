import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError, getAuthToken } from '../api/client'
import type { Tag, TagGovernanceConflictDetails, TagKind } from '../api/types'
import { Button } from '../components/Button'
import { cx } from '../components/classNames'
import { DeleteTagDialog } from '../components/DeleteTagDialog'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { Modal } from '../components/Modal'
import { Spinner } from '../components/Spinner'
import { TagFormDialog, type TagFormValue } from '../components/TagFormDialog'
import { TextInput } from '../components/TextInput'
import { useCurrentUser } from '../hooks/useAuthQueries'
import { useCreateTag, useDeleteTag, useUpdateTag } from '../hooks/useTagMutations'
import { useTags } from '../hooks/useTagQueries'

/** `ALL` 只存在于前端:后端的约定是"不传 kind 即两种都要",没有一个叫 ALL 的枚举值。 */
type KindFilter = 'ALL' | TagKind

const KIND_FILTER_OPTIONS: ReadonlyArray<{ readonly value: KindFilter; readonly label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'CURATED', label: '分类(CURATED)' },
  { value: 'FREE', label: '自由标签(FREE)' },
]

const CONVERSION_NEEDS_CONFIRMATION = 'TAG_KIND_CONVERSION_NEEDS_CONFIRMATION'

function isConflictDetails(value: unknown): value is TagGovernanceConflictDetails {
  return typeof value === 'object' && value !== null
}

/** 从 409 的 `details` 里取受影响的 skill 名字。取不到就返回空数组,不编造内容。 */
function readAffectedSkills(error: unknown): readonly string[] {
  if (!(error instanceof ApiError) || !isConflictDetails(error.details)) {
    return []
  }
  const skills = error.details.affectedSkills
  return Array.isArray(skills) ? skills : []
}

/**
 * 标签治理页,ADMIN 专属(业务建模文档 5.7、6.2)。
 *
 * <p>它补的是一个真实缺口:在此之前 `/tags` 只有读接口,新增一个精选分类必须写一条 Flyway 迁移。
 *
 * <p><b>页面上最需要说清楚的是 kind 转换的两个方向不对称</b>:`FREE -> CURATED` 是硬拒绝
 * (会让某些 skill 的分类数溢出上限),`CURATED -> FREE` 是先告知再放行(那批 skill 会从导航消失,
 * 但 CURATED 下限已经放宽到 0,不构成非法状态)。两者都由服务端判定,页面只负责把结果讲明白。
 */
export default function TagManagePage() {
  const currentUserQuery = useCurrentUser()
  const [kindFilter, setKindFilter] = useState<KindFilter>('ALL')
  const [keyword, setKeyword] = useState('')
  const [editing, setEditing] = useState<Tag | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [deleting, setDeleting] = useState<Tag | null>(null)
  // 待确认的 CURATED -> FREE 转换。存下整份表单值,确认后原样重发一次带 confirmed 的请求 ——
  // 只存 tagId 的话,确认时得把用户刚填的名字/描述再猜一遍。
  const [pendingConversion, setPendingConversion] = useState<TagFormValue | null>(null)

  const tagsQuery = useTags()
  const createMutation = useCreateTag()
  const updateMutation = useUpdateTag()
  const deleteMutation = useDeleteTag()

  // **必须先判 token,再判查询状态。** 被禁用的 query 在 TanStack Query 里恒为 pending,
  // 先判 isPending 会让未登录用户永远停在加载动画上(与 ManagePage 同一条踩坑)。
  const hasToken = getAuthToken() !== null
  const isAdmin = currentUserQuery.data?.role === 'ADMIN'

  if (!hasToken || currentUserQuery.isError) {
    return (
      <EmptyState
        title="请先登录"
        description="标签治理是管理员功能。"
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

  if (!isAdmin) {
    return (
      <EmptyState
        title="需要管理员权限"
        description="只有管理员能建、改、删词条。你仍然可以在上传时自由打标签。"
      />
    )
  }

  const allTags = tagsQuery.data ?? []
  const normalizedKeyword = keyword.trim().toLowerCase()
  const visibleTags = allTags.filter((tag) => {
    const kindMatched = kindFilter === 'ALL' || tag.kind === kindFilter
    const keywordMatched =
      normalizedKeyword === '' || tag.name.toLowerCase().includes(normalizedKeyword)
    return kindMatched && keywordMatched
  })

  const migrationCandidates =
    deleting === null
      ? []
      : allTags.filter((tag) => tag.kind === deleting.kind && tag.id !== deleting.id)

  function openCreate() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(tag: Tag) {
    setEditing(tag)
    setFormOpen(true)
  }

  function closeForm() {
    setFormOpen(false)
    setEditing(null)
    createMutation.reset()
    updateMutation.reset()
  }

  function submitForm(value: TagFormValue) {
    if (editing === null) {
      createMutation.mutate(
        {
          name: value.name,
          description: value.description === '' ? undefined : value.description,
          sortOrder: value.sortOrder,
        },
        { onSuccess: closeForm },
      )
      return
    }
    submitUpdate(value, false)
  }

  function submitUpdate(value: TagFormValue, confirmed: boolean) {
    if (editing === null) {
      return
    }
    updateMutation.mutate(
      {
        id: editing.id,
        request: {
          name: value.name,
          kind: value.kind,
          description: value.description === '' ? undefined : value.description,
          sortOrder: value.sortOrder,
          confirmed,
        },
      },
      {
        onSuccess: () => {
          setPendingConversion(null)
          closeForm()
        },
        onError: (error) => {
          // 唯一一个"不是失败、是要你确认"的错误码。其余错误交给下面的 ErrorBanner 正常显示。
          if (error instanceof ApiError && error.code === CONVERSION_NEEDS_CONFIRMATION) {
            setPendingConversion(value)
          }
        },
      },
    )
  }

  function confirmDelete(migrateToTagId: number | undefined) {
    if (deleting === null) {
      return
    }
    deleteMutation.mutate(
      { id: deleting.id, request: migrateToTagId === undefined ? undefined : { migrateToTagId } },
      {
        onSuccess: () => {
          setDeleting(null)
          deleteMutation.reset()
        },
      },
    )
  }

  const formError =
    editing === null
      ? (createMutation.error as ApiError | null)
      : (updateMutation.error as ApiError | null)?.code === CONVERSION_NEEDS_CONFIRMATION
        ? null
        : (updateMutation.error as ApiError | null)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">标签治理</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            CURATED 是导航分类,由管理员维护;FREE 是用户上传时自由打的,失去全部引用后会被自动清理。
          </p>
        </div>
        <Button onClick={openCreate}>新建分类</Button>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <fieldset
          className="flex items-center gap-1 rounded-control border border-gray-200 p-1 dark:border-gray-700"
          aria-label="按类型筛选"
        >
          {KIND_FILTER_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={kindFilter === option.value}
              onClick={() => setKindFilter(option.value)}
              className={cx(
                'rounded-control px-3 py-1 text-sm',
                kindFilter === option.value
                  ? 'bg-brand-600 text-white'
                  : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800',
              )}
            >
              {option.label}
            </button>
          ))}
        </fieldset>

        {/* 本地过滤而不是发请求:标签就几十条,已经整份在手里了,再往返一次只会让输入变卡。 */}
        <TextInput
          label="搜索"
          placeholder="按名称过滤"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
      </div>

      {tagsQuery.isError && <ErrorBanner error={tagsQuery.error as ApiError} />}
      {deleteMutation.isError && <ErrorBanner error={deleteMutation.error as ApiError} />}

      {tagsQuery.isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" label="正在加载词条" />
        </div>
      )}

      {!tagsQuery.isPending && visibleTags.length === 0 && (
        <EmptyState title="没有匹配的词条" description="换个筛选条件,或新建一个分类。" />
      )}

      {visibleTags.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-3xl border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-left text-gray-500 dark:border-gray-800 dark:text-gray-400">
                <th className="py-2 pr-4 font-medium">名称</th>
                <th className="py-2 pr-4 font-medium">类型</th>
                <th className="py-2 pr-4 font-medium">描述</th>
                <th className="py-2 pr-4 font-medium">排序</th>
                <th className="py-2 pr-4 font-medium">skill 数</th>
                <th className="py-2 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {visibleTags.map((tag) => (
                <tr
                  key={tag.id}
                  className="border-b border-gray-100 last:border-b-0 dark:border-gray-800"
                >
                  <td className="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">
                    {tag.name}
                  </td>
                  <td className="py-3 pr-4">
                    <span
                      className={cx(
                        'rounded-control px-2 py-0.5 text-xs',
                        tag.kind === 'CURATED'
                          ? 'bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-400'
                          : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
                      )}
                    >
                      {tag.kind}
                    </span>
                  </td>
                  <td className="py-3 pr-4 text-gray-600 dark:text-gray-400">
                    {tag.description ?? '-'}
                  </td>
                  <td className="py-3 pr-4 text-gray-600 dark:text-gray-400">{tag.sortOrder}</td>
                  <td className="py-3 pr-4 text-gray-600 dark:text-gray-400">{tag.skillCount}</td>
                  <td className="py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <Button variant="secondary" size="sm" onClick={() => openEdit(tag)}>
                        编辑
                      </Button>
                      <Button variant="danger" size="sm" onClick={() => setDeleting(tag)}>
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

      {formError !== null && <ErrorBanner error={formError} />}

      <TagFormDialog
        open={formOpen}
        tag={editing}
        submitting={createMutation.isPending || updateMutation.isPending}
        onClose={closeForm}
        onSubmit={submitForm}
      />

      {/* CURATED -> FREE 的知情确认。服务端第一次用 409 把受影响清单送回来,这里摆出来让 ADMIN 看过再决定
          (业务建模文档 5.7:从"拒绝"降级为"告知")。 */}
      <Modal
        open={pendingConversion !== null}
        title="确认转成 FREE?"
        onClose={() => setPendingConversion(null)}
        actions={[
          {
            label: '我知道了,继续',
            variant: 'danger',
            loading: updateMutation.isPending,
            onClick: () => {
              if (pendingConversion !== null) {
                submitUpdate(pendingConversion, true)
              }
            },
          },
          { label: '取消', onClick: () => setPendingConversion(null), variant: 'ghost' },
        ]}
      >
        <div className="flex flex-col gap-3">
          <p className="text-sm text-gray-700 dark:text-gray-300">
            {(updateMutation.error as ApiError | null)?.message ??
              '这些 skill 会不再出现在这个分类下。'}
          </p>
          {readAffectedSkills(updateMutation.error).length > 0 && (
            <ul className="max-h-48 overflow-y-auto rounded-control bg-gray-50 p-3 text-sm text-gray-600 dark:bg-gray-900 dark:text-gray-400">
              {readAffectedSkills(updateMutation.error).map((skillName) => (
                <li key={skillName}>{skillName}</li>
              ))}
            </ul>
          )}
        </div>
      </Modal>

      <DeleteTagDialog
        open={deleting !== null}
        tag={deleting}
        migrationCandidates={migrationCandidates}
        submitting={deleteMutation.isPending}
        onClose={() => {
          setDeleting(null)
          deleteMutation.reset()
        }}
        onConfirm={confirmDelete}
      />
    </div>
  )
}
