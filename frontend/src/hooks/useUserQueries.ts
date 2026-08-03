import { useQuery } from '@tanstack/react-query'
import { userKeys } from '../api/queryKeys'
import type { UserListFilter } from '../api/types'
import { fetchUsers } from '../api/users'

/** `GET /users`(ADMIN 专属),用户管理页列表。 */
export function useUsers(filter: UserListFilter = {}) {
  return useQuery({
    queryKey: userKeys.list(filter),
    queryFn: () => fetchUsers(filter),
  })
}
