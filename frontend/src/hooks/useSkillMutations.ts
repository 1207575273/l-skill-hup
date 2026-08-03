import { useMutation, useQueryClient } from '@tanstack/react-query'
import { skillKeys, tagKeys } from '../api/queryKeys'
import type { PublishSkillPayload, PublishSkillVersionPayload } from '../api/skills'
import {
  precheckSkillPackage,
  publishSkill,
  publishSkillVersion,
  relistSkill,
  unlistSkill,
  updateSkillMetadata,
} from '../api/skills'
import type { SkillDetail, UpdateSkillPayload } from '../api/types'

/**
 * skill 写侧变更钩子。**失效范围是这个文件的核心信息**——发布/改版/下架任一动作漏标了该失效的
 * query key,对应页面就会显示过期数据(最典型的回归:发布完一个 skill,列表页不刷新)。
 */

/** `POST /skills/precheck`,只读不落库,成功/失败都不影响任何已缓存资源,不需要失效。 */
export function usePrecheckSkillPackage() {
  return useMutation({
    mutationFn: precheckSkillPackage,
  })
}

/**
 * `POST /skills`,发布新 skill。
 *
 * 失效:`skillKeys.lists()`(新 skill 要出现在列表里)、`tagKeys.all`(打上的标签 skillCount 变化)。
 * 顺手把详情缓存种进去(`setQueryData`),从发布页跳到详情页时不必再拉一次。
 */
export function usePublishSkill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: PublishSkillPayload) => publishSkill(payload),
    onSuccess: (skill: SkillDetail) => {
      queryClient.setQueryData(skillKeys.detail(skill.id), skill)
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
    },
  })
}

/**
 * `POST /skills/{id}/versions`,给既有 skill 发新版本。
 *
 * 失效:这个 skill 的详情(`latestVersion`/`certification` 随新版本重置为 NONE)与版本列表;
 * 也失效 `skillKeys.lists()`——列表卡片的证书徽章、`updatedAt` 排序都跟着变。
 */
export function usePublishSkillVersion(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: PublishSkillVersionPayload) => publishSkillVersion(skillId, payload),
    onSuccess: (skill: SkillDetail) => {
      queryClient.setQueryData(skillKeys.detail(skillId), skill)
      queryClient.invalidateQueries({ queryKey: skillKeys.versions(skillId) })
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
    },
  })
}

/**
 * `POST /skills/{id}/update`,改展示名/来源/标签。
 *
 * 失效:详情、`skillKeys.lists()`(展示名/来源在卡片上可见)、`tagKeys.all`(标签重新分配后
 * 各标签的 skillCount 都可能变)。
 */
export function useUpdateSkillMetadata(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: UpdateSkillPayload) => updateSkillMetadata(skillId, payload),
    onSuccess: (skill: SkillDetail) => {
      queryClient.setQueryData(skillKeys.detail(skillId), skill)
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
    },
  })
}

/** `POST /skills/{id}/unlist`。失效列表——下架后这条要立刻从列表消失。 */
export function useUnlistSkill(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => unlistSkill(skillId),
    onSuccess: (skill: SkillDetail) => {
      queryClient.setQueryData(skillKeys.detail(skillId), skill)
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
    },
  })
}

/** `POST /skills/{id}/relist`。失效列表——恢复后这条要重新出现在列表。 */
export function useRelistSkill(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => relistSkill(skillId),
    onSuccess: (skill: SkillDetail) => {
      queryClient.setQueryData(skillKeys.detail(skillId), skill)
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
    },
  })
}
