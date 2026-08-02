// 所有脚本共用的路径。脚本无论从哪个目录被调用,这里算出来的都是仓库内的绝对路径。

import { fileURLToPath } from 'node:url'

/** 本文件在 scripts/lib/,往上两级就是仓库根 */
export const repoRoot = fileURLToPath(new URL('../../', import.meta.url))

export const backendDir = fileURLToPath(new URL('../../backend/', import.meta.url))
export const frontendDir = fileURLToPath(new URL('../../frontend/', import.meta.url))
export const dockerDir = fileURLToPath(new URL('../../docker/', import.meta.url))

export const composeFile = fileURLToPath(new URL('../../docker/docker-compose.yml', import.meta.url))
export const dockerEnvFile = fileURLToPath(new URL('../../docker/.env', import.meta.url))
export const dockerEnvExample = fileURLToPath(
  new URL('../../docker/.env.example', import.meta.url),
)
