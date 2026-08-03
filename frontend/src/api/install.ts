import { apiGet, apiPost } from './client'
import type {
  ClientCode,
  ClientTarget,
  InstallBundleItem,
  InstallBundleRequest,
  InstallBundleResult,
  ScopeCode,
} from './types'

/**
 * 安装链路资源函数,对应 `interfaces/rest/install` 下的 `InstallController`(挂在 `/api/v1`)与
 * `InstallScriptController`(挂在 `/i`,不是 JSON API)。
 */

/** `GET /clients`,已实测的 (client, scope) 适配表,匿名可用。 */
export function fetchInstallClients(): Promise<ClientTarget[]> {
  return apiGet<ClientTarget[]>('/clients')
}

/** `POST /bundles`,生成安装单,匿名可用(`createdByUserId` 由服务端按当前会话可选写入)。 */
export function createInstallBundle(
  items: InstallBundleItem[],
  client: ClientCode,
  scope: ScopeCode,
): Promise<InstallBundleResult> {
  const payload: InstallBundleRequest = { items, client, scope }
  return apiPost<InstallBundleResult>('/bundles', payload)
}

export type InstallScriptKind = 'ps1' | 'sh'

/**
 * 固定拼 `/skillhub/i/...`,与 `client.ts` 里 `DEFAULT_API_BASE_URL` 一样是硬编码的应用
 * context-path 约定,**不随 `VITE_API_BASE_URL` 覆盖而变化**——脚本端点刻意不挂在 `/api/v1` 下
 * (`InstallScriptController` 类注释),用不同的基址逻辑。
 */
const INSTALL_SCRIPT_BASE_PATH = '/skillhub/i'

/**
 * 拼装安装脚本的下载/直达链接,**不在这里发请求**——脚本内容要被 `curl | sh` / `iwr | iex`
 * 直接执行,不是 JSON API,不走 `apiGet`/`apiPost` 这套错误模型。
 */
export function buildInstallScriptUrl(token: string, kind: InstallScriptKind): string {
  return `${INSTALL_SCRIPT_BASE_PATH}/${token}.${kind}`
}
