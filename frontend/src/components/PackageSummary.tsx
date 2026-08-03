import type { PackageValidationReport } from '../lib/skillPackageTypes'

export interface PackageSummaryProps {
  readonly report: PackageValidationReport
}

const BYTES_PER_KB = 1024

/** 字节数格式化,只服务本组件——`components/textFormat.ts` 是卡片专用的格式化,不共用。 */
function formatBytes(bytes: number): string {
  if (bytes < BYTES_PER_KB) {
    return `${bytes} B`
  }
  const kilobytes = bytes / BYTES_PER_KB
  if (kilobytes < BYTES_PER_KB) {
    return `${kilobytes.toFixed(1)} KB`
  }
  return `${(kilobytes / BYTES_PER_KB).toFixed(1)} MB`
}

/**
 * 本地校验通过后的包信息摘要:frontmatter 解析结果(name / description / allowed-tools)
 * 加上文件数、解压后大小、是否带 README——都是即时反馈,不代表服务端 precheck 一定通过。
 */
export function PackageSummary({ report }: PackageSummaryProps) {
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-card border border-gray-200 p-4 text-sm dark:border-gray-700">
      <dt className="text-gray-500 dark:text-gray-400">name</dt>
      <dd className="font-mono text-gray-900 dark:text-gray-100">{report.parsed?.name}</dd>
      <dt className="text-gray-500 dark:text-gray-400">description</dt>
      <dd className="text-gray-900 dark:text-gray-100">{report.parsed?.description}</dd>
      <dt className="text-gray-500 dark:text-gray-400">allowed-tools</dt>
      <dd className="text-gray-900 dark:text-gray-100">
        {report.parsed && report.parsed.allowedTools.length > 0
          ? report.parsed.allowedTools.join(', ')
          : '未声明'}
      </dd>
      <dt className="text-gray-500 dark:text-gray-400">文件数</dt>
      <dd className="text-gray-900 dark:text-gray-100">{report.fileCount}</dd>
      <dt className="text-gray-500 dark:text-gray-400">解压后大小</dt>
      <dd className="text-gray-900 dark:text-gray-100">{formatBytes(report.unpackedBytes)}</dd>
      <dt className="text-gray-500 dark:text-gray-400">README</dt>
      <dd className="text-gray-900 dark:text-gray-100">{report.hasReadme ? '有' : '无'}</dd>
    </dl>
  )
}
