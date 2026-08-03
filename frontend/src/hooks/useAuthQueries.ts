import { useQuery } from '@tanstack/react-query'
import { fetchAuthProviders, fetchCurrentUser, fetchIdentities } from '../api/auth'
import { getAuthToken } from '../api/client'
import { authKeys } from '../api/queryKeys'

/** 登录方式集合几乎不变,给一个较长的 staleTime。 */
const AUTH_PROVIDERS_STALE_TIME_MS = 10 * 60_000

/**
 * `GET /auth/me`(需登录)。没有 token 时不发请求——发了也是必然的 401,徒增一次网络往返与错误噪音。
 *
 * `enabled` 读的是 `getAuthToken()` 这个模块级变量,不是响应式状态:登录/登出后要靠调用方所在组件
 * 重新渲染(路由跳转、`useLogin`/`useLogout` 触发的状态变化)才会重新求值。P0 的登录/登出都会带来
 * 页面跳转或全局状态变化,这个前提成立;真出现"同一组件里 token 变了但没重渲染"的场景,再补
 * `useSyncExternalStore` 订阅 token 变化。
 */
export function useCurrentUser() {
  return useQuery({
    queryKey: authKeys.me,
    queryFn: fetchCurrentUser,
    enabled: getAuthToken() !== null,
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
  return useQuery({
    queryKey: authKeys.identities,
    queryFn: fetchIdentities,
    enabled: getAuthToken() !== null,
  })
}
