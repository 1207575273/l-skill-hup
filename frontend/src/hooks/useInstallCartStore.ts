import { create } from 'zustand'
import type { ClientCode, InstallBundleItem, ScopeCode } from '../api/types'

/**
 * 安装购物车(客户端状态,P0)。
 *
 * P0 的核心交互:列表页勾选多个 skill -> 生成一条安装命令。这份勾选集合的真相
 * 只在浏览器里、不在服务端,因此归 Zustand 管,不进 TanStack Query
 * (判断依据见 frontend/CLAUDE.md 第三节:这份数据的真相在服务器上吗?不在)。
 *
 * **P2 上线收藏夹(`skill_favorite`,业务建模文档 4.11)后,本 store 要整体删除,
 * 不与收藏夹并存**(依据业务建模文档第九章 P2:"收藏与收藏夹(上线后删掉前端的
 * Zustand 购物车,不并存)")。两套都能"攒一批 skill"的机制并存,会让用户不知道
 * 该用哪个。届时"勾选生成安装单"的入口改成从收藏夹页面发起。
 */

/**
 * 购物车里的一项。比 `InstallBundleItem` 多存一个展示名,供购物车抽屉直接渲染,
 * 不必反查列表页的 Query 缓存。这不是"把服务端数据塞进 Zustand"——它只是加入
 * 那一刻的只读快照,不订阅、不失效、不承担"真相"职责,真正生成安装单时服务端
 * 会按 skillId/versionId 重新校验。
 */
export interface InstallCartItem extends InstallBundleItem {
  readonly skillDisplayName: string
}

interface InstallCartState {
  readonly items: readonly InstallCartItem[]
  readonly client: ClientCode
  readonly scope: ScopeCode
  addItem: (item: InstallCartItem) => void
  removeItem: (skillId: number) => void
  toggleItem: (item: InstallCartItem) => void
  clear: () => void
  setClient: (client: ClientCode) => void
  setScope: (scope: ScopeCode) => void
}

const DEFAULT_CLIENT: ClientCode = 'claude'
const DEFAULT_SCOPE: ScopeCode = 'global'

export const useInstallCartStore = create<InstallCartState>((set, get) => ({
  items: [],
  client: DEFAULT_CLIENT,
  scope: DEFAULT_SCOPE,

  addItem: (item) =>
    set((state) =>
      state.items.some((existing) => existing.skillId === item.skillId)
        ? state
        : { items: [...state.items, item] },
    ),

  removeItem: (skillId) =>
    set((state) => ({
      items: state.items.filter((existing) => existing.skillId !== skillId),
    })),

  toggleItem: (item) => {
    const alreadySelected = get().items.some((existing) => existing.skillId === item.skillId)
    if (alreadySelected) {
      get().removeItem(item.skillId)
    } else {
      get().addItem(item)
    }
  },

  clear: () => set({ items: [] }),
  setClient: (client) => set({ client }),
  setScope: (scope) => set({ scope }),
}))

/**
 * 组件用 selector 订阅,不整个 store 取出来(frontend/CLAUDE.md 第三节):
 * 只关心数量的组件(如导航栏角标)用这个,`client`/`scope` 变化不会触发它重渲染。
 */
export function useInstallCartCount(): number {
  return useInstallCartStore((state) => state.items.length)
}

/** 组件判断某个 skill 是否已在购物车里(如列表卡片上的勾选态)。 */
export function useIsSkillInInstallCart(skillId: number): boolean {
  return useInstallCartStore((state) => state.items.some((item) => item.skillId === skillId))
}
