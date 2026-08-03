import { apiGet, apiPost } from './client'
import type { CreateUserPayload, PageResponse, UserListFilter, UserSummary } from './types'

/** 用户管理资源函数(ADMIN 专属),对应 `interfaces/rest/UserController.java`。 */

/** `GET /users`。 */
export function fetchUsers(filter: UserListFilter = {}): Promise<PageResponse<UserSummary>> {
  return apiGet<PageResponse<UserSummary>>('/users', {
    query: { page: filter.page, size: filter.size, provider: filter.provider },
  })
}

/** `POST /users`,P0 唯一开户途径,`role` 缺省时后端按 `USER` 处理。 */
export function createUser(payload: CreateUserPayload): Promise<UserSummary> {
  return apiPost<UserSummary>('/users', payload)
}

/** `POST /users/{id}/disable`。 */
export function disableUser(userId: number): Promise<void> {
  return apiPost<void>(`/users/${userId}/disable`)
}

/** `POST /users/{id}/enable`。 */
export function enableUser(userId: number): Promise<void> {
  return apiPost<void>(`/users/${userId}/enable`)
}
