import { useState } from 'react'
import { Link } from 'react-router'
import type { ApiError } from '../api/client'
import type {
  ClientCode,
  ClientTarget,
  InstallBundleItem,
  InstallBundleResult,
  ScopeCode,
} from '../api/types'
import { Button } from '../components/Button'
import { CommandBlock } from '../components/CommandBlock'
import { EmptyState } from '../components/EmptyState'
import { ErrorBanner } from '../components/ErrorBanner'
import { Select } from '../components/Select'
import { Spinner } from '../components/Spinner'
import { useInstallCartStore } from '../hooks/useInstallCartStore'
import { useCreateInstallBundle } from '../hooks/useInstallMutations'
import { useInstallClients } from '../hooks/useInstallQueries'

/**
 * 客户端与安装级别的中文展示名。`QuickInstallMenu.tsx` 里有同样一份,但那个文件
 * 是私有实现、不导出也不许改,这里的量小(两个 map,各三四项),独立维护一份比
 * 为了复用两行常量去改一个"明确不能动"的文件更划算。
 */
const CLIENT_LABELS: Record<string, string> = {
  claude: 'Claude Code',
  codex: 'Codex',
  workbuddy: 'WorkBuddy',
}

const SCOPE_LABELS: Record<string, string> = {
  global: '全局',
  project: '当前项目',
}

function describeTarget(target: ClientTarget): string {
  const client = CLIENT_LABELS[target.client] ?? target.client
  const scope = SCOPE_LABELS[target.scope] ?? target.scope
  return `${client} · ${scope}(${target.skillsSubDir})`
}

/** `(client, scope)` 编码成 `<select>` 能用的单一字符串键,反向解析见 `parseTargetKey`。 */
function targetKey(client: string, scope: string): string {
  return `${client}:${scope}`
}

function parseTargetKey(key: string): { client: ClientCode; scope: ScopeCode } {
  const [client, scope] = key.split(':')
  return { client: client as ClientCode, scope: scope as ScopeCode }
}

/**
 * 安装单页:已勾选清单(来自 `useInstallCartStore`)+ 客户端与安装级别选择
 * (选项来自 `GET /clients`,只列已实测组合)+ 生成命令。
 *
 * **命令文本不在这里拼。** `POST /bundles` 已经返回拼好的 `powerShellCommand` /
 * `bashCommand`,本页只负责展示与复制(`CommandBlock`),自己拼模板等于制造第二个
 * 事实源。
 */
export default function InstallPage() {
  const items = useInstallCartStore((state) => state.items)
  const client = useInstallCartStore((state) => state.client)
  const scope = useInstallCartStore((state) => state.scope)
  const setClient = useInstallCartStore((state) => state.setClient)
  const setScope = useInstallCartStore((state) => state.setScope)
  const removeItem = useInstallCartStore((state) => state.removeItem)

  const clientsQuery = useInstallClients()
  const createBundle = useCreateInstallBundle()
  const [bundle, setBundle] = useState<InstallBundleResult | null>(null)

  if (items.length === 0) {
    return (
      <EmptyState
        title="安装单是空的"
        description="先回到列表页勾选想要安装的 skill,再回到这里生成安装命令。"
        action={
          <Link
            to="/"
            className="inline-flex h-10 items-center rounded-control bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
          >
            回到列表页
          </Link>
        }
      />
    )
  }

  const targetOptions = (clientsQuery.data ?? []).map((target) => ({
    value: targetKey(target.client, target.scope),
    label: describeTarget(target),
  }))

  function handleTargetChange(value: string) {
    const parsed = parseTargetKey(value)
    setClient(parsed.client)
    setScope(parsed.scope)
  }

  function handleGenerate() {
    const bundleItems: InstallBundleItem[] = items.map(({ skillId, versionId }) => ({
      skillId,
      versionId,
    }))
    createBundle.mutate({ items: bundleItems, client, scope }, { onSuccess: setBundle })
  }

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">生成安装命令</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          已勾选 {items.length} 个 skill,选择目标客户端与安装级别后生成安装命令。
        </p>
      </div>

      <ul aria-label="已勾选的 skill" className="flex flex-col gap-2">
        {items.map((item) => (
          <li
            key={item.skillId}
            className="flex items-center justify-between rounded-card border border-gray-200 px-4 py-2 dark:border-gray-800"
          >
            <span className="text-sm text-gray-900 dark:text-gray-100">
              {item.skillDisplayName}
            </span>
            <Button variant="ghost" size="sm" onClick={() => removeItem(item.skillId)}>
              移除
            </Button>
          </li>
        ))}
      </ul>

      {clientsQuery.isPending && <Spinner size="sm" label="正在读取可用客户端" />}
      {clientsQuery.isError && (
        <p className="text-sm text-danger-600 dark:text-danger-400">
          读取可用客户端失败,请稍后重试
        </p>
      )}

      {targetOptions.length > 0 && (
        <Select
          label="安装客户端与级别"
          value={targetKey(client, scope)}
          onChange={handleTargetChange}
          options={targetOptions}
        />
      )}

      <Button
        onClick={handleGenerate}
        loading={createBundle.isPending}
        disabled={targetOptions.length === 0}
      >
        生成安装命令
      </Button>

      {createBundle.isError && (
        <ErrorBanner error={createBundle.error as ApiError} onRetry={handleGenerate} />
      )}

      {bundle && (
        <div className="flex flex-col gap-4 rounded-card border border-gray-200 p-4 dark:border-gray-800">
          <CommandBlock label="PowerShell(Windows)" command={bundle.powerShellCommand} />
          <CommandBlock label="bash(macOS / Linux / Git Bash)" command={bundle.bashCommand} />
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {/* 有效期要显式告诉人:旧命令在群里流传半年后还能装到过期版本,正是它要防的事。 */}
            该命令在 {new Date(bundle.expiresAt).toLocaleString()} 之后失效,过期后重新生成即可。
          </p>
        </div>
      )}
    </div>
  )
}
