import { useState } from 'react'
import { useParams } from 'react-router'
import type { ApiError } from '../api/client'
import { pickAvatarColor } from '../components/avatarColor'
import { Button } from '../components/Button'
import { CertificationBadge } from '../components/CertificationBadge'
import { cx } from '../components/classNames'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { Modal } from '../components/Modal'
import { QuickInstallMenu } from '../components/QuickInstallMenu'
import { SkillContentViewer } from '../components/SkillContentViewer'
import { SkillMetadataEditForm } from '../components/SkillMetadataEditForm'
import { SkillVersionList } from '../components/SkillVersionList'
import { Spinner } from '../components/Spinner'
import {
  formatSkillOwnerLabel,
  parseSkillIdParam,
  sortSkillTagsForDisplay,
} from '../components/skillDetailFormat'
import { TagChip } from '../components/TagChip'
import { formatDateOnly } from '../components/textFormat'
import { useCurrentUser } from '../hooks/useAuthQueries'
import { useRelistSkill, useUnlistSkill, useUpdateSkillMetadata } from '../hooks/useSkillMutations'
import { useSkillDetail, useSkillVersions } from '../hooks/useSkillQueries'
import { useTags } from '../hooks/useTagQueries'

const SKILL_NOT_FOUND_CODE = 'SKILL_NOT_FOUND'

/**
 * skill 详情页(B2)。四种态:加载 / 不存在(路由参数无效或服务端 404)/ 请求失败 / 正常
 * (业务建模文档第七章)。
 *
 * 权限判断只用于决定要不要显示管理入口(下架/恢复、编辑元数据)——**真正的拦截在服务端**,
 * 这里判断错了顶多是多显示或少显示一个按钮,点了之后服务端仍会按权限矩阵(5.16)兜底拒绝。
 */
