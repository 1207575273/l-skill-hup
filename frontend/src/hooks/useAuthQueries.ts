import { useQuery } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import { fetchAuthProviders, fetchCurrentUser, fetchIdentities } from '../api/auth'
import { getAuthToken, subscribeAuthToken } from '../api/client'
import { authKeys } from '../api/queryKeys'

/** 登录方式集合几乎不变,给一个较长的 staleTime。 */
const AUTH_PROVIDERS_STALE_TIME_MS = 10 * 60_000

/**
 * 当前是否持有 token,**响应式**。
 *
 * 上一版直接在 `enabled` 里写 `getAuthToken() !== null`,并附了一句"登录/登出都会带来页面跳转,
 * 这个前提成立"。实测那个前提不成立:登录成功后 `navigate` 回列表页,`AppLayout` 并不重新挂载,
 * 右上角一直显示"登录",刷新才好。`invalidateQueries` 也救不回来 —— 被 `enabled: false` 关掉的查询,
 * 失效了同样不会发请求。
 *
 * 返回 boolean 而不是 token 本身:`getSnapshot` 必须返回稳定值,原始类型天然满足;
 * 而且调用方只关心"有没有",不关心是什么。
 */
function useHasAuthToken(): boolean {
  return useSyncExternalStore(
    subscribeAuthToken,
    () => getAuthToken() !== null,
    // 服务端渲染快照。本项目目前不做 SSR,给 false 是安全的默认:
    // 宁可首屏当作未登录、水合后再纠正,也不要渲染出一个假的已登录态。
    () => false,
  )
}

/**
 * `GET /auth/me`(需登录)。没有 token 时不发请求——发了也是必然的 401,徒增一次网络往返与错误噪音。
 */
export function useCurrentUser() {
  const hasToken = useHasAuthToken()
  return useQuery({
    queryKey: authKeys.me,
    queryFn: fetchCurrentUser,
    enabled: hasToken,
    retry: false,
  })
}

/** `GET /auth/providers`,匿名可用,登录页据此决定显示哪些登录方式按钮、是否显示注册入口。 */
export function useAuthProviders() {
  return useQuery({
    queryKey: authKeys.providers,
    queryFn: fetchAuthProviders,
    staleTime: AUTH_PROVIDERS_STALE_TIME_MS,
  })
}

/** `GET /identities`(需登录),同 {@link useCurrentUser} 的 enabled 语义。 */
export function useIdentities() {
  const hasToken = useHasAuthToken()
  return useQuery({
    queryKey: authKeys.identities,
    queryFn: fetchIdentities,
    enabled: hasToken,
  })
}
