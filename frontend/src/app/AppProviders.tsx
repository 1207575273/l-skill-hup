import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { BrowserRouter } from 'react-router'
import { queryClient } from './queryClient'

/**
 * 必须与 `vite.config.ts` 的 `base`(`/skillhub/`)以及后端 `server.servlet.context-path`
 * (`/skillhub`)对齐,否则路由在打包部署后会整体错位(参见 frontend/CLAUDE.md 第五节)。
 * `BrowserRouter` 的 `basename` 约定不带末尾斜杠。
 */
const ROUTER_BASENAME = '/skillhub'

/**
 * 组合根:TanStack Query(服务端数据)+ react-router(路由)。
 * Zustand 不需要 Provider,直接在组件里 import store 使用。
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter basename={ROUTER_BASENAME}>{children}</BrowserRouter>
    </QueryClientProvider>
  )
}
