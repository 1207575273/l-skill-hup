import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { type ApiError, getAuthToken } from '../api/client'
import type {
  SkillPrecheckExisting,
  SkillPrecheckIdenticalMatch,
  SkillPrecheckViolation,
  SkillSource,
} from '../api/types'
import { Button } from '../components/Button'
import { CuratedTagPicker } from '../components/CuratedTagPicker'
import { DuplicateNameDialog } from '../components/DuplicateNameDialog'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { PackageSourcePicker } from '../components/PackageSourcePicker'
import { PackageSummary } from '../components/PackageSummary'
import { PackageViolationList } from '../components/PackageViolationList'
import type { SelectOption } from '../components/Select'
import { Select } from '../components/Select'
import { Spinner } from '../components/Spinner'
import { TextInput } from '../components/TextInput'
import { useCurrentUser } from '../hooks/useAuthQueries'
import {
  usePrecheckSkillPackage,
  usePublishSkill,
  usePublishSkillVersion,
} from '../hooks/useSkillMutations'
import { useTags } from '../hooks/useTagQueries'
import type { SkillUploadInput } from '../lib/skillPackageBuilder'
import { normalizeSkillUploadToZip } from '../lib/skillPackageBuilder'
import type { PackageValidationReport, PackageViolation } from '../lib/skillPackageTypes'
import { validateSkillPackage } from '../lib/skillPackageValidator'

/**
 * 上传页(业务建模 5.1/5.2/5.5、3.8、6.2 precheck 响应)。
 *
 * **前端校验不是安全边界。** 本页依赖的 `lib/skillPackageValidator.ts` 与
 * `POST /skills/precheck` 只是为了让作者上传前就看到问题、即时反馈——服务端在
 * `POST /skills` / `POST /skills/{id}/versions` 时会用同一套规则完整重做一遍解包、
 * 路径安全、体积上限、frontmatter 解析。三个月后如果有人看到这一整套完整的前端校验
 * 流程,很自然会觉得"服务端应该可以信任它了"——这个错觉在这个项目里代价很高:
 * skill-hub 分发的是会被 Agent 执行的内容,真正的安全边界只能在服务端
 * (`.claude/CLAUDE.md` 第七节)。
 *
 * 页面状态机(细节见任务报告):
 *   empty -> checking -> invalid(本地校验未过,回到 empty 允许重选)
 *                      -> valid(本地校验通过,展示发布表单)
 *   valid + 提交表单 -> precheck 进行中
 *     -> serverViolations(服务端发现问题)
 *     -> identical(内容摘要命中已有版本)
 *     -> duplicateDecision(重名二选一弹窗,3.8)
 *          -> 改名新建 -> POST /skills
 *          -> 作为新版本(需 canPublishNewVersion)-> POST /skills/{id}/versions
 *     -> 都不命中 -> 直接 POST /skills
 *   发布成功 -> 跳转 /skills/{id}
 */

type LocalPackageState =
  | { readonly status: 'empty' }
  | { readonly status: 'checking' }
  | { readonly status: 'invalid'; readonly violations: PackageViolation[] }
  | { readonly status: 'valid'; readonly zipBlob: Blob; readonly report: PackageValidationReport }

interface PublishFormState {
  readonly version: string
  readonly displayName: string
  readonly source: SkillSource
  readonly curatedTagIds: number[]
  readonly freeTagsRaw: string
}

interface PublishFormErrors {
  readonly version?: string
  readonly curatedTagIds?: string
  readonly freeTags?: string
}

interface DuplicateDecisionState {
  readonly existing: SkillPrecheckExisting
  readonly suggestedName: string | null
}

/** 业务建模 5.4:必须匹配 SemVer,不支持预发布标识与构建元数据。 */
const SEMVER_PATTERN = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/
/** 业务建模 5.7:CURATED 每个 skill 1~3 个,FREE 至多 10 个,单个标签长度上限 16。 */
const MIN_CURATED_TAGS = 1
const MAX_CURATED_TAGS = 3
const MAX_FREE_TAGS = 10
const FREE_TAG_MAX_LENGTH = 16

