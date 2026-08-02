// Docker / WSL 探测与唤醒。
//
// 【为什么脚本必须自带这一段】
// 本机 dockerd 跑在 WSL 里,而 WSL 会周期性自己关闭 —— 这是 WSL 的已知缺陷
// (microsoft/WSL#40363:Hyper-V VmSwitch 事件 285 与生命周期管理交互失败),
// 微软没有根本修复,vmIdleTimeout=-1 和常驻 sleep infinity 都实测挡不住。
// 本机决策是不装看门狗(每分钟闪黑窗太烦),所以**项目脚本是唯一的防线**。
//
// 断连时的报错是 "connectex: 目标计算机积极拒绝",看起来像 docker 配置坏了,
// 极具误导性。不能让使用者自己去排查,脚本必须主动探测、主动唤醒、主动说清楚。
//
// 详见根 CLAUDE.md 10.1 与 docs/20260802193801_Windows下WSL2-Docker开发环境实践.md。

import { copyFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'

import { log } from './log.mjs'
import { capture, run, sleep } from './proc.mjs'
import { composeFile, dockerEnvExample, dockerEnvFile } from './paths.mjs'

/** dockerd 原生监听的地址。docker context `wsl` 指向的就是它。 */
const DOCKER_PING_URL = process.env.DOCKER_PING_URL ?? 'http://localhost:12375/_ping'
const WSL_DISTRO = process.env.WSL_DISTRO ?? 'Ubuntu-20.04'
const POSTGRES_CONTAINER = 'skillhub-postgres'

async function pingDocker(timeoutMs = 3000) {
  try {
    const response = await fetch(DOCKER_PING_URL, { signal: AbortSignal.timeout(timeoutMs) })
    return response.ok
  } catch {
    return false
  }
}

/**
 * 确保 docker 可达,不可达就唤醒 WSL 并等它起来。
 *
 * @returns {Promise<boolean>} 最终是否可达
 */
export async function ensureDocker() {
  if (await pingDocker()) {
    log.pass('docker 可达')
    return true
  }

  log.warn('docker 不可达,正在唤醒 WSL')
  log.info('这是 WSL 的已知缺陷(microsoft/WSL#40363),不是你的配置坏了,不用去改 daemon.json')

  const code = await run('wsl.exe', ['-d', WSL_DISTRO, '--', '/bin/true'], { quiet: true })
  if (code === 127) {
    log.fail('找不到 wsl.exe。确认 WSL 已安装,或用 DOCKER_PING_URL 指向别的 docker daemon')
    return false
  }

  for (let attempt = 1; attempt <= 20; attempt += 1) {
    await sleep(1500)
    if (await pingDocker()) {
      log.pass(`docker 已恢复(等待约 ${((attempt * 1500) / 1000).toFixed(1)}s)`)
      return true
    }
  }

  log.fail(`唤醒后 30s 内仍连不上 ${DOCKER_PING_URL}`)
  log.info(`手动排查:wsl -d ${WSL_DISTRO} -- systemctl status docker`)
  return false
}

/**
 * 哪个容器把这个宿主端口发布出去了。没有就返回 null。
 *
 * 为什么需要它:WSL2 是 mirrored 网络模式,容器发布的端口在 WSL 里监听、
 * 镜像到 Windows,**宿主的 netstat 看不到 LISTENING 条目也查不到 PID**。
 * 光报"端口被占"使用者会一头雾水 —— 而本项目里最常见的原因恰恰就是
 * 上一次 `up -d --build` 起的 skillhub-app 还在跑。
 */
export async function whoPublishesPort(port) {
  const { code, stdout } = await capture('docker', ['ps', '--format', 'json'])
  if (code !== 0) return null

  for (const line of stdout.split(/\r?\n/)) {
    if (line.trim().length === 0) continue
    try {
      const container = JSON.parse(line)
      // Ports 形如 "0.0.0.0:18080->18080/tcp, [::]:18080->18080/tcp"
      if (typeof container.Ports === 'string' && container.Ports.includes(`:${port}->`)) {
        return { name: container.Names, image: container.Image }
      }
    } catch {
      // 单行解析失败不影响其他行,继续
    }
  }
  return null
}

/** docker/.env 不存在就从模板复制一份,省掉一次"为什么起不来"的来回 */
async function ensureDockerEnvFile() {
  if (existsSync(dockerEnvFile)) return
  if (!existsSync(dockerEnvExample)) return

  await copyFile(dockerEnvExample, dockerEnvFile)
  log.info('docker/.env 不存在,已从 .env.example 复制一份(dev 默认值可直接用)')
}

/**
 * 容器的健康状态;没定义 healthcheck 就退回运行状态。容器不存在返回 null。
 *
 * 刻意不用 `docker inspect -f '{{...}}'`:Go 模板里带空格,而 Windows 上
 * 走 shell 传参极易被引号规则坑到。直接解析 JSON 没有这个问题,也更好读。
 */
async function containerHealth(name) {
  const { code, stdout } = await capture('docker', ['inspect', name])
  if (code !== 0) return null

  try {
    const state = JSON.parse(stdout)?.[0]?.State
    if (state === undefined) return null
    return state.Health?.Status ?? state.Status ?? null
  } catch {
    return null
  }
}

/**
 * 确保基座 postgres 在跑且健康。
 *
 * 不健康就直接起,已经健康就跳过 —— dev 反复跑脚本时不该每次都重启数据库。
 *
 * @returns {Promise<boolean>}
 */
export async function ensurePostgres() {
  const current = await containerHealth(POSTGRES_CONTAINER)
  if (current === 'healthy') {
    log.pass(`postgres 已在运行且健康(${POSTGRES_CONTAINER})`)
    return true
  }

  await ensureDockerEnvFile()
  log.info(current === null ? '启动 postgres 基座' : `postgres 当前状态 ${current},重新拉起`)

  const code = await run('docker', ['compose', '-f', composeFile, 'up', '-d', 'postgres'])
  if (code !== 0) {
    log.fail(`docker compose up postgres 失败(exit ${code})`)
    return false
  }

  // 健康检查是 pg_isready,冷启动初始化要十几秒。这里必须等,
  // 不等的话后端会在 PG 还没 ready 时启动,Flyway 直接连接失败退出。
  for (let attempt = 1; attempt <= 30; attempt += 1) {
    const status = await containerHealth(POSTGRES_CONTAINER)
    if (status === 'healthy') {
      log.pass(`postgres 健康(等待约 ${attempt * 2}s),宿主端口 15432`)
      return true
    }
    if (status === 'exited' || status === 'dead') {
      log.fail(`postgres 容器状态 ${status},看日志:docker logs ${POSTGRES_CONTAINER}`)
      return false
    }
    await sleep(2000)
  }

  log.fail('postgres 60s 内未变成 healthy')
  log.info(`看日志:docker logs ${POSTGRES_CONTAINER}`)
  return false
}
