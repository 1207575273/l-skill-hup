import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { cx } from '../components/classNames'

export interface AppLayoutProps {
  readonly children: ReactNode
  /**
   * 登录态插槽:当前用户名 / 登录按钮。不传时渲染默认占位(指向 `/login` 的登录
   * 入口链接)。真实认证态由后续任务接入,有两条路都行:
   *   1. 包一层知道当前用户的组件,`<AppLayout authSlot={<UserMenu user={currentUser} />}>`;
   *   2. 或者直接改下面 `DefaultAuthSlot` 的分支,把 `useCurrentUser` 之类的 hook 接进来。
   * 本轮不做登录逻辑,也不调 `api/`,所以只留插槽 + 占位,两条路都不预先关闭。
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

function DefaultAuthSlot() {
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
 * 全站布局:顶部导航(站点名 + 列表/上传入口)+ 内容区 + 登录态插槽。
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
            </nav>
          </div>
          <div>{authSlot ?? <DefaultAuthSlot />}</div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  )
}
