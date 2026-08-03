import type { SkillListFilter, TagKind, UserListFilter } from './types'

/**
 * TanStack Query 查询键(query key)命名约定。
 *
 * 服务端数据一律走 TanStack Query,不许塞进 Zustand(frontend/CLAUDE.md 第三节)。
 * 这份文件是查询键的**唯一事实源**,`hooks/` 里写 `useQuery` 时一律从这里取键,
 * 不许在 hook 里手写字面量数组 —— 手写的键在 `invalidateQueries` 时极易漏改。
 *
 * 约定:
 * 1. 每个业务域导出一个 `xxxKeys` 对象,数组的第一项固定是域名(如 `'skills'`),
 *    用于按域粗粒度失效(`queryClient.invalidateQueries({ queryKey: skillKeys.all })`)。
 * 2. 层级从粗到细:`all -> lists()/details() -> list(filter)/detail(id) -> 子资源`。
 *    这样"精确失效一条详情"与"让整域重新拉取"都只需要取对应层级的键。
 * 3. 键数组里只放**可序列化的原始值或普通对象**(number / string / boolean / plain
 *    object),不放函数、类实例、Date —— TanStack Query 用结构化比较判断键是否相同,
 *    放不可序列化的值会导致比较结果不稳定。
 * 4. 每个 key 工厂函数都显式返回 `as const`,保证 TS 把数组推断成元组而不是
 *    `unknown[]`,`useQuery` 的返回类型才能被正确收窄。
 */

export const skillKeys = {
  all: ['skills'] as const,
  lists: () => [...skillKeys.all, 'list'] as const,
  list: (filter: SkillListFilter) => [...skillKeys.lists(), filter] as const,
  /**
   * 管理视图列表。**必须与公开列表 `lists()` 分开**:两者返回的集合不同(管理视图含已下架),
   * 共用一个 key 会让下架后的公开列表读到带下架项的缓存,反之亦然。
   */
  managed: (filter: unknown) => [...skillKeys.all, 'managed', filter] as const,
  managedAll: () => [...skillKeys.all, 'managed'] as const,
  details: () => [...skillKeys.all, 'detail'] as const,
  detail: (id: number) => [...skillKeys.details(), id] as const,
  versions: (id: number) => [...skillKeys.detail(id), 'versions'] as const,
  /** 评论。挂在 detail 之下,删 skill 时一并失效;带页码,翻页各自缓存。 */
  comments: (id: number, page: number) => [...skillKeys.detail(id), 'comments', page] as const,
  commentsAll: (id: number) => [...skillKeys.detail(id), 'comments'] as const,
  /**
   * 单个文件的内容。带上 versionId 与 path 两级 —— 同一个 skill 的不同版本、同一版本的不同文件
   * 都是各自独立的缓存条目,切版本时不能复用上一版的内容。
   */
  file: (id: number, versionId: number, path: string) =>
    [...skillKeys.detail(id), 'versions', versionId, 'file', path] as const,
}

export const tagKeys = {
  all: ['tags'] as const,
  list: (kind?: TagKind) => [...tagKeys.all, 'list', kind ?? 'ALL'] as const,
  suggest: (keyword: string) => [...tagKeys.all, 'suggest', keyword] as const,
}

export const clientKeys = {
  /** GET /clients,已实测的 (client, scope) 适配表,变化极少,可长 staleTime。 */
  all: ['clients'] as const,
}

export const authKeys = {
  me: ['auth', 'me'] as const,
  providers: ['auth', 'providers'] as const,
  identities: ['identities'] as const,
}

export const userKeys = {
  /** `GET /users`,ADMIN 专属(`UserController`)。 */
  all: ['users'] as const,
  lists: () => [...userKeys.all, 'list'] as const,
  list: (filter: UserListFilter) => [...userKeys.lists(), filter] as const,
}
