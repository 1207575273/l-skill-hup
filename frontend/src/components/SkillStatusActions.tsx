import type { ManagedSkill } from '../api/types'
import { useRelistSkill, useUnlistSkill } from '../hooks/useSkillMutations'
import { Button } from './Button'

export interface SkillStatusActionsProps {
  readonly skillId: number
  readonly status: ManagedSkill['status']
}

/**
 * 管理页里单行的上下架按钮。
 *
 * <p><b>为什么是独立组件而不是在表格里内联。</b>`useUnlistSkill` / `useRelistSkill` 都以 `skillId`
 * 为参数,而 hook 不能在 `items.map(...)` 里调用(hooks 规则:数量与顺序必须在每次渲染间稳定)。
 * 把它拆成一个按行渲染的组件,每行各自持有自己的 mutation,是唯一干净的写法。
 *
 * <p>两个动作互斥地只显示一个:已发布只能下架,已下架只能恢复。同时摆两个按钮会让人以为存在
 * "既非上架也非下架"的第三种状态。
 */
export function SkillStatusActions({ skillId, status }: SkillStatusActionsProps) {
  const unlistMutation = useUnlistSkill(skillId)
  const relistMutation = useRelistSkill(skillId)

  if (status === 'UNLISTED') {
    return (
      <Button
        variant="secondary"
        size="sm"
        loading={relistMutation.isPending}
        onClick={() => relistMutation.mutate()}
      >
        恢复上架
      </Button>
    )
  }

  if (status === 'PUBLISHED') {
    return (
      <Button
        variant="secondary"
        size="sm"
        loading={unlistMutation.isPending}
        onClick={() => unlistMutation.mutate()}
      >
        下架
      </Button>
    )
  }

  // **两个已知值都不匹配时什么都不渲染,而不是回落到"下架"。**
  //
  // 这里原本写的是 `if (UNLISTED) 恢复上架; else 下架`,踩过一次:服务端一度返回的 DTO 里没有 status 字段,
  // 前端拿到 undefined,于是**每一行都渲染成"下架"** —— 包括已经下架的。那个 bug 的可怕之处在于按钮本身
  // 看起来完全正常,只是做的事跟你要的相反,而界面上没有任何异常迹象。
  //
  // 少一个按钮是能被一眼看见的故障,给一个反向按钮不是。所以这里宁可什么都不给。
  return null
}
