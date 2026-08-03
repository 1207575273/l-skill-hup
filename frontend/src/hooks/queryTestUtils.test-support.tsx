/**
 * `hooks/` 查询/变更测试的共享基础设施。**只给测试用,不被任何非测试代码引用。**
 *
 * `renderHook` 跑 `useQuery`/`useMutation` 必须包一层 `QueryClientProvider`,每个测试用例都要一个
 * 全新的 `QueryClient`(不共享缓存,否则用例之间互相污染)。文件名用 `.test-support.ts` 而不是
 * `.ts`,约定同 `lib/skillPackageFixtures.test-support.ts`:不匹配 Vitest 的 `*.test.ts` 收集规则,
 * 不会被当成一个空测试文件执行。
 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement, ReactNode } from 'react'

/** 关掉重试与自动刷新——测试要的是确定性,不是生产环境那套"多试几次再放弃"的容错策略。 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
      mutations: {
        retry: false,
      },
    },
  })
}

export function createQueryWrapper(
  queryClient: QueryClient,
): ({ children }: { children: ReactNode }) => ReactElement {
  return function QueryWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}
