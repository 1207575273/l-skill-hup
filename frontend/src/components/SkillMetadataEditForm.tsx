import { type FormEvent, useState } from 'react'
import type { ApiError } from '../api/client'
import type { SkillSource, Tag, UpdateSkillPayload } from '../api/types'
import { Button } from './Button'
import { ErrorBanner } from './ErrorBanner'
import { Select } from './Select'
import { parseFreeTagNamesInput } from './skillDetailFormat'
import { TextInput } from './TextInput'

export interface SkillMetadataEditFormProps {
  /** 只读展示。name 参与安装路径拼接、创建后任何角色都不可改(权限矩阵 5.16),这里不给编辑入口。 */
  readonly skillName: string
  readonly initialDisplayName: string
  readonly initialSource: SkillSource
  readonly initialCuratedTagIds: readonly number[]
  readonly initialFreeTagNames: readonly string[]
  readonly curatedTagOptions: readonly Tag[]
  readonly submitting: boolean
  readonly error?: ApiError | null
  readonly onCancel: () => void
  readonly onSubmit: (payload: UpdateSkillPayload) => void
}

const SOURCE_OPTIONS: ReadonlyArray<{ value: SkillSource; label: string }> = [
  { value: 'INTERNAL', label: '企业内部' },
  { value: 'COMMUNITY', label: '社区' },
]

const MIN_CURATED_TAGS = 1
const MAX_CURATED_TAGS = 3
const MAX_FREE_TAGS = 10

/**
 * skill 元数据编辑表单(展示名 / 来源 / 标签),owner / ADMIN 专用(权限矩阵 5.16)。
 *
 * 数量校验(CURATED 1~3、FREE 至多 10,见 5.7)只是提交前的即时反馈,不是安全边界——
 * 服务端会在同一事务内把这两条规则完整重做一遍(项目宪法第七节)。
 */
export function SkillMetadataEditForm({
  skillName,
  initialDisplayName,
  initialSource,
  initialCuratedTagIds,
  initialFreeTagNames,
  curatedTagOptions,
  submitting,
  error,
  onCancel,
  onSubmit,
}: SkillMetadataEditFormProps) {
  const [displayName, setDisplayName] = useState(initialDisplayName)
  const [source, setSource] = useState<SkillSource>(initialSource)
  const [curatedTagIds, setCuratedTagIds] = useState<readonly number[]>(initialCuratedTagIds)
  const [freeTagNamesText, setFreeTagNamesText] = useState(initialFreeTagNames.join(', '))
  const [validationMessage, setValidationMessage] = useState<string | null>(null)

  function toggleCuratedTag(tagId: number) {
    setCuratedTagIds((current) =>
      current.includes(tagId) ? current.filter((id) => id !== tagId) : [...current, tagId],
    )
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (curatedTagIds.length < MIN_CURATED_TAGS || curatedTagIds.length > MAX_CURATED_TAGS) {
      setValidationMessage(`分类标签需选择 ${MIN_CURATED_TAGS}~${MAX_CURATED_TAGS} 个`)
      return
    }

    const freeTagNames = parseFreeTagNamesInput(freeTagNamesText)
    if (freeTagNames.length > MAX_FREE_TAGS) {
      setValidationMessage(`自由标签最多 ${MAX_FREE_TAGS} 个`)
      return
    }

    setValidationMessage(null)
    onSubmit({ displayName, source, curatedTagIds: [...curatedTagIds], freeTagNames })
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
      <p className="text-xs text-gray-500 dark:text-gray-400">
        标识 <code className="rounded bg-gray-100 px-1 py-0.5 dark:bg-gray-800">{skillName}</code>{' '}
        创建后不可修改
      </p>

      <TextInput
        label="展示名"
        value={displayName}
        onChange={(event) => setDisplayName(event.target.value)}
        required
      />

      <Select<SkillSource>
        label="来源"
        value={source}
        onChange={setSource}
        options={SOURCE_OPTIONS}
      />

      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium text-gray-700 dark:text-gray-300">
          分类标签(至少 1 个,至多 3 个)
        </legend>
        <div className="flex flex-wrap gap-3">
          {curatedTagOptions.map((tag) => (
            <label
              key={tag.id}
              className="inline-flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-300"
            >
              <input
                type="checkbox"
                className="accent-brand-600"
                checked={curatedTagIds.includes(tag.id)}
                onChange={() => toggleCuratedTag(tag.id)}
              />
              {tag.name}
            </label>
          ))}
        </div>
      </fieldset>

      <TextInput
        label="自由标签"
        hint="用逗号分隔,至多 10 个;不存在的标签会自动创建"
        value={freeTagNamesText}
        onChange={(event) => setFreeTagNamesText(event.target.value)}
      />

      {validationMessage && (
        <p role="alert" className="text-xs text-danger-600 dark:text-danger-400">
          {validationMessage}
        </p>
      )}

      {error && <ErrorBanner error={error} />}

      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          取消
        </Button>
        <Button type="submit" variant="primary" loading={submitting}>
          保存修改
        </Button>
      </div>
    </form>
  )
}
