import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// 未开 globals,组件测试靠这里清理 DOM,不要依赖隐式全局钩子
afterEach(() => {
  cleanup()
})
