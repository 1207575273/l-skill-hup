import { useState } from 'react'
import type { ApiError } from '../api/client'
import type { ClientTarget, InstallBundleResult } from '../api/types'
import { useCreateInstallBundle } from '../hooks/useInstallMutations'
import { useInstallClients } from '../hooks/useInstallQueries'
import { Button } from './Button'
import { Dropdown } from './Dropdown'
import { ErrorBanner } from './ErrorBanner'
import { Modal } from './Modal'
import { Spinner } from './Spinner'

/**
 * "快速接入":选客户端与安装级别,当场拿到可复制的安装命令。
 *
 * <p>展示约定 7.4 把选项收在下拉里、而不是跳详情页再选,理由写得很直白:<b>大多数人是照着别人推荐来装的,
 * 不需要读详情</b>。少两次点击直接影响流通性,而流通性是这个平台唯一的价值。所以这个部件同时挂在列表卡片
 * 和详情页上 —— 两处共用一份实现,不各写一份。
 *
 * <p><b>命令文本不在前端拼。</b>后端 `CreateBundleResponse` 已经返回拼好的 `powerShellCommand` /
 * `bashCommand`,前端只负责展示与复制。自己拼模板等于制造第二个事实源,后端哪天改了 URL 形态或加了参数,
 * 前端不会跟着变而且没人会发现。
 */
export interface QuickInstallMenuProps {
  readonly skillId: number
  /** 要安装的版本 id。列表页传最新版本,详情页可以传用户选中的历史版本。 */
  readonly versionId: number
}

/** 客户端与安装级别的中文展示名。适配表本身来自 `GET /clients`,这里只负责把 code 翻成人话。 */
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
  return `${client} · ${scope}`
}

export function QuickInstallMenu({ skillId, versionId }: QuickInstallMenuProps) {
  const clientsQuery = useInstallClients()
  const createBundle = useCreateInstallBundle()
  const [bundle, setBundle] = useState<InstallBundleResult | null>(null)

  function handlePick(target: ClientTarget) {
    createBundle.mutate(
      { items: [{ skillId, versionId }], client: target.client, scope: target.scope },
      { onSuccess: setBundle },
    )
  }

  return (
    <>
      <Dropdown
        align="end"
        renderTrigger={({ onClick, open }) => (
          <Button
            size="sm"
            variant="primary"
            onClick={onClick}
            aria-expanded={open}
            loading={createBundle.isPending}
          >
            快速接入
          </Button>
        )}
      >
        <div className="min-w-52 p-1">
          {clientsQuery.isPending ? (
            <div className="px-3 py-2">
              <Spinner size="sm" label="正在读取可用客户端" />
            </div>
          ) : null}

          {clientsQuery.isError ? (
            <p className="px-3 py-2 text-sm text-danger-600">读取可用客户端失败,请稍后重试</p>
          ) : null}

          {/*
            只列出后端返回的组合。适配表里未实测的组合(当前是 workbuddy 的项目级)后端压根不返回,
            所以这里不需要、也不该自己判断哪些能装 —— 猜路径是明令禁止的。
          */}
          {clientsQuery.data?.map((target) => (
            <button
              key={`${target.client}-${target.scope}`}
              type="button"
              className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700"
              onClick={() => handlePick(target)}
            >
              <span className="block">{describeTarget(target)}</span>
              <span className="block text-xs text-gray-500 dark:text-gray-400">
                {target.skillsSubDir}
              </span>
            </button>
          ))}
        </div>
      </Dropdown>

      {createBundle.isError ? <ErrorBanner error={createBundle.error as ApiError} /> : null}

      <Modal
        open={bundle !== null}
        title="安装命令"
        onClose={() => setBundle(null)}
        actions={[{ label: '关闭', onClick: () => setBundle(null), variant: 'secondary' }]}
      >
        {bundle === null ? null : (
          <div className="space-y-4">
            <CommandBlock label="PowerShell(Windows)" command={bundle.powerShellCommand} />
            <CommandBlock label="bash(macOS / Linux / Git Bash)" command={bundle.bashCommand} />
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {/* 有效期要显式告诉人:旧命令在群里流传半年后还能装到过期版本,正是它要防的事。 */}
              该命令在 {new Date(bundle.expiresAt).toLocaleString()} 之后失效,过期后重新生成即可。
            </p>
          </div>
        )}
      </Modal>
    </>
  )
}

function CommandBlock({ label, command }: { readonly label: string; readonly command: string }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(command)
    setCopied(true)
  }

  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <span className="text-sm font-medium">{label}</span>
        <Button size="sm" variant="ghost" onClick={handleCopy}>
          {copied ? '已复制' : '复制'}
        </Button>
      </div>
      <pre className="overflow-x-auto rounded bg-gray-900 p-3 text-xs text-gray-100">
        <code>{command}</code>
      </pre>
    </div>
  )
}
