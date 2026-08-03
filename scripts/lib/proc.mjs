// 子进程封装。零第三方依赖,只用 node 内置模块。

import { spawn } from 'node:child_process'
import { connect } from 'node:net'

import { colors } from './log.mjs'

/**
 * Windows 上 mvn / pnpm / docker 都是 .cmd 或 .exe 包装,不走 shell 起不来。
 *
 * 这里的所有命令与参数都是脚本里写死的字面量,没有任何外部输入拼进去,
 * 所以不存在命令注入面。将来若要把用户输入传进来,必须先改掉这个开关。
 */
const useShell = process.platform === 'win32'

/**
 * shell:true 时 Node **只是把 args 用空格拼起来,不做任何转义**。
 * 含空格的参数(docker inspect 的 Go 模板、带空格的路径)会被 cmd.exe 劈成多个,
 * 症状是命令莫名其妙地失败或行为不对,而且完全看不出是引号问题。
 */
const quoteForShell = (arg) => {
  if (!useShell) return arg
  return /[\s"^&|<>()]/.test(arg) ? `"${arg.replaceAll('"', '\\"')}"` : arg
}

const prepare = (args) => args.map(quoteForShell)

/**
 * 让子进程里的 JVM 用 UTF-8 写 stdout / stderr。
 *
 * Windows 上 `stdout.encoding` 默认取 ANSI 代码页(中文机器是 GBK),而 `file.encoding` 从 JDK 18
 * 起恒为 UTF-8 —— 这两个不一致正是乱码的来源:应用的 logback 按 UTF-8 编码写出,Maven 把它解码后
 * 又按 GBK 写到自己的 stdout,于是我们这边按 UTF-8 读就成了乱码。
 *
 * **这不是可选的加固,是 capture / spawnLabeled 的前提**:那两个函数都调 `setEncoding('utf8')`,
 * 拿 GBK 字节按 UTF-8 解一定出乱码。谁解码谁负责,所以在这里注入而不是让每个调用方记得传。
 *
 * 用追加而不是覆盖:`MAVEN_OPTS` 可能已经被人设了别的东西(代理、内存),整个替掉会悄悄失效。
 */
const withUtf8JvmOutput = (env) => {
  const existing = process.env.MAVEN_OPTS ?? ''
  return {
    ...env,
    MAVEN_OPTS: `${existing} -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`.trim(),
  }
}

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * 跑一条命令并等它结束,输出直接透传到当前终端。
 *
 * @returns {Promise<number>} 退出码
 */
export function run(command, args, { cwd, env, quiet = false } = {}) {
  return new Promise((resolve) => {
    // stdio 是 inherit,字节不经我们的手、直接落到终端,所以这里要跟随**终端**而不是一律 UTF-8。
    // 与后端 ConsoleEncodingEnvironmentPostProcessor 是同一条判据:接着真终端(cmd / PowerShell)
    // 就让 JVM 按平台选,那时终端本身是 GBK,强推 UTF-8 反而制造乱码;没接终端
    // (Git Bash 的 mintty 走管道、重定向到文件、CI)则一律 UTF-8。
    const encodingEnv = process.stdout.isTTY ? env : withUtf8JvmOutput(env)
    const child = spawn(command, prepare(args), {
      cwd,
      env: { ...process.env, ...encodingEnv },
      stdio: quiet ? 'ignore' : 'inherit',
      shell: useShell,
    })
    child.on('error', () => resolve(127))
    child.on('close', (code) => resolve(code ?? 1))
  })
}

/**
 * 跑一条命令并收集输出,不打到终端。用于探测类命令(docker inspect、netstat)。
 *
 * @returns {Promise<{code: number, stdout: string, stderr: string}>}
 */
export function capture(command, args, { cwd, env } = {}) {
  return new Promise((resolve) => {
    const child = spawn(command, prepare(args), {
      cwd,
      env: { ...process.env, ...withUtf8JvmOutput(env) },
      stdio: ['ignore', 'pipe', 'pipe'],
      shell: useShell,
    })
    let stdout = ''
    let stderr = ''
    child.stdout.setEncoding('utf8')
    child.stderr.setEncoding('utf8')
    child.stdout.on('data', (chunk) => {
      stdout += chunk
    })
    child.stderr.on('data', (chunk) => {
      stderr += chunk
    })
    child.on('error', (error) => resolve({ code: 127, stdout, stderr: String(error) }))
    child.on('close', (code) => resolve({ code: code ?? 1, stdout, stderr }))
  })
}

/**
 * 起一个长跑进程,每行输出前面加上来源标签。
 *
 * 后端和 Vite 并行跑时,不加标签的话两边日志会糊成一团分不清谁是谁。
 */
export function spawnLabeled(label, color, command, args, { cwd, env } = {}) {
  const child = spawn(command, prepare(args), {
    cwd,
    env: { ...process.env, ...withUtf8JvmOutput(env) },
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: useShell,
  })
  prefixStream(child.stdout, label, color)
  prefixStream(child.stderr, label, color)
  return child
}

function prefixStream(stream, label, color) {
  // 按行缓冲:chunk 边界不一定落在换行处,直接打会把一行劈成两半
  let pending = ''
  stream.setEncoding('utf8')
  stream.on('data', (chunk) => {
    pending += chunk
    const lines = pending.split(/\r?\n/)
    pending = lines.pop() ?? ''
    for (const line of lines) {
      console.log(`${color(`[${label}]`)} ${line}`)
    }
  })
  stream.on('end', () => {
    if (pending.length > 0) {
      console.log(`${color(`[${label}]`)} ${pending}`)
    }
  })
}

/**
 * 杀掉整棵进程树。
 *
 * Windows 上 shell:true 起出来的直接子进程是 cmd.exe,`child.kill()` 只会杀掉
 * 那个 cmd,底下真正干活的 java / node 会变成孤儿继续占着端口 ——
 * 下次跑 dev 就会报端口被占,而且看不出是上一次没退干净。必须用 taskkill /T 杀整棵树。
 */
export function killTree(child) {
  if (child.pid === undefined || child.exitCode !== null || child.killed) return

  if (process.platform === 'win32') {
    spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
    return
  }
  child.kill('SIGTERM')
}

/** 端口有没有被占。连得上就是被占。 */
export function isPortTaken(port, host = '127.0.0.1', timeoutMs = 800) {
  return new Promise((resolve) => {
    const socket = connect({ port, host })
    const finish = (taken) => {
      socket.destroy()
      resolve(taken)
    }
    socket.setTimeout(timeoutMs)
    socket.once('connect', () => finish(true))
    socket.once('timeout', () => finish(false))
    socket.once('error', () => finish(false))
  })
}

/**
 * 占着这个端口的是谁。只有 Windows 能查到,查不到返回 null。
 *
 * 这一步是为了少一次来回:光说"端口被占"使用者还得自己去 netstat,
 * 直接把进程名和 PID 报出来,他能立刻决定是关掉还是换端口。
 */
export async function whoHoldsPort(port) {
  if (process.platform !== 'win32') return null

  const { stdout } = await capture('netstat', ['-ano', '-p', 'TCP'])
  const line = stdout
    .split(/\r?\n/)
    .find((row) => row.includes(`:${port} `) && row.includes('LISTENING'))
  if (line === undefined) return null

  const pid = line.trim().split(/\s+/).pop()
  if (pid === undefined) return null

  const { stdout: taskOutput } = await capture('tasklist', [
    '/fi',
    `PID eq ${pid}`,
    '/nh',
    '/fo',
    'csv',
  ])
  const name = taskOutput.split(',')[0]?.replaceAll('"', '').trim()
  return { pid, name: name && name.length > 0 ? name : '未知进程' }
}

export const label = {
  backend: (text) => colors.magenta(text),
  frontend: (text) => colors.cyan(text),
}
