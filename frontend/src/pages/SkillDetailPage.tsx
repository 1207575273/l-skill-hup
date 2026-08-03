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
import { SkillFilePreview } from '../components/SkillFilePreview'
import { SkillFileTree } from '../components/SkillFileTree'
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
import { useInstallCartStore } from '../hooks/useInstallCartStore'
import { useRelistSkill, useUnlistSkill, useUpdateSkillMetadata } from '../hooks/useSkillMutations'
import { useSkillDetail, useSkillFileContent, useSkillVersions } from '../hooks/useSkillQueries'
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

  const cartItems = useInstallCartStore((state) => state.items)
  const toggleCartItem = useInstallCartStore((state) => state.toggleItem)

  // 选中的文件路径。**换版本时要清空** —— 上一版有的文件下一版可能没有,
  // 留着旧路径会让预览停在一个 404 上,而用户看不出是自己换了版本导致的。
  const [selectedFilePath, setSelectedFilePath] = useState<string | null>(null)

  // **必须在所有 early return 之前调用**:下面有四处提前返回(id 非法 / 加载中 / 404 / 请求失败),
  // 把 hook 放在它们之后就是条件调用 hook,React 会在状态切换时报 hooks 顺序错乱。
  // 所以这里不能等 skillQuery.data 就位再取值,只能从 optional chain 里拿,
  // 拿不到时传 null,由 hook 自己的 enabled 关掉请求。
  const fileVersionId = selectedVersionId ?? skillQuery.data?.latestVersion?.id ?? null
  const fileContentQuery = useSkillFileContent(skillId ?? 0, fileVersionId, selectedFilePath)

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
  // 按 skillId 判断即可:购物车对同一个 skill 只保留一项(store 的 addItem 就是按 skillId 去重的),
  // 换版本不会变成两项。
  const inInstallCart = cartItems.some((item) => item.skillId === skill.id)

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
          <div className="flex items-center gap-2">
            <QuickInstallMenu skillId={skill.id} versionId={effectiveVersionId} />
            {/*
              详情页加入安装单用的是**当前选中的版本**,不是最新版 —— 用户特意翻到 1.0.3 再点"加入",
              安装单里却躺着最新版,那是最难察觉的一类错。列表页没有版本选择,所以那边用 latestVersionId。
            */}
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                toggleCartItem({
                  skillId: skill.id,
                  versionId: effectiveVersionId,
                  skillDisplayName: skill.displayName,
                })
              }
            >
              {inInstallCart ? '已加入安装单' : '加入安装单'}
            </Button>
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

      {latestVersion && (
        <section aria-labelledby="skill-files-heading" className="flex flex-col gap-3">
          <h2
            id="skill-files-heading"
            className="text-lg font-semibold text-gray-900 dark:text-gray-100"
          >
            文件结构
            <span className="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">
              共 {latestVersion.fileCount} 个文件
            </span>
          </h2>
          {/*
            左树右预览。窄屏(默认)垂直堆叠、宽屏(md 起)左右分栏 —— 手机上并排会让两栏都窄到没法看。
            固定 `h-[32rem]` 而不是让内容撑高:两栏各自滚动才谈得上"点左边看右边",
            否则选一个长文件会把整页顶得老长,树被挤到屏幕外,下一次点击还得先滚回去。
          */}
          <div className="flex h-[32rem] flex-col gap-3 md:flex-row">
            {/* 树固定宽度不参与伸缩:它的内容是路径,宽度随选中文件变化会让整个布局跳动 */}
            <div className="h-48 shrink-0 md:h-full md:w-72">
              <SkillFileTree
                files={latestVersion.files}
                selectedPath={selectedFilePath}
                onSelectFile={setSelectedFilePath}
              />
            </div>
            {/* min-w-0 必须有:否则 pre 里的长行会把这一栏撑开,把左侧的树挤出容器 */}
            <div className="min-w-0 flex-1 overflow-hidden">
              <SkillFilePreview
                path={selectedFilePath}
                content={fileContentQuery.data ?? null}
                isLoading={fileContentQuery.isPending && selectedFilePath !== null}
                isError={fileContentQuery.isError}
              />
            </div>
          </div>
        </section>
      )}

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
