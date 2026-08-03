import AppRouter from './app/router'

/**
 * 应用入口组件。真正的路由表在 `app/router.tsx`,这里只做挂载,
 * 不写任何页面视觉 —— 全局 Provider(Query/Router)在 `app/AppProviders.tsx`。
 */
export default function App() {
  return <AppRouter />
}
