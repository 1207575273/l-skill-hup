import { useMutation } from '@tanstack/react-query'
import { createInstallBundle } from '../api/install'
import type { ClientCode, InstallBundleItem, ScopeCode } from '../api/types'

export interface CreateInstallBundleInput {
  readonly items: InstallBundleItem[]
  readonly client: ClientCode
  readonly scope: ScopeCode
}

/**
 * `POST /bundles`,生成安装单。产出的是一个一次性 token + 可复制命令,**不对应任何被查询的资源**——
 * 没有 `GET /bundles/{id}` 这种读接口给它失效,成功后不需要 `invalidateQueries`。
 */
export function useCreateInstallBundle() {
  return useMutation({
    mutationFn: ({ items, client, scope }: CreateInstallBundleInput) =>
      createInstallBundle(items, client, scope),
  })
}
