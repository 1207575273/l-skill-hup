import { useMutation, useQueryClient } from '@tanstack/react-query'
import { skillKeys, tagKeys } from '../api/queryKeys'
import { createTag, deleteTag, updateTag } from '../api/tags'
import type { CreateTagRequest, DeleteTagRequest, UpdateTagRequest } from '../api/types'

/**
 * 标签治理的写侧钩子(ADMIN)。
 *
 * **三个动作全都要连 `skillKeys.all` 一起失效。** 标签不只出现在标签列表里:skill 卡片与详情页都嵌着
 * 标签行,筛选面板按 tagId 过滤。改个名却只失效 `tagKeys`,列表页的卡片上会一直挂着旧名字,而这种
 * 不一致没有任何报错——它只是看起来像"缓存没刷新",没人会想到是失效范围写窄了。
 */

/** `POST /tags`,新建 CURATED 词条。 */
export function useCreateTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateTagRequest) => createTag(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
    },
  })
}

/**
 * `POST /tags/{id}/update`。
 *
 * 除标签自身外还要失效 skill 域:改名 / 转 kind 都会改变 skill 卡片与详情里那一行标签的显示。
 */
export function useUpdateTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdateTagRequest }) =>
      updateTag(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
      queryClient.invalidateQueries({ queryKey: skillKeys.all })
    },
  })
}

/**
 * `POST /tags/{id}/delete`。
 *
 * 删除会真的改动 `skill_tag`(断开引用或整体迁移),skill 侧缓存里的标签行必然过期,必须一并失效。
 */
export function useDeleteTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request?: DeleteTagRequest }) =>
      deleteTag(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
      queryClient.invalidateQueries({ queryKey: skillKeys.all })
    },
  })
}