const SOURCE_OPTIONS: ReadonlyArray<SelectOption<SkillSource>> = [
  { value: 'INTERNAL', label: '企业内部' },
  { value: 'COMMUNITY', label: '社区' },
]

/**
 * 版本号默认值。绝大多数上传是"发布一个新 skill",而新 skill 的第一个版本按惯例就是 1.0.0 ——
 * 让人每次手打一遍是纯粹的摩擦。
 *
 * <p>给默认值而不是"留空时后端兜底":留空的话表单校验必须放行空值,而那条校验同时也在拦
 * "1.0" 这类非 SemVer 输入,放行空值就等于给它开了个特例。预填一个合法值,校验规则保持单一,
 * 想发别的版本直接改掉即可。
 */
const DEFAULT_VERSION = '1.0.0'

const INITIAL_FORM_STATE: PublishFormState = {
  version: DEFAULT_VERSION,
  displayName: '',
  source: 'INTERNAL',
  curatedTagIds: [],
  freeTagsRaw: '',
}

/**
 * 自由标签入库前归一化(业务建模 5.7:去首尾空白、折叠中间连续空白、去重)。
 * 这里只做即时反馈用的粗校验,真正的归一化与自动创建在服务端。
 */
function parseFreeTags(raw: string): string[] {
  const seen = new Set<string>()
  const tags: string[] = []
  for (const candidate of raw.split(',')) {
    const tag = candidate.trim().replace(/\s+/g, ' ')
    if (tag !== '' && !seen.has(tag)) {
      seen.add(tag)
      tags.push(tag)
    }
  }
  return tags
}

function validatePublishForm(form: PublishFormState): PublishFormErrors {
  const versionError = SEMVER_PATTERN.test(form.version.trim())
    ? undefined
    : '版本号必须是 SemVer 格式,如 1.0.0'

  const curatedTagIdsError =
    form.curatedTagIds.length < MIN_CURATED_TAGS || form.curatedTagIds.length > MAX_CURATED_TAGS
      ? `分类标签需选择 ${MIN_CURATED_TAGS}~${MAX_CURATED_TAGS} 个`
      : undefined

  const freeTags = parseFreeTags(form.freeTagsRaw)
  const freeTagsError =
    freeTags.length > MAX_FREE_TAGS
      ? `自由标签最多 ${MAX_FREE_TAGS} 个`
      : freeTags.some((tag) => tag.length > FREE_TAG_MAX_LENGTH)
        ? `单个自由标签长度不得超过 ${FREE_TAG_MAX_LENGTH} 个字符`
        : undefined

  return {
    version: versionError,
    curatedTagIds: curatedTagIdsError,
    freeTags: freeTagsError,
  }
}

