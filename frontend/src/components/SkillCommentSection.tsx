import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { type ApiError, getAuthToken } from '../api/client'
import type { CurrentUser, SkillCommentItem } from '../api/types'
import { useAddSkillComment, useDeleteSkillComment } from '../hooks/useSkillMutations'
import { useSkillComments } from '../hooks/useSkillQueries'
import { Button } from './Button'
import { ErrorBanner } from './ErrorBanner'
import { Spinner } from './Spinner'
import { formatDateTime } from './textFormat'

export interface SkillCommentSectionProps {
  readonly skillId: number
  /** 未登录时为 `undefined`。决定能不能发表,以及哪些评论上出现删除按钮。 */
  readonly currentUser: CurrentUser | undefined
}

const DELETED_PLACEHOLDER = '该评论已删除'
const MAX_CONTENT_LENGTH = 2000

/**
 * skill 详情页的评论区(业务建模文档 5.11)。
 *
 * <p><b>正文一律按纯文本渲染,绝不走 Markdown。</b>这是 5.11 的硬要求,不是省事:渲染用户内容是 XSS 的
 * 主要入口,而这个平台本来就在分发会被 Agent 执行的东西,不该再增加攻击面。React 的 `{content}` 天然转义,
 * 所以这里只需要**不做额外的事** —— 具体说就是不碰 `dangerouslySetInnerHTML`、不调
 * `renderMarkdownToSafeHtml`(那个是给 SKILL.md / README 正文用的,两者的信任级别完全不同)。
 * `whitespace-pre-wrap` 保留换行,让多段评论仍然可读。
 *
 * <p><b>只做一层回复</b>,与服务端的不变量一致。回复框挂在顶级评论上,回复的回复没有入口 ——
 * 界面上做不出来的操作,就不会有人去试。
 */
export function SkillCommentSection({ skillId, currentUser }: SkillCommentSectionProps) {
  const [page, setPage] = useState(1)
  const [replyingTo, setReplyingTo] = useState<number | null>(null)

  const commentsQuery = useSkillComments(skillId, page)
  const isLoggedIn = getAuthToken() !== null && currentUser !== undefined

  const items = commentsQuery.data?.items ?? []
  const total = commentsQuery.data?.total ?? 0
  const size = commentsQuery.data?.size ?? 20
  const hasNextPage = page * size < total

  return (
    <section aria-labelledby="skill-comments-heading" className="flex flex-col gap-4">
      <h2
        id="skill-comments-heading"
        className="text-lg font-semibold text-gray-900 dark:text-gray-100"
      >
        评论
        {total > 0 ? (
          <span className="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">{total}</span>
        ) : null}
      </h2>

      {isLoggedIn ? (
        <CommentComposer skillId={skillId} parentId={null} placeholder="说点什么" />
      ) : (
        <p className="text-sm text-gray-500 dark:text-gray-400">
          <Link to="/login" className="text-brand-600 hover:underline dark:text-brand-500">
            登录
          </Link>
          后可以发表评论。
        </p>
      )}

      {commentsQuery.isError && <ErrorBanner error={commentsQuery.error as ApiError} />}

      {commentsQuery.isPending && (
        <div className="flex justify-center py-8">
          <Spinner size="md" label="正在加载评论" />
        </div>
      )}

      {!commentsQuery.isPending && items.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-gray-400">还没有评论,来做第一个。</p>
      )}

      <ul className="flex flex-col gap-4">
        {items.map((comment) => (
          <li key={comment.id}>
            <CommentItem
              skillId={skillId}
              comment={comment}
              currentUser={currentUser}
              isLoggedIn={isLoggedIn}
              replyingTo={replyingTo}
              onToggleReply={(id) => setReplyingTo((current) => (current === id ? null : id))}
            />
          </li>
        ))}
      </ul>

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
    </section>
  )
}

interface CommentItemProps {
  readonly skillId: number
  readonly comment: SkillCommentItem
  readonly currentUser: CurrentUser | undefined
  readonly isLoggedIn: boolean
  readonly replyingTo: number | null
  readonly onToggleReply: (commentId: number) => void
}

