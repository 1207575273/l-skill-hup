import { apiGet, apiPost } from './client'
import type {
  AuthProviders,
  ChangePasswordPayload,
  CurrentUser,
  IdentitySummary,
  LoginPayload,
  LoginResult,
  RegisterPayload,
} from './types'

/**
 * 认证资源函数,对应 `AuthController.java` 与 `IdentityController.java`。
 *
 * **不在这里调用 `setAuthToken`**——写入/清除 token 是一次业务副作用(登录成功后记住会话、
 * 登出后忘记会话),属于 `hooks/` 的职责,资源函数只管发请求、拿响应。
 */

/** `POST /auth/login`。 */
export function login(payload: LoginPayload): Promise<LoginResult> {
  return apiPost<LoginResult>('/auth/login', payload)
}

/** `POST /auth/register`,受服务端 `localRegisterEnabled` 开关控制,关闭时后端直接拒绝。 */
export function register(payload: RegisterPayload): Promise<LoginResult> {
  return apiPost<LoginResult>('/auth/register', payload)
}

/** `POST /auth/logout`(需登录),成功时 204 No Content。 */
export function logout(): Promise<void> {
  return apiPost<void>('/auth/logout')
}

/** `GET /auth/me`(需登录)。 */
export function fetchCurrentUser(): Promise<CurrentUser> {
  return apiGet<CurrentUser>('/auth/me')
}

/** `GET /auth/providers`,匿名可用,决定登录页展示哪些登录方式按钮与是否显示注册入口。 */
export function fetchAuthProviders(): Promise<AuthProviders> {
  return apiGet<AuthProviders>('/auth/providers')
}

/** `GET /identities`(需登录)。 */
export function fetchIdentities(): Promise<IdentitySummary[]> {
  return apiGet<IdentitySummary[]>('/identities')
}

/**
 * `POST /identities/change-password`(需登录)。改的是**当前会话所用身份**的密码,路径里不带
 * identityId——由服务端按 `user_session.identity_id` 定位,前端不需要也不能自己传。
 */
export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return apiPost<void>('/identities/change-password', payload)
}
