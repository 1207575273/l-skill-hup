#!/usr/bin/env node
//
// 全量自查入口:依次跑后端与前端的质量闸门,最后汇总。
//
//   node scripts/check.mjs             后端 + 前端
//   node scripts/check.mjs --backend   只查后端
//   node scripts/check.mjs --frontend  只查前端
//
// 全绿才算任务完成,不许拿"只是小改动"绕过。
// 退出码:全过 0,有一项失败 1 —— 可以直接挂到 CI 或 git hook 上。

import { ensureDocker } from './lib/docker.mjs'
import { colors, humanizeDuration, log } from './lib/log.mjs'
import { backendDir, frontendDir } from './lib/paths.mjs'
import { run } from './lib/proc.mjs'

const flags = new Set(process.argv.slice(2))
const onlyBackend = flags.has('--backend')
const onlyFrontend = flags.has('--frontend')

const steps = []

if (!onlyFrontend) {
  steps.push({
    name: '后端 mvn verify',
    detail: 'Spotless + 编译 + JUnit + ArchUnit + JaCoCo(>=80%)',
    cwd: backendDir,
    command: 'mvn',
    args: ['-B', '-ntp', 'verify'],
    // 单测用 Testcontainers 起真 PostgreSQL,docker 不通就跑不了
    needsDocker: true,
  })
}

if (!onlyBackend) {
  steps.push({
    name: '前端 pnpm check',
    detail: 'Biome lint/format + tsc --noEmit + Vitest',
    cwd: frontendDir,
    command: 'pnpm',
    args: ['check'],
    needsDocker: false,
  })
}

process.exit(await main())

async function main() {
  if (steps.length === 0) {
    log.fail('--backend 与 --frontend 不能同时给')
    return 1
  }

  if (steps.some((step) => step.needsDocker)) {
    log.section('环境检查')
    if (!(await ensureDocker())) {
      log.fail('docker 不可达,后端单测用 Testcontainers 起真 PostgreSQL,跑不了')
      return 1
    }
  }

  const results = []
  for (const step of steps) {
    log.section(step.name)
    log.plain(colors.gray(`    ${step.detail}`))
    log.plain()

    const startedAt = Date.now()
    const code = await run(step.command, step.args, { cwd: step.cwd })
    results.push({ name: step.name, code, elapsed: Date.now() - startedAt })
  }

  return report(results)
}

function report(results) {
  log.section('汇总')

  const width = Math.max(...results.map((item) => item.name.length))
  for (const { name, code, elapsed } of results) {
    const line = `${name.padEnd(width)}  ${humanizeDuration(elapsed).padStart(7)}`
    if (code === 0) log.pass(line)
    else log.fail(`${line}  exit=${code}`)
  }

  const failed = results.filter((item) => item.code !== 0)
  log.plain()
  if (failed.length === 0) {
    log.pass(`全绿,${results.length} 项全部通过`)
    return 0
  }

  log.fail(`${failed.length}/${results.length} 项失败:${failed.map((f) => f.name).join('、')}`)
  return 1
}
