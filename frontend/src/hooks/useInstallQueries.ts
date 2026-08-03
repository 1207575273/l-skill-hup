import { useQuery } from '@tanstack/react-query'
import { fetchInstallClients } from '../api/install'
import { clientKeys } from '../api/queryKeys'

/** `GET /clients` 变化极少(`queryKeys.ts` clientKeys 的注释),给一个较长的 staleTime。 */
const CLIENT_TARGET_STALE_TIME_MS = 10 * 60_000

/**
 * `GET /clients`,已实测的 (client, scope) 适配表,匿名可用。
 *
 * 支持 `enabled` 是有实际用途的:列表页一屏几十张卡片,每张都挂一个"快速接入",
 * 恒开的话一进页面就是几十个并发请求。挂在下拉的展开状态上,只有真点开的那张才发。
 */
export function useInstallClients(options?: { readonly enabled?: boolean }) {
  return useQuery({
    queryKey: clientKeys.all,
    queryFn: fetchInstallClients,
    staleTime: CLIENT_TARGET_STALE_TIME_MS,
    enabled: options?.enabled ?? true,
  })
}