export default function UploadPage() {
  const navigate = useNavigate()
  const hasToken = getAuthToken() !== null
  const currentUserQuery = useCurrentUser()
  const curatedTagsQuery = useTags('CURATED')

  const [localPackage, setLocalPackage] = useState<LocalPackageState>({ status: 'empty' })
  const [serverViolations, setServerViolations] = useState<SkillPrecheckViolation[] | null>(null)
  const [identicalMatch, setIdenticalMatch] = useState<SkillPrecheckIdenticalMatch | null>(null)
  const [duplicateDecision, setDuplicateDecision] = useState<DuplicateDecisionState | null>(null)
  const [form, setForm] = useState<PublishFormState>(INITIAL_FORM_STATE)
  const [formErrors, setFormErrors] = useState<PublishFormErrors>({})

  const precheckMutation = usePrecheckSkillPackage()
  const publishSkillMutation = usePublishSkill()
  const publishSkillVersionMutation = usePublishSkillVersion(
    duplicateDecision?.existing.skillId ?? 0,
  )

  const isSubmitting =
    precheckMutation.isPending ||
    publishSkillMutation.isPending ||
    publishSkillVersionMutation.isPending

  async function processPackageInput(input: SkillUploadInput) {
    setServerViolations(null)
    setIdenticalMatch(null)
    setDuplicateDecision(null)
    setLocalPackage({ status: 'checking' })

    const normalized = await normalizeSkillUploadToZip(input)
    if (!normalized.ok) {
      setLocalPackage({ status: 'invalid', violations: normalized.violations })
      return
    }

    // 压缩比检查只对"拖入 zip"形态有意义(业务建模 5.2),其余两种形态在打包前
    // 不存在"压缩后大小"这个概念。
    const originalArchiveBytes = input.kind === 'zip' ? input.file.size : undefined
    const report = await validateSkillPackage(normalized.package.entries, { originalArchiveBytes })
    if (!report.valid) {
      setLocalPackage({ status: 'invalid', violations: report.violations })
      return
    }
    setLocalPackage({ status: 'valid', zipBlob: normalized.package.blob, report })
  }

  function handlePackageInputSelected(input: SkillUploadInput) {
    void processPackageInput(input)
  }

  async function publishAsNewSkill(zipBlob: Blob) {
    try {
      const skill = await publishSkillMutation.mutateAsync({
        packageFile: zipBlob,
        version: form.version.trim(),
        displayName: form.displayName.trim() || undefined,
        source: form.source,
        curatedTagIds: form.curatedTagIds,
        freeTagNames: parseFreeTags(form.freeTagsRaw),
      })
      navigate(`/skills/${skill.id}`)
    } catch {
      // 错误已经落进 publishSkillMutation.error,下方 ErrorBanner 会展示,
      // 这里只需要防止 unhandled rejection。
    }
  }

  async function publishAsNewVersion(zipBlob: Blob) {
    try {
      const skill = await publishSkillVersionMutation.mutateAsync({
        packageFile: zipBlob,
        version: form.version.trim(),
      })
      navigate(`/skills/${skill.id}`)
    } catch {
      // 同上,由 publishSkillVersionMutation.error 承载。
    }
  }

  async function submitPublishFlow() {
    if (localPackage.status !== 'valid' || isSubmitting) {
      return
    }

    const errors = validatePublishForm(form)
    setFormErrors(errors)
    if (errors.version || errors.curatedTagIds || errors.freeTags) {
      return
    }

    setServerViolations(null)
    setIdenticalMatch(null)
    try {
      const result = await precheckMutation.mutateAsync(localPackage.zipBlob)
      if (result.violations.length > 0) {
        setServerViolations(result.violations)
        return
      }
      if (result.identical) {
        setIdenticalMatch(result.identical)
        return
      }
      if (result.duplicatedName && result.existing) {
        setDuplicateDecision({ existing: result.existing, suggestedName: result.suggestedName })
        return
      }
      await publishAsNewSkill(localPackage.zipBlob)
    } catch {
      // precheckMutation.error 已捕获,下方 ErrorBanner 会展示。
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void submitPublishFlow()
  }

  function handleRenameAndCreate() {
    if (localPackage.status !== 'valid' || isSubmitting) {
      return
    }
    void publishAsNewSkill(localPackage.zipBlob)
  }

  function handlePublishAsNewVersion() {
    if (localPackage.status !== 'valid' || !duplicateDecision || isSubmitting) {
      return
    }
    void publishAsNewVersion(localPackage.zipBlob)
  }

  // 上传是登录才可用的动作(业务建模 5.16)。token 缺失时直接引导去登录;token 存在
  // 但 /auth/me 失败(过期/失效)时按同样的方式处理,不单独区分"两种没登录"。
  if (!hasToken || currentUserQuery.isError) {
    return (
      <EmptyState
        title="上传 skill 需要先登录"
        description="登录后即可发布新 skill,或给自己拥有的 skill 发新版本。"
        action={
          <Link
            to="/login"
            className="inline-flex h-10 items-center rounded-control bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
          >
            去登录
          </Link>
        }
      />
    )
  }

  if (currentUserQuery.isPending) {
    return <Spinner label="加载账号信息中" />
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">发布 skill</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          选文件夹、拖入 zip 或上传单个 SKILL.md,三种方式效果一致,服务端只认 zip 一种入口。
        </p>
      </div>

      <PackageSourcePicker onSelect={handlePackageInputSelected} disabled={isSubmitting} />

      {localPackage.status === 'checking' && <Spinner label="正在解析包" />}

      {localPackage.status === 'invalid' && (
        <PackageViolationList title="本地校验未通过" violations={localPackage.violations} />
      )}

      {localPackage.status === 'valid' && (
        <>
          <PackageSummary report={localPackage.report} />

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <TextInput
              label="版本号"
              placeholder="1.0.0"
              value={form.version}
              onChange={(event) => setForm((prev) => ({ ...prev, version: event.target.value }))}
              errorMessage={formErrors.version}
              hint="SemVer 格式;发布新版本时必须严格大于该 skill 现有最大版本"
            />
            <TextInput
              label="展示名(可选)"
              maxLength={128}
              value={form.displayName}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, displayName: event.target.value }))
              }
              hint={`留空时使用 SKILL.md 的 name${
                localPackage.report.parsed ? `(${localPackage.report.parsed.name})` : ''
              }`}
            />
            <Select
              label="来源"
              value={form.source}
              onChange={(value) => setForm((prev) => ({ ...prev, source: value }))}
              options={SOURCE_OPTIONS}
            />
            <div className="flex flex-col gap-1">
              <CuratedTagPicker
                tags={curatedTagsQuery.data ?? []}
                value={form.curatedTagIds}
                onChange={(curatedTagIds) => setForm((prev) => ({ ...prev, curatedTagIds }))}
                max={MAX_CURATED_TAGS}
                errorMessage={formErrors.curatedTagIds}
              />
              {curatedTagsQuery.isPending && <Spinner size="sm" label="加载分类标签" />}
              {curatedTagsQuery.isError && (
                <ErrorBanner
                  error={curatedTagsQuery.error as ApiError}
                  onRetry={() => void curatedTagsQuery.refetch()}
                />
              )}
            </div>
            <TextInput
              label="自由标签"
              value={form.freeTagsRaw}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, freeTagsRaw: event.target.value }))
              }
              hint={`用逗号分隔,最多 ${MAX_FREE_TAGS} 个,不存在的会自动创建`}
              errorMessage={formErrors.freeTags}
            />
            <Button type="submit" loading={isSubmitting} fullWidth>
              发布
            </Button>
          </form>
        </>
      )}

      {serverViolations && (
        <PackageViolationList title="服务端校验未通过" violations={serverViolations} />
      )}

      {identicalMatch && (
        <div
          role="alert"
          className="rounded-card border border-danger-500 bg-danger-50 p-4 text-sm text-danger-700 dark:border-danger-700 dark:bg-danger-700/10 dark:text-danger-100"
        >
          完全相同的包已存在:
          <Link to={`/skills/${identicalMatch.skillId}`} className="ml-1 underline">
            skill #{identicalMatch.skillId} · v{identicalMatch.version}
          </Link>
        </div>
      )}

      {precheckMutation.isError && <ErrorBanner error={precheckMutation.error as ApiError} />}
      {publishSkillMutation.isError && (
        <ErrorBanner error={publishSkillMutation.error as ApiError} />
      )}
      {publishSkillVersionMutation.isError && (
        <ErrorBanner error={publishSkillVersionMutation.error as ApiError} />
      )}

      {duplicateDecision && (
        <DuplicateNameDialog
          open
          existing={duplicateDecision.existing}
          suggestedName={duplicateDecision.suggestedName}
          onRenameAndCreate={handleRenameAndCreate}
          onPublishAsNewVersion={handlePublishAsNewVersion}
          onCancel={() => setDuplicateDecision(null)}
          submitting={isSubmitting}
        />
      )}
    </div>
  )
}
