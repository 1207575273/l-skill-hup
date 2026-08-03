import { useQuery } from '@tanstack/react-query'
import { fetchInstallClients } from '../api/install'
import { clientKeys } from '../api/queryKeys'

/** `GET /clients` 变化极少(`queryKeys.ts` clientKeys 的注释),给一个较长的 staleTime。 */
const CLIENT_TARGET_STALE_TIME_MS = 10 * 60_000

/** `GET /clients`,已实测的 (client, scope) 适配表,匿名可用。 */
export function useInstallClients() {
  return useQuery({
    queryKey: clientKeys.all,
    queryFn: fetchInstallClients,
    staleTime: CLIENT_TARGET_STALE_TIME_MS,
  })
}
