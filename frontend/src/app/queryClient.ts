import { QueryClient } from '@tanstack/react-query'

/**
 * 全局唯一的 QueryClient 实例。放独立文件是为了让测试也能拿到同一个实例
 * (或按需替换成测试专用实例),不要在组件里 `new QueryClient()`。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 内网工具,数据变动不频繁,别让每次切页都重新拉。
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
})
