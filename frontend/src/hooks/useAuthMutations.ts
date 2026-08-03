import { useMutation, useQueryClient } from '@tanstack/react-query'
import { changePassword, login, logout, register } from '../api/auth'
import { setAuthToken } from '../api/client'
import { authKeys } from '../api/queryKeys'
import type {
  ChangePasswordPayload,
  LoginPayload,
  LoginResult,
  RegisterPayload,
} from '../api/types'

/**
 * 认证写侧钩子。**token 的写入/清除(`setAuthToken`)是这一层的职责,不在 `api/auth.ts` 里**——
 * 那边只管发请求、拿响应,把它当成一次业务副作用去处理才是 `hooks/` 该干的事
 * (frontend/CLAUDE.md 第一节"UI 与业务逻辑分离")。
 */

function rememberSession(result: LoginResult): void {
  setAuthToken(result.token)
}

/** `POST /auth/login`。登录成功后写 token,并让 `GET /auth/me`/`GET /identities` 重新拉取。 */
export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: LoginPayload) => login(payload),
    onSuccess: (result: LoginResult) => {
      rememberSession(result)
      queryClient.invalidateQueries({ queryKey: authKeys.me })
      queryClient.invalidateQueries({ queryKey: authKeys.identities })
    },
  })
}

/** `POST /auth/register`,后端注册成功即签发会话,失效范围与 {@link useLogin} 相同。 */
export function useRegister() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: RegisterPayload) => register(payload),
    onSuccess: (result: LoginResult) => {
      rememberSession(result)
      queryClient.invalidateQueries({ queryKey: authKeys.me })
      queryClient.invalidateQueries({ queryKey: authKeys.identities })
    },
  })
}

/**
 * `POST /auth/logout`。清 token 并清空**全部**查询缓存(`queryClient.clear()`),不是只失效
 * auth 相关 key——上一个用户看到的数据不该原样留给下一个访问者。
 */
export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      setAuthToken(null)
      queryClient.clear()
    },
  })
}

/**
 * `POST /identities/change-password`。改密码不改变任何已缓存资源的展示内容,不需要失效 query;
 * 成功与否都由调用方(表单)据 mutation 状态展示反馈。
 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (payload: ChangePasswordPayload) => changePassword(payload),
  })
}
