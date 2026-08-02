#!/usr/bin/env node
//
// 本地开发入口:探测环境 -> 起基座 -> 并行跑后端与 Vite。
//
//   node scripts/dev.mjs                 全都起
//   node scripts/dev.mjs --backend-only  只起后端
//   node scripts/dev.mjs --frontend-only 只起 Vite
//   node scripts/dev.mjs --no-docker     跳过 docker/postgres 检查(数据库在别处时用)
//
// 零第三方依赖,node 直接跑。

import { ensureDocker, ensurePostgres, whoPublishesPort } from './lib/docker.mjs'
import { colors, log } from './lib/log.mjs'
import { backendDir, frontendDir } from './lib/paths.mjs'
import { isPortTaken, killTree, label, spawnLabeled, whoHoldsPort } from './lib/proc.mjs'

const BACKEND_PORT = Number(process.env.SERVER_PORT ?? 18080)
const VITE_PORT = 5173

const flags = new Set(process.argv.slice(2))
const withBackend = !flags.has('--frontend-only')
const withFrontend = !flags.has('--backend-only')
const withDocker = !flags.has('--no-docker')

await main()

async function main() {
  log.section('环境检查')

  if (withDocker) {
    if (!(await ensureDocker())) process.exit(1)
    if (!(await ensurePostgres())) process.exit(1)
  } else {
    log.skip('按 --no-docker 跳过 docker 与 postgres 检查')
  }

  if (!(await checkPorts())) process.exit(1)

  log.section('启动')
  const children = []

  if (withBackend) {
    log.info(`后端  -> http://localhost:${BACKEND_PORT}/skillhub`)
    // profiles.default 就是 dev,不用显式传 -Pdev
    children.push(
      spawnLabeled('backend', label.backend, 'mvn', ['-B', '-ntp', 'spring-boot:run'], {
        cwd: backendDir,
      }),
    )
  }

  if (withFrontend) {
    log.info(`前端  -> http://localhost:${VITE_PORT}/skillhub/`)
    children.push(spawnLabeled('frontend', label.frontend, 'pnpm', ['dev'], { cwd: frontendDir }))
  }

  if (children.length === 0) {
    log.fail('--backend-only 与 --frontend-only 不能同时给')
    process.exit(1)
  }

  log.plain(colors.gray('    Ctrl+C 结束,会连同 java / node 子进程一起收掉'))
  log.plain()

  wireShutdown(children)
}

async function checkPorts() {
  const wanted = []
  if (withBackend) wanted.push({ port: BACKEND_PORT, who: '后端' })
  if (withFrontend) wanted.push({ port: VITE_PORT, who: 'Vite dev server' })

  let ok = true
  for (const { port, who } of wanted) {
    if (!(await isPortTaken(port))) continue

    ok = false

    // 先问 docker:WSL2 mirrored 模式下容器发布的端口在宿主 netstat 里查不到,
    // 而"上次的 skillhub-app 还在跑"恰恰是本项目最常见的原因。
    const container = await whoPublishesPort(port)
    if (container !== null) {
      log.fail(`端口 ${port} 被容器 ${container.name} 占着(${container.image}),${who}起不来`)
      log.info(`要收掉它:docker compose -f docker/docker-compose.yml stop app`)
      continue
    }

    const holder = await whoHoldsPort(port)
    if (holder !== null) {
      log.fail(`端口 ${port} 被 ${holder.name} (PID ${holder.pid}) 占着,${who}起不来`)
      log.info(`要收掉它:taskkill /PID ${holder.pid} /T /F`)
      continue
    }

    log.fail(`端口 ${port} 已被占用,${who}起不来(查不到持有者,可能在 WSL 或别的命名空间里)`)
  }
  if (ok) log.pass('端口空闲')
  return ok
}

/**
 * Ctrl+C 时收掉所有子进程;任一子进程先退出也把其余的收掉。
 *
 * 不做后半条的话,后端崩了之后 Vite 还在跑,页面照样打得开、请求全 500,
 * 看起来像"接口坏了"而不是"后端已经没了",很浪费排查时间。
 */
function wireShutdown(children) {
  let shuttingDown = false

  const shutdown = (reason, exitCode) => {
    if (shuttingDown) return
    shuttingDown = true
    log.plain()
    log.info(`收尾:${reason}`)
    for (const child of children) killTree(child)
    // 给 taskkill 一点时间,否则进程还没死脚本就退了
    setTimeout(() => process.exit(exitCode), 800)
  }

  process.on('SIGINT', () => shutdown('收到 Ctrl+C', 0))
  process.on('SIGTERM', () => shutdown('收到 SIGTERM', 0))

  for (const child of children) {
    child.on('close', (code) => {
      if (shuttingDown) return
      shutdown(`有子进程退出(exit ${code}),连带停掉其余进程`, code ?? 1)
    })
  }
}
