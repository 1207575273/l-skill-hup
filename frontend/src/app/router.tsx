import { Outlet, Route, Routes } from 'react-router'
import InstallPage from '../pages/InstallPage'
import LoginPage from '../pages/LoginPage'
import ManagePage from '../pages/ManagePage'
import RegisterPage from '../pages/RegisterPage'
import SkillDetailPage from '../pages/SkillDetailPage'
import SkillListPage from '../pages/SkillListPage'
import UploadPage from '../pages/UploadPage'
import { AppLayout } from './AppLayout'

/**
 * 布局路由:五个页面都套在 `AppLayout` 里(顶部导航 + 内容区),页面组件本身
 * 不知道自己被套了布局。用 `<Outlet />` 而不是把 `AppLayout` 分别包在每个
 * `<Route element>` 里,是为了让布局只挂载一次,页面切换时导航栏不重新渲染。
 */
function LayoutRoute() {
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}

/**
 * P0 路由骨架。页面组件目前只做最小占位(能路由到、渲染一行文字),
 * 视觉与布局留给后续按 docs/20260802224934_业务建模与接口契约.md 第七章的展示约定单独做。
 *
 * 不含 `<BrowserRouter>`:basename 与 Router 上下文由 `app/AppProviders.tsx` 提供,
 * 这里只放路由表,方便测试时用 `<MemoryRouter>` 单独包裹 `AppRouter`。
 */
export default function AppRouter() {
  return (
    <Routes>
      <Route element={<LayoutRoute />}>
        <Route path="/" element={<SkillListPage />} />
        <Route path="/skills/:id" element={<SkillDetailPage />} />
        <Route path="/upload" element={<UploadPage />} />
        {/* 管理页自己处理未登录(引导登录),路由不做守卫 —— 与上传页同一套:
            把"要不要登录"的判断放路由表里,会让两处各判一次而且迟早不一致。 */}
        <Route path="/manage" element={<ManagePage />} />
        <Route path="/install" element={<InstallPage />} />
        <Route path="/login" element={<LoginPage />} />
        {/* 注册页自己按 /auth/providers 的 localRegisterEnabled 决定放不放行 ——
            路由恒定存在,开关归服务端。之前登录页有指向 /register 的链接、但这条路由不存在,
            点了直接白屏。 */}
        <Route path="/register" element={<RegisterPage />} />
      </Route>
    </Routes>
  )
}
