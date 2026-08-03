import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

/**
 * `findBy*` / `waitFor` 的等待上限。Testing Library 默认 1000ms,对本项目太紧。
 *
 * 页面测试的典型形态是"渲染 -> TanStack Query 的 mock promise resolve -> 重渲染出结果",
 * 这条链本身很快,但 vitest 把几十个测试文件并行铺开时 CPU 是抢的,单个 worker 被挂起几百毫秒
 * 很正常。实测症状:`SkillListPage` 的卡片渲染用例单独跑必过,全量跑必挂,失败时 DOM 停在
 * "正在加载 skill 列表"——查询根本还没 resolve,不是断言写错了。
 *
 * **放宽超时不会把真失败变成通过**:断言永远不会满足的用例仍然会失败,只是多等几秒;
 * 它挡掉的只有"机器忙"这一种假阳性。反过来,留着 1000ms 的代价是每次机器负载稍高就红一片,
 * 而红得毫无规律的套件很快就没人信了。
 */
configure({ asyncUtilTimeout: 5000 })

// 未开 globals,组件测试靠这里清理 DOM,不要依赖隐式全局钩子
afterEach(() => {
  cleanup()
})
