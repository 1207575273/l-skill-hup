import { Route, Routes } from 'react-router'
import InstallPage from '../pages/InstallPage'
import LoginPage from '../pages/LoginPage'
import SkillDetailPage from '../pages/SkillDetailPage'
import SkillListPage from '../pages/SkillListPage'
import UploadPage from '../pages/UploadPage'

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
      <Route path="/" element={<SkillListPage />} />
      <Route path="/skills/:id" element={<SkillDetailPage />} />
      <Route path="/upload" element={<UploadPage />} />
      <Route path="/install" element={<InstallPage />} />
      <Route path="/login" element={<LoginPage />} />
    </Routes>
  )
}
