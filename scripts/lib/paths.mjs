// 所有脚本共用的路径。脚本无论从哪个目录被调用,这里算出来的都是仓库内的绝对路径。

import { fileURLToPath } from 'node:url'

// 本文件在 scripts/lib/,所以往上两级是仓库根。
// 只导出真正被用到的路径,用不上的不预留 —— 预留的常量没人维护,迟早指向不存在的目录。

export const backendDir = fileURLToPath(new URL('../../backend/', import.meta.url))
export const frontendDir = fileURLToPath(new URL('../../frontend/', import.meta.url))

export const composeFile = fileURLToPath(new URL('../../docker/docker-compose.yml', import.meta.url))
export const dockerEnvFile = fileURLToPath(new URL('../../docker/.env', import.meta.url))
export const dockerEnvExample = fileURLToPath(
  new URL('../../docker/.env.example', import.meta.url),
)
