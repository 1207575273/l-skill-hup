import { useMutation, useQueryClient } from '@tanstack/react-query'
import { skillKeys, tagKeys } from '../api/queryKeys'
import type {
  AddCommentPayload,
  PublishSkillPayload,
  PublishSkillVersionPayload,
} from '../api/skills'
import {
  addSkillComment,
  certifySkillVersion,
  deleteSkill,
  deleteSkillComment,
  precheckSkillPackage,
  publishSkill,
  publishSkillVersion,
  relistSkill,
  unlistSkill,
  updateSkillMetadata,
} from '../api/skills'
import type { CertifyVersionRequest, SkillDetail, UpdateSkillPayload } from '../api/types'

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

/**
 * `POST /skills/{id}/unlist`。失效公开列表(下架后这条要立刻消失)**和管理视图**
 * (那边这条不消失,但状态徽章要从"已发布"变成"已下架")。
 *
 * 两处都要标是因为它们是两份独立缓存(`lists()` 与 `managedAll()`)。只标公开列表的话,
 * 在管理页点下架会看起来毫无反应 —— 而管理页恰恰是最常触发这个动作的地方。
 */
export function useUnlistSkill(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => unlistSkill(skillId),
    onSuccess: (skill: SkillDetail) => {
      queryClient.setQueryData(skillKeys.detail(skillId), skill)
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
      queryClient.invalidateQueries({ queryKey: skillKeys.managedAll() })
    },
  })
}

/**
 * `POST /skills/{id}/delete`,**物理删除,不可逆**。
 *
 * 成功后把这条的详情缓存直接**移除**而不是置空:留一个 undefined 的缓存条目,
 * 详情页再进来时会先渲染一帧"加载完成但没数据"的空壳,而正确的表现是 404。
 *
 * 管理视图与公开列表两处缓存都要失效 —— 删掉的 skill 在两边都不该继续出现,
 * 只失效其中一处会让另一处留着一张点进去就 404 的卡片。
 */
export function useDeleteSkill(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deleteSkill(skillId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: skillKeys.detail(skillId) })
      queryClient.invalidateQueries({ queryKey: skillKeys.managedAll() })
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
    },
  })
}

/**
 * `POST /skills/{id}/comments`。
 *
 * **同时失效评论列表与 skill 详情**:详情响应里带着 `commentCount`,只刷评论列表的话,
 * 新评论出现了而上面的计数还是旧的 —— 那种不一致会让人怀疑到底发出去没有。
 */
export function useAddSkillComment(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: AddCommentPayload) => addSkillComment(skillId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: skillKeys.commentsAll(skillId) })
      queryClient.invalidateQueries({ queryKey: skillKeys.detail(skillId) })
    },
  })
}

/**
 * `POST /comments/{id}/delete`,软删除。
 *
 * 失效范围与发表完全一致 —— 计数同样要跟着变。删除按钮在评论条目上,而 skillId 不在那条评论的
 * 响应体里,所以由调用方从页面上下文传进来。
 */
export function useDeleteSkillComment(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (commentId: number) => deleteSkillComment(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: skillKeys.commentsAll(skillId) })
      queryClient.invalidateQueries({ queryKey: skillKeys.detail(skillId) })
    },
  })
}

/**
 * `POST /skills/{id}/versions/{version}/certify`(ADMIN),认证或撤销某个版本。
 *
 * **失效范围是这个 hook 唯一容易写错的地方。** 认证挂在版本上,但它的影响面比"版本列表"大得多:
 * - `versions(skillId)` —— 那一行的徽章要变;
 * - `detail(skillId)` —— 详情页顶部的徽章取自 `latestVersion`,改的正好是最新版时它也要变;
 * - `lists()` —— 列表卡片带认证徽章,而且 `certifiedOnly` 筛选的结果集会变;
 * - `managedAll()` —— 管理表格里有 certification 列。
 *
 * 只失效 `versions` 是最自然也最错的写法:版本列表当场刷新了,页面顶部的徽章却还挂着旧状态,
 * 而这种不一致没有任何报错。
 */
export function useCertifyVersion(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ version, request }: { version: string; request: CertifyVersionRequest }) =>
      certifySkillVersion(skillId, version, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: skillKeys.versions(skillId) })
      queryClient.invalidateQueries({ queryKey: skillKeys.detail(skillId) })
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
      queryClient.invalidateQueries({ queryKey: skillKeys.managedAll() })
    },
  })
}

/** `POST /skills/{id}/relist`。失效范围同 {@link useUnlistSkill},两处缓存都要标。 */
export function useRelistSkill(skillId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => relistSkill(skillId),
    onSuccess: (skill: SkillDetail) => {
      queryClient.setQueryData(skillKeys.detail(skillId), skill)
      queryClient.invalidateQueries({ queryKey: skillKeys.lists() })
      queryClient.invalidateQueries({ queryKey: skillKeys.managedAll() })
    },
  })
}
