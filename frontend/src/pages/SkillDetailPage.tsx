import { useState } from 'react'
import { useParams } from 'react-router'
import type { ApiError } from '../api/client'
import type { SkillVersionSummary } from '../api/types'
import { pickAvatarColor } from '../components/avatarColor'
import { Button } from '../components/Button'
import { CertificationBadge } from '../components/CertificationBadge'
import { CertifyVersionDialog } from '../components/CertifyVersionDialog'
import { cx } from '../components/classNames'
import { DownloadSkillButton } from '../components/DownloadSkillButton'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { Modal } from '../components/Modal'
import { QuickInstallMenu } from '../components/QuickInstallMenu'
import { SkillCommentSection } from '../components/SkillCommentSection'
import { SkillContentViewer } from '../components/SkillContentViewer'
import { SkillFilePreview } from '../components/SkillFilePreview'
import { SkillFileTree } from '../components/SkillFileTree'
import { SkillMetadataEditForm } from '../components/SkillMetadataEditForm'
import { SkillVersionList } from '../components/SkillVersionList'
import { Spinner } from '../components/Spinner'
import {
  formatSkillOwnershipLabel,
  parseSkillIdParam,
  sortSkillTagsForDisplay,
} from '../components/skillDetailFormat'
import { TagChip } from '../components/TagChip'
import { formatDateOnly } from '../components/textFormat'
import { useCurrentUser } from '../hooks/useAuthQueries'
import { useInstallCartStore } from '../hooks/useInstallCartStore'
import {
  useCertifyVersion,
  useClaimSkill,
  useRelistSkill,
  useUnlistSkill,
  useUpdateSkillMetadata,
} from '../hooks/useSkillMutations'
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
  const claimSkill = useClaimSkill(skillId ?? 0)
  const updateMetadata = useUpdateSkillMetadata(skillId ?? 0)
  const certifyVersion = useCertifyVersion(skillId ?? 0)

  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [isEditOpen, setIsEditOpen] = useState(false)
  const [certifyTarget, setCertifyTarget] = useState<SkillVersionSummary | null>(null)

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
  // 下载接口按**版本号**定位(与安装脚本、认证接口一致),而选中态存的是 versionId,
  // 所以这里要回查一次。版本列表还没加载完时回落到最新版 —— 详情响应里自带它的版本号。
  const selectedVersion = versions.find((item) => item.id === effectiveVersionId)
  // 按 skillId 判断即可:购物车对同一个 skill 只保留一项(store 的 addItem 就是按 skillId 去重的),
  // 换版本不会变成两项。
  const inInstallCart = cartItems.some((item) => item.skillId === skill.id)

  const currentUser = currentUserQuery.data
  // 无主 skill 的归属人是内置 system,而 system 永远登不进来 —— 所以这条判断对无主 skill 自动
  // 退化成"仅 ADMIN",不需要为它加分支。匿名上传的人要自己管理,先认领。
  const canManage = Boolean(
    currentUser && (currentUser.role === 'ADMIN' || currentUser.id === skill.owner?.userId),
  )
  // 认领只要求"登录了":不看角色,也不校验声明归属是否对得上(先到先得,理由见后端
  // SkillClaimApplicationService —— 本地建号普遍没绑工号,拿工号当门槛会导致谁都认领不了)。
  const canClaim = skill.unclaimed && currentUser !== undefined
  // 认证是平台治理动作,**只看 ADMIN,不看 owner**(5.8):作者给自己发认证徽章会让整套机制当场归零。
  // 与 canManage 刻意分成两个变量 —— 合成一个"能管理"会让这条区别在某次改动里被顺手抹掉。
  const canCertify = currentUser?.role === 'ADMIN'

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
                {formatSkillOwnershipLabel(skill.owner, skill.declaredOwner, skill.unclaimed)} ·
                更新于 {formatDateOnly(skill.updatedAt)}
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
            {skill.unclaimed && (
              <>
                {/*
                  无主意味着这个包是匿名上传(或早先导入)的,归属不可追溯。与"未认证"叠加时尤其值得醒目:
                  平台对匿名内容没有任何背书,而包里的东西会被 Agent 执行 —— 用琥珀色而不是灰色,
                  是因为这不是一条中性的状态说明,是一条需要使用者停一下的提示。
                */}
                <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-200">
                  未认领
                </span>
                {canClaim ? (
                  <Button
                    variant="secondary"
                    loading={claimSkill.isPending}
                    onClick={() => claimSkill.mutate()}
                  >
                    认领为我的
                  </Button>
                ) : (
                  <span className="text-xs text-gray-500 dark:text-gray-400">登录后可认领</span>
                )}
                {claimSkill.isError && <ErrorBanner error={claimSkill.error as ApiError} />}
              </>
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
            {/*
              直接下载 zip。"快速接入"要先选客户端、再选全局还是项目级,再把命令粘进终端 ——
              每一步都假定使用者知道自己在干什么。下载一个 zip 是零概念的动作,谁都会,
              先让完全不懂的人能把东西拿到手。

              下的是**当前选中的版本**,与上面"加入安装单"同一条判据:用户特意翻到 1.0.3
              再点下载,给他最新版是最难察觉的一类错。
            */}
            <DownloadSkillButton
              skillId={skill.id}
              version={selectedVersion?.version ?? latestVersion.version}
              size="sm"
            />
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

      <SkillCommentSection skillId={skill.id} currentUser={currentUser} />

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
            skillId={skill.id}
            versions={versions}
            selectedVersionId={effectiveVersionId}
            onSelectVersion={setSelectedVersionId}
            onCertifyVersion={canCertify ? setCertifyTarget : undefined}
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

      {/* 认证弹窗放在最外层而不是塞进版本列表那一节:它是模态的,DOM 位置与视觉位置无关,
          而放在 section 里会让那一节的语义(版本列表)混进一个不属于它的东西。 */}
      <CertifyVersionDialog
        open={certifyTarget !== null}
        version={certifyTarget}
        submitting={certifyVersion.isPending}
        error={certifyVersion.error as ApiError | null}
        onClose={() => {
          setCertifyTarget(null)
          certifyVersion.reset()
        }}
        onSubmit={(certification, note) => {
          if (certifyTarget === null) {
            return
          }
          certifyVersion.mutate(
            {
              version: certifyTarget.version,
              // 空意见传 undefined 而不是空串:服务端会把空白归一化成 null,前端先说清楚"没有"更直接
              request: { certification, note: note === '' ? undefined : note },
            },
            { onSuccess: () => setCertifyTarget(null) },
          )
        }}
      />
    </div>
  )
}
