import type { SkillSortOption, SkillSource, Tag } from '../api/types'
import { Button } from './Button'
import { Select } from './Select'
import { TagFilterChip } from './TagFilterChip'
import { TextInput } from './TextInput'

export interface SkillFilterPanelProps {
  readonly keyword: string
  readonly source: SkillSource | ''
  readonly sort: SkillSortOption
  readonly certifiedOnly: boolean
  /** CURATED 词条,展示约定 7.2 里"分类"那一行,由调用方从 `useTags()` 按 kind 分好传入。 */
  readonly curatedTags: readonly Tag[]
  /** FREE 词条,"标签"那一行。 */
  readonly freeTags: readonly Tag[]
  readonly selectedTagIds: readonly number[]
  readonly onKeywordChange: (keyword: string) => void
  readonly onSourceChange: (source: SkillSource | '') => void
  readonly onSortChange: (sort: SkillSortOption) => void
  readonly onCertifiedOnlyChange: (certifiedOnly: boolean) => void
  readonly onToggleTag: (tagId: number) => void
  readonly onReset: () => void
}

const SOURCE_OPTIONS: ReadonlyArray<{ value: SkillSource | ''; label: string }> = [
  { value: '', label: '全部来源' },
  { value: 'INTERNAL', label: '企业内部' },
  { value: 'COMMUNITY', label: '社区' },
]

const SORT_OPTIONS: ReadonlyArray<{ value: SkillSortOption; label: string }> = [
  { value: 'latest', label: '最新发布' },
  { value: 'downloads', label: '下载最多' },
  { value: 'likes', label: '点赞最多' },
]

/**
 * skill 列表的筛选面板。纯展示 + 回调,不持有筛选状态的真相、不发请求
 * (frontend/CLAUDE.md 第二节)——真相状态由调用方(`SkillListPage`)同步进 URL query
 * string,这样筛选结果能直接甩链接给同事(业务建模文档第七章、权限矩阵里浏览匿名可用
 * 正是为了支撑这个场景)。
 */
export function SkillFilterPanel({
  keyword,
  source,
  sort,
  certifiedOnly,
  curatedTags,
  freeTags,
  selectedTagIds,
  onKeywordChange,
  onSourceChange,
  onSortChange,
  onCertifiedOnlyChange,
  onToggleTag,
  onReset,
}: SkillFilterPanelProps) {
  const hasActiveFilter =
    keyword !== '' || source !== '' || certifiedOnly || selectedTagIds.length > 0

  return (
    <div className="flex flex-col gap-4 rounded-card border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-900/50">
      <div className="flex flex-wrap items-end gap-3">
        <div className="w-full max-w-xs">
          <TextInput
            label="关键词"
            placeholder="按名称或描述搜索"
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
          />
        </div>
        <Select label="来源" value={source} onChange={onSourceChange} options={SOURCE_OPTIONS} />
        <Select label="排序" value={sort} onChange={onSortChange} options={SORT_OPTIONS} />
        <label className="flex h-10 items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
          <input
            type="checkbox"
            checked={certifiedOnly}
            onChange={(event) => onCertifiedOnlyChange(event.target.checked)}
            className="h-4 w-4 rounded border-gray-300 text-brand-600 focus:ring-1 focus:ring-brand-600 dark:border-gray-700"
          />
          仅看已认证
        </label>
        {hasActiveFilter && (
          <Button variant="ghost" size="sm" onClick={onReset}>
            清空筛选条件
          </Button>
        )}
      </div>

      {curatedTags.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-gray-500 dark:text-gray-400">分类</span>
          {curatedTags.map((tag) => (
            <TagFilterChip
              key={tag.id}
              tag={tag}
              selected={selectedTagIds.includes(tag.id)}
              onToggle={onToggleTag}
            />
          ))}
        </div>
      )}

      {freeTags.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-gray-500 dark:text-gray-400">标签</span>
          {freeTags.map((tag) => (
            <TagFilterChip
              key={tag.id}
              tag={tag}
              selected={selectedTagIds.includes(tag.id)}
              onToggle={onToggleTag}
            />
          ))}
        </div>
      )}
    </div>
  )
}