function CommentItem({
  skillId,
  comment,
  currentUser,
  isLoggedIn,
  replyingTo,
  onToggleReply,
}: CommentItemProps) {
  return (
    <article className="rounded-card border border-gray-200 p-3 dark:border-gray-800">
      <CommentBody skillId={skillId} comment={comment} currentUser={currentUser} />

      {/* 回复入口只挂在顶级评论上,且已删除的评论不能再被回复 —— 与服务端的校验一致。
          界面上做不出来的操作,就不会有人去试。 */}
      {isLoggedIn && !comment.deleted && (
        <div className="mt-2">
          <button
            type="button"
            onClick={() => onToggleReply(comment.id)}
            className="text-xs text-brand-600 hover:underline dark:text-brand-500"
          >
            {replyingTo === comment.id ? '取消回复' : '回复'}
          </button>
        </div>
      )}

      {replyingTo === comment.id && (
        <div className="mt-2">
          <CommentComposer
            skillId={skillId}
            parentId={comment.id}
            placeholder="回复这条评论"
            onSubmitted={() => onToggleReply(comment.id)}
          />
        </div>
      )}

      {comment.replies.length > 0 && (
        <ul className="mt-3 flex flex-col gap-3 border-l-2 border-gray-100 pl-3 dark:border-gray-800">
          {comment.replies.map((reply) => (
            <li key={reply.id}>
              <CommentBody skillId={skillId} comment={reply} currentUser={currentUser} />
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}

interface CommentBodyProps {
  readonly skillId: number
  readonly comment: SkillCommentItem
  readonly currentUser: CurrentUser | undefined
}

function CommentBody({ skillId, comment, currentUser }: CommentBodyProps) {
  const deleteMutation = useDeleteSkillComment(skillId)

  // 删除权限与服务端保持一致:作者本人或 ADMIN。**skill 的 owner 不在其列** ——
  // 让被评论者能删评论,会把"我的地盘我说了算"变成删除异见的手段。
  const canDelete =
    !comment.deleted &&
    currentUser !== undefined &&
    (currentUser.role === 'ADMIN' || currentUser.id === comment.author?.userId)

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-gray-500 dark:text-gray-400">
          {comment.author?.displayName ?? '未知用户'} · {formatDateTime(comment.createdAt)}
        </p>
        {canDelete && (
          <button
            type="button"
            disabled={deleteMutation.isPending}
            onClick={() => deleteMutation.mutate(comment.id)}
            className="text-xs text-gray-400 hover:text-danger-600 disabled:opacity-50 dark:hover:text-danger-500"
          >
            删除
          </button>
        )}
      </div>

      {/*
        纯文本渲染。React 的 {} 插值天然转义,这里的关键是**不做额外的事**:
        不用 dangerouslySetInnerHTML,不调 renderMarkdownToSafeHtml —— 那个是给 SKILL.md / README 用的,
        与用户随手输入的评论信任级别完全不同。whitespace-pre-wrap 保留换行,让多段评论仍然可读;
        break-words 防一长串无空格字符把布局撑破。
      */}
      <p
        className={
          comment.deleted
            ? 'text-sm text-gray-400 italic dark:text-gray-500'
            : 'whitespace-pre-wrap break-words text-sm text-gray-800 dark:text-gray-200'
        }
      >
        {comment.deleted ? DELETED_PLACEHOLDER : comment.content}
      </p>

      {deleteMutation.isError && <ErrorBanner error={deleteMutation.error as ApiError} />}
    </div>
  )
}

interface CommentComposerProps {
  readonly skillId: number
  readonly parentId: number | null
  readonly placeholder: string
  readonly onSubmitted?: () => void
}

function CommentComposer({ skillId, parentId, placeholder, onSubmitted }: CommentComposerProps) {
  const [content, setContent] = useState('')
  const addMutation = useAddSkillComment(skillId)

  // 上限与服务端的 CommentContent 一致。前端这份只为即时反馈 —— 服务端会用同一条规则再判一次,
  // 这里放行不等于那边放行(frontend/CLAUDE.md 第六节:前端校验不是安全边界)。
  const trimmed = content.trim()
  const canSubmit = trimmed !== '' && trimmed.length <= MAX_CONTENT_LENGTH && !addMutation.isPending

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit) {
      return
    }
    addMutation.mutate(
      { parentId: parentId ?? undefined, content: trimmed },
      {
        onSuccess: () => {
          // 只有成功才清空。失败还留着,不然一次网络抖动就让人白打一遍。
          setContent('')
          onSubmitted?.()
        },
      },
    )
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <label className="flex flex-col gap-1">
        <span className="sr-only">{placeholder}</span>
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder={placeholder}
          rows={3}
          className="w-full rounded-control border border-gray-300 px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 focus:border-brand-500 focus:outline-none dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
        />
      </label>

      <div className="flex items-center justify-between gap-2">
        <span
          className={
            trimmed.length > MAX_CONTENT_LENGTH
              ? 'text-xs text-danger-600 dark:text-danger-400'
              : 'text-xs text-gray-400 dark:text-gray-500'
          }
        >
          {trimmed.length}/{MAX_CONTENT_LENGTH}
        </span>
        <Button type="submit" size="sm" loading={addMutation.isPending} disabled={!canSubmit}>
          发表
        </Button>
      </div>

      {addMutation.isError && <ErrorBanner error={addMutation.error as ApiError} />}
    </form>
  )
}
