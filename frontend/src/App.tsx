/**
 * 脚手架占位页。
 *
 * 真正的页面进 pages/,组件进 components/,请求与状态进 hooks/。
 * 这里只做组合,不写业务细节。
 */
export default function App() {
  return (
    <div className="min-h-screen bg-white text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <header className="border-b border-slate-200 dark:border-slate-800">
        <div className="mx-auto flex max-w-5xl items-center gap-3 px-6 py-4">
          <span className="rounded-card bg-brand-500 px-2 py-1 text-sm font-semibold text-white">
            skill-hub
          </span>
          <span className="text-sm text-slate-500 dark:text-slate-400">
            团队内部 Agent Skill 分发平台
          </span>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-10">
        <h1 className="text-2xl font-semibold">脚手架已就绪</h1>
        <p className="mt-2 text-slate-600 dark:text-slate-400">
          下一步:skill 列表、详情、发布与安装选择页。
        </p>
      </main>
    </div>
  )
}
