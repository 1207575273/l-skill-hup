import { useTagSuggestions } from '../hooks/useTagQueries'
import { TextInput } from './TextInput'

export interface FreeTagInputProps {
  readonly value: string
  readonly onChange: (next: string) => void
  readonly maxTags: number
  readonly errorMessage?: string
}

/** 逗号既收中文的也收英文的 —— 中文输入法下打出的是全角逗号,按半角切会把一整串当成一个标签。 */
const SEPARATOR_PATTERN = /[,，]/

/**
 * 取"正在输入的那一段":最后一个逗号之后的部分。联想只针对它,前面已经用逗号结束的标签是敲定了的。
 */
export function currentSegment(raw: string): string {
  const parts = raw.split(SEPARATOR_PATTERN)
  return (parts[parts.length - 1] ?? '').trim()
}

/** 用选中的词条替换正在输入的那一段,并补上分隔符,让人可以接着敲下一个。 */
export function replaceCurrentSegment(raw: string, picked: string): string {
  const parts = raw.split(SEPARATOR_PATTERN)
  parts[parts.length - 1] = picked
  return `${parts.map((part) => part.trim()).join(', ')}, `
}

/**
 * 自由标签输入框 + 输入联想。
 *
 * <p><b>联想是防标签爆炸的主力,不是锦上添花</b>(业务建模文档 5.7):同义词爆炸(代码审查 /
 * code-review / 代码检查 / codereview)靠事后合并是清不完的,而把已有词条在用户敲第二个字时就推到
 * 面前,能让它从源头不产生。归一化和长度上限只是兜底。
 *
 * <p>联想只作用于最后一段:前面用逗号结束的标签是已经敲定的,拿整串去匹配只会什么都匹配不到。
 *
 * <p>不做下拉浮层,直接在输入框下面平铺成一行按钮:候选最多十个,浮层要处理定位、失焦关闭、键盘
 * 导航,而这里的收益只是省一点垂直空间。
 */
export function FreeTagInput({ value, onChange, maxTags, errorMessage }: FreeTagInputProps) {
  const segment = currentSegment(value)
  const suggestionsQuery = useTagSuggestions(segment)
  const suggestions = (suggestionsQuery.data ?? []).filter(
    // 已经完全打出来的词条不必再推一遍,那一行按钮里最没用的就是"你刚打完的这个"。
    (tag) => tag.name !== segment,
  )

  return (
    <div className="flex flex-col gap-2">
      <TextInput
        label="自由标签"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        hint={`用逗号分隔,最多 ${maxTags} 个,不存在的会自动创建`}
        errorMessage={errorMessage}
      />
      {suggestions.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-gray-500 dark:text-gray-400">已有词条:</span>
          {suggestions.map((tag) => (
            <button
              key={tag.id}
              type="button"
              onClick={() => onChange(replaceCurrentSegment(value, tag.name))}
              className="rounded-control border border-gray-200 px-2 py-0.5 text-xs text-gray-600 hover:border-brand-500 hover:text-brand-600 dark:border-gray-700 dark:text-gray-300 dark:hover:text-brand-500"
            >
              {tag.name}
              <span className="ml-1 text-gray-400">{tag.skillCount}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
