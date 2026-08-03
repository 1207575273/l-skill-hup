import { useState } from 'react'
import type { ApiError } from '../api/client'
import type { ClientTarget, InstallBundleResult } from '../api/types'
import { useCreateInstallBundle } from '../hooks/useInstallMutations'
import { useInstallClients } from '../hooks/useInstallQueries'
import { useSkillVersions } from '../hooks/useSkillQueries'
import { Button } from './Button'
import { CommandBlock } from './CommandBlock'
import { Modal } from './Modal'
import { Spinner } from './Spinner'

/**
 * "快速接入":选客户端与安装级别,当场拿到可复制的安装命令。
 *
 * <p>展示约定 7.4 把选项收在下拉里、而不是跳详情页再选,理由写得很直白:<b>大多数人是照着别人推荐来装的,
 * 不需要读详情</b>。少两次点击直接影响流通性,而流通性是这个平台唯一的价值。所以这个部件同时挂在列表卡片
 * 和详情页上 —— 两处共用一份实现,不各写一份。
 *
 * <p><b>下拉的开关由本组件自己持有,调用方不要再包一层 `Dropdown`。</b>初版是"卡片提供下拉外壳、这里只提供
 * 面板内容",结果两边各有一层触发器,用户要点两次"快速接入"才看得到客户端列表 —— 那正好把 7.4 想省掉的点击
 * 又加了回来。自己持有还有两个连带好处:展示命令的 `Modal` 不会随下拉收起被卸载;查询能挂在展开状态上。
 *
 * <p><b>两个查询都挂 `enabled: open`。</b>列表页一屏几十张卡片,恒开的话一进页面就是几十个并发请求。
 *
 * <p><b>命令文本不在前端拼。</b>后端 `CreateBundleResponse` 已经返回拼好的 `powerShellCommand` /
 * `bashCommand`,前端只负责展示与复制。自己拼模板等于制造第二个事实源,后端哪天改了 URL 形态或加了参数,
 * 前端不会跟着变而且没人会发现。
 */
export interface QuickInstallMenuProps {
  readonly skillId: number
  /**
   * 指定要安装的版本。**不传就装最新版**(展开时才去查版本列表)。
   * 详情页让用户选了历史版本时传它;列表卡片不传 —— 列表项里本来就没有版本 id。
   */
  readonly versionId?: number
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
  const [open, setOpen] = useState(false)
  const [bundle, setBundle] = useState<InstallBundleResult | null>(null)

  const needsVersionLookup = versionId === undefined
  const clientsQuery = useInstallClients({ enabled: open })
  const versionsQuery = useSkillVersions(skillId, { enabled: open && needsVersionLookup })
  const createBundle = useCreateInstallBundle()

  // 版本列表按 created_at DESC, id DESC 排(SkillMapper.xml 的 selectVersionsBySkillId),
  // 所以第一条就是最新版本 —— 这是读 SQL 确认的,不是假设。
  const effectiveVersionId = versionId ?? versionsQuery.data?.[0]?.id ?? null

  function handlePick(target: ClientTarget) {
    if (effectiveVersionId === null) {
      return
    }
    createBundle.mutate(
      {
        items: [{ skillId, versionId: effectiveVersionId }],
        client: target.client,
        scope: target.scope,
      },
      {
        onSuccess: (result) => {
          setBundle(result)
          setOpen(false)
        },
      },
    )
  }

  const loading =
    clientsQuery.isLoading ||
    (needsVersionLookup && versionsQuery.isLoading) ||
    createBundle.isPending

  return (
    <div className="relative inline-block">
      <Button
        size="sm"
        variant="primary"
        aria-expanded={open}
        aria-haspopup="menu"
        loading={createBundle.isPending}
        onClick={() => setOpen((prev) => !prev)}
      >
        快速接入
      </Button>

      {open ? (
        <div
          // biome-ignore lint/a11y/useSemanticElements: 这是下拉面板容器,内部条目才是 menuitem
          role="menu"
          className="absolute right-0 z-20 mt-1 min-w-56 rounded-control border border-gray-200 bg-white p-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
        >
          {loading ? (
            <div className="px-3 py-2">
              <Spinner size="sm" label="正在读取可用客户端" />
            </div>
          ) : null}

          {clientsQuery.isError || versionsQuery.isError ? (
            <p className="px-3 py-2 text-sm text-danger-600 dark:text-danger-400">
              读取失败,请稍后重试
            </p>
          ) : null}

          {!loading &&
          needsVersionLookup &&
          versionsQuery.isSuccess &&
          effectiveVersionId === null ? (
            <p className="px-3 py-2 text-sm text-gray-500 dark:text-gray-400">
              该 skill 暂无可安装版本
            </p>
          ) : null}

          {/*
            只列出后端返回的组合。适配表里未实测的组合(当前是 workbuddy 的项目级)后端压根不返回,
            所以这里不需要、也不该自己判断哪些能装 —— 猜路径是明令禁止的。
          */}
          {!loading && effectiveVersionId !== null
            ? clientsQuery.data?.map((target) => (
                <button
                  key={`${target.client}-${target.scope}`}
                  type="button"
                  role="menuitem"
                  className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700"
                  onClick={() => handlePick(target)}
                >
                  <span className="block">{describeTarget(target)}</span>
                  <span className="block text-xs text-gray-500 dark:text-gray-400">
                    {target.skillsSubDir}
                  </span>
                </button>
              ))
            : null}
        </div>
      ) : null}

      {createBundle.isError ? (
        <p className="mt-1 text-sm text-danger-600 dark:text-danger-400">
          生成安装命令失败:{(createBundle.error as ApiError).message}
        </p>
      ) : null}

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
    </div>
  )
}