export default function SkillDetailPage() {
  const { id } = useParams<{ id: string }>()
  const skillId = parseSkillIdParam(id)

  // skillId 解析失败时仍要调用 hook(不能条件调用 hook),靠 `enabled` 关闭请求——
  // 这正是 useSkillDetail/useSkillVersions 的 enabled 选项设计要覆盖的场景。
  const skillQuery = useSkillDetail(skillId ?? 0, { enabled: skillId !== null })
  const versionsQuery = useSkillVersions(skillId ?? 0, { enabled: skillId !== null })
  const currentUserQuery = useCurrentUser()
  const curatedTagsQuery = useTags('CURATED')

  const unlistSkill = useUnlistSkill(skillId ?? 0)
  const relistSkill = useRelistSkill(skillId ?? 0)
  const updateMetadata = useUpdateSkillMetadata(skillId ?? 0)

  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [isEditOpen, setIsEditOpen] = useState(false)

  if (skillId === null) {
    return (
      <EmptyState
        title="无效的 skill 编号"
        description="链接里的 skill 编号不正确,请从列表页重新进入。"
      />
    )
  }

  if (skillQuery.isPending) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" label="正在加载 skill 详情" />
      </div>
    )
  }

  if (skillQuery.isError) {
    const error = skillQuery.error as ApiError
    if (error.code === SKILL_NOT_FOUND_CODE) {
      return (
        <EmptyState title="没有找到这个 skill" description="它可能已被下架删除,或者链接有误。" />
      )
    }
    return <ErrorBanner error={error} onRetry={() => skillQuery.refetch()} />
  }

  const skill = skillQuery.data
  const latestVersion = skill.latestVersion
  const effectiveVersionId = selectedVersionId ?? latestVersion?.id ?? null
  const versions = versionsQuery.data ?? []

  const currentUser = currentUserQuery.data
  const canManage = Boolean(
    currentUser && (currentUser.role === 'ADMIN' || currentUser.id === skill.owner?.userId),
  )

  function handleToggleListing() {
    if (skill.status === 'PUBLISHED') {
      unlistSkill.mutate()
    } else {
      relistSkill.mutate()
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-4">
            <span
              aria-hidden="true"
              className="flex h-14 w-14 shrink-0 items-center justify-center rounded-control text-xl font-semibold text-white"
              style={{ backgroundColor: pickAvatarColor(skill.name) }}
            >
              {skill.displayName.charAt(0) || skill.name.charAt(0)}
            </span>
            <div className="flex flex-col gap-1">
              <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">
                {skill.displayName}
              </h1>
              <p className="font-mono text-sm text-gray-500 dark:text-gray-400">{skill.name}</p>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {formatSkillOwnerLabel(skill.owner)} · 更新于 {formatDateOnly(skill.updatedAt)}
              </p>
            </div>
          </div>
          <div className="flex flex-col items-end gap-2">
            <CertificationBadge
              certification={latestVersion?.certification ?? 'NONE'}
              certifyNote={latestVersion?.certifyNote}
            />
            {skill.status === 'UNLISTED' && (
              <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                已下架
              </span>
            )}
          </div>
        </div>

        <p className="max-w-3xl text-sm text-gray-600 dark:text-gray-300">{skill.description}</p>

        <ul aria-label="标签" className="flex flex-wrap items-center gap-1.5">
          {sortSkillTagsForDisplay(skill.tags).map((tag) => (
            <li key={tag.id}>
              <TagChip tag={tag} />
            </li>
          ))}
        </ul>

        <p className="flex flex-wrap items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
          <span>下载 {skill.downloadCount}</span>
          <span>赞 {skill.likeCount}</span>
          <span>踩 {skill.dislikeCount}</span>
          <span>收藏 {skill.favoriteCount}</span>
          <span>评论 {skill.commentCount}</span>
        </p>

        {latestVersion && effectiveVersionId !== null && (
          <div>
            <QuickInstallMenu skillId={skill.id} versionId={effectiveVersionId} />
          </div>
        )}
      </header>

      <section aria-labelledby="skill-content-heading" className="flex flex-col gap-3">
        <h2
          id="skill-content-heading"
          className="text-lg font-semibold text-gray-900 dark:text-gray-100"
        >
          正文
        </h2>
        {latestVersion ? (
          <SkillContentViewer
            readmeContent={latestVersion.readmeContent}
            skillMdContent={latestVersion.skillMdContent}
          />
        ) : (
          <EmptyState title="暂无可用版本" description="这个 skill 目前没有已发布的版本内容。" />
        )}
      </section>

      <section aria-labelledby="skill-versions-heading" className="flex flex-col gap-3">
        <h2
          id="skill-versions-heading"
          className="text-lg font-semibold text-gray-900 dark:text-gray-100"
        >
          版本列表
        </h2>
        {versionsQuery.isPending ? (
          <Spinner label="正在加载版本列表" />
        ) : versionsQuery.isError ? (
          <ErrorBanner
            error={versionsQuery.error as ApiError}
            onRetry={() => versionsQuery.refetch()}
          />
        ) : versions.length === 0 ? (
          <EmptyState title="暂无版本" />
        ) : (
          <SkillVersionList
            versions={versions}
            selectedVersionId={effectiveVersionId}
            onSelectVersion={setSelectedVersionId}
          />
        )}
      </section>

      {canManage && (
        <section
          aria-labelledby="skill-manage-heading"
          className={cx(
            'flex flex-col gap-3 rounded-card border border-gray-200 p-4',
            'dark:border-gray-800',
          )}
        >
          <h2
            id="skill-manage-heading"
            className="text-lg font-semibold text-gray-900 dark:text-gray-100"
          >
            管理操作
          </h2>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              loading={unlistSkill.isPending || relistSkill.isPending}
              onClick={handleToggleListing}
            >
              {skill.status === 'PUBLISHED' ? '下架此 skill' : '恢复上架'}
            </Button>
            <Button variant="secondary" onClick={() => setIsEditOpen(true)}>
              编辑元数据
            </Button>
          </div>
          {unlistSkill.isError && <ErrorBanner error={unlistSkill.error as ApiError} />}
          {relistSkill.isError && <ErrorBanner error={relistSkill.error as ApiError} />}
        </section>
      )}

      <Modal open={isEditOpen} title="编辑元数据" onClose={() => setIsEditOpen(false)}>
        {isEditOpen ? (
          <SkillMetadataEditForm
            skillName={skill.name}
            initialDisplayName={skill.displayName}
            initialSource={skill.source}
            initialCuratedTagIds={skill.tags
              .filter((tag) => tag.kind === 'CURATED')
              .map((tag) => tag.id)}
            initialFreeTagNames={skill.tags
              .filter((tag) => tag.kind === 'FREE')
              .map((tag) => tag.name)}
            curatedTagOptions={curatedTagsQuery.data ?? []}
            submitting={updateMetadata.isPending}
            error={updateMetadata.error as ApiError | null}
            onCancel={() => setIsEditOpen(false)}
            onSubmit={(payload) =>
              updateMetadata.mutate(payload, { onSuccess: () => setIsEditOpen(false) })
            }
          />
        ) : null}
      </Modal>
    </div>
  )
}
