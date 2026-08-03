import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { Button } from '../components/Button'
import { cx } from '../components/classNames'
import { Spinner } from '../components/Spinner'
import { useLogout } from '../hooks/useAuthMutations'
import { useCurrentUser } from '../hooks/useAuthQueries'
import { useInstallCartCount } from '../hooks/useInstallCartStore'

export interface AppLayoutProps {
  readonly children: ReactNode
  /**
   * 登录态插槽:当前用户名 / 登录按钮。不传时渲染默认实现 `NavAuthStatus`——直接消费
   * `useCurrentUser`/`useLogout`(下面的选项 2),已登录显示用户名 + 登出,未登录显示
   * 指向 `/login` 的登录入口。真实业务页面(`router.tsx` 的 `LayoutRoute`)不传这个
   * prop,吃默认实现;测试或未来的特殊场景仍可以传自定义节点整体替换,不锁死这条路。
   */
  readonly authSlot?: ReactNode
}

function navLinkClassName({ isActive }: { isActive: boolean }): string {
  return cx(
    'text-sm font-medium transition-colors',
    isActive
      ? 'text-brand-600 dark:text-brand-500'
      : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100',
  )
}

/**
 * 安装单入口 + 已勾选数量。挂在主导航而不是 `authSlot`——生成安装单匿名也能做
 * (业务建模文档 5.16 权限矩阵:下载 zip / 生成安装单不要求登录),数量与登录态
 * 是两件不相关的事,不该被绑在一起。数量来自 Zustand 购物车(`useInstallCartCount`),
 * 是纯客户端状态,不经 TanStack Query。
 */
function InstallCartNavLink() {
  const count = useInstallCartCount()
  return (
    <NavLink to="/install" className={navLinkClassName}>
      安装单{count > 0 ? ` (${count})` : ''}
    </NavLink>
  )
}

/**
 * 默认登录态展示。`useCurrentUser` 在没有 token 时 `enabled: false`,`data` 恒为
 * `undefined`,天然落到"未登录"分支——不需要额外读 `getAuthToken()` 再判断一次。
 */
function NavAuthStatus() {
  const currentUserQuery = useCurrentUser()
  const logoutMutation = useLogout()

  if (currentUserQuery.data) {
    return (
      <div className="flex items-center gap-3">
        <span className="text-sm text-gray-700 dark:text-gray-300">
          {currentUserQuery.data.displayName}
        </span>
        <Button
          variant="ghost"
          size="sm"
          loading={logoutMutation.isPending}
          onClick={() => logoutMutation.mutate()}
        >
          登出
        </Button>
      </div>
    )
  }

  // 只有"有 token 但还没拿到 /auth/me 结果"这一小段窗口期才是加载态,
  // 没有 token 时 fetchStatus 恒为 idle,不会走进这个分支。
  if (currentUserQuery.isFetching) {
    return <Spinner size="sm" label="正在确认登录状态" />
  }

  return (
    <Link
      to="/login"
      className="text-sm font-medium text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
    >
      登录
    </Link>
  )
}

/**
 * 全站布局:顶部导航(站点名 + 浏览/上传/安装单入口)+ 内容区 + 登录态插槽。
 * 放在 `app/` 而不是 `components/`,因为它是路由级的组合根 chrome,和
 * `router.tsx`/`AppProviders.tsx` 一样只做装配,不是可复用的展示组件。
 */
export function AppLayout({ children, authSlot }: AppLayoutProps) {
  return (
    <div className="min-h-screen bg-white text-gray-900 dark:bg-gray-950 dark:text-gray-100">
      <header className="border-b border-gray-200 dark:border-gray-800">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
          <div className="flex items-center gap-6">
            <Link to="/" className="text-lg font-semibold text-brand-600 dark:text-brand-500">
              skill-hub
            </Link>
            <nav className="flex items-center gap-4" aria-label="主导航">
              <NavLink to="/" end className={navLinkClassName}>
                浏览
              </NavLink>
              <NavLink to="/upload" className={navLinkClassName}>
                上传
              </NavLink>
              <InstallCartNavLink />
            </nav>
          </div>
          <div>{authSlot ?? <NavAuthStatus />}</div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  )
}
