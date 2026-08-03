import { useMutation, useQueryClient } from '@tanstack/react-query'
import { userKeys } from '../api/queryKeys'
import type { CreateUserPayload } from '../api/types'
import { createUser, disableUser, enableUser } from '../api/users'

/** 用户管理写侧钩子(ADMIN 专属)。三个动作都只影响 `GET /users` 列表,失效范围统一是 `userKeys.lists()`。 */

/** `POST /users`,新用户要出现在列表里。 */
export function useCreateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateUserPayload) => createUser(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.lists() })
    },
  })
}

/** `POST /users/{id}/disable`,列表里这一行的 `status` 要变。 */
export function useDisableUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) => disableUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.lists() })
    },
  })
}

/** `POST /users/{id}/enable`,同 {@link useDisableUser}。 */
export function useEnableUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) => enableUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.lists() })
    },
  })
}
