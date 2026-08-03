export interface PackageViolationListItem {
  readonly code: string
  readonly message: string
  readonly path?: string | null
}

export interface PackageViolationListProps {
  readonly title: string
  readonly violations: readonly PackageViolationListItem[]
}

/**
 * 校验违规逐条展示。前端本地校验产出的 `PackageViolation`(`lib/skillPackageTypes.ts`)
 * 与服务端 precheck 返回的 `SkillPrecheckViolation`(`api/types.ts`)字段形状兼容
 * (`{code, message, path?}`),两处共用同一个展示组件,不为"有没有 path"分别写两份 UI。
 *
 * **前端校验不是安全边界。** 这里列出的每一条都只是即时反馈——服务端 precheck 会用
 * 同一套规则完整重做一遍;这个列表存在的意义是让作者在真正提交前就看到具体是哪个文件、
 * 违反了哪一条,而不是等服务端 500 才知道包不合法(`.claude/CLAUDE.md` 第七节)。
 */
export function PackageViolationList({ title, violations }: PackageViolationListProps) {
  if (violations.length === 0) {
    return null
  }

  return (
    <div
      role="alert"
      className="flex flex-col gap-2 rounded-card border border-danger-500 bg-danger-50 p-4 dark:border-danger-700 dark:bg-danger-700/10"
    >
      <p className="text-sm font-medium text-danger-700 dark:text-danger-100">{title}</p>
      <ul className="flex flex-col gap-1 text-sm text-danger-700 dark:text-danger-200">
        {violations.map((violation, index) => (
          <li key={`${violation.code}-${violation.path ?? index}`}>
            <code className="rounded bg-white px-1 py-0.5 text-xs dark:bg-gray-900">
              {violation.code}
            </code>
            {violation.path && <span className="ml-1 font-mono text-xs">{violation.path}</span>}
            <span className="ml-1">{violation.message}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
