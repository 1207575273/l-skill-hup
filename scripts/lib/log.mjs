// 统一输出格式。项目规范禁用 emoji 与装饰字符:
// 状态一律 [PASS]/[FAIL]/[WARN]/[SKIP]/[INFO],箭头 ->,分隔线用 = 或 - 重复。

const useColor = process.stdout.isTTY && !process.env.NO_COLOR

const paint = (code, text) => (useColor ? `\u001b[${code}m${text}\u001b[0m` : text)

export const colors = {
  gray: (text) => paint(90, text),
  red: (text) => paint(31, text),
  green: (text) => paint(32, text),
  yellow: (text) => paint(33, text),
  cyan: (text) => paint(36, text),
  magenta: (text) => paint(35, text),
  bold: (text) => paint(1, text),
}

const tagged = (label, color, message) => {
  console.log(`${color(`[${label}]`)} ${message}`)
}

export const log = {
  info: (message) => tagged('INFO', colors.cyan, message),
  pass: (message) => tagged('PASS', colors.green, message),
  fail: (message) => tagged('FAIL', colors.red, message),
  warn: (message) => tagged('WARN', colors.yellow, message),
  skip: (message) => tagged('SKIP', colors.gray, message),
  plain: (message = '') => console.log(message),

  /** 分隔标题,拿来区分脚本的几个阶段 */
  section(title) {
    console.log()
    console.log(colors.bold(`=== ${title} ${'='.repeat(Math.max(0, 60 - title.length))}`))
  },
}

/** 毫秒转成人读的耗时,汇总表里用 */
export const humanizeDuration = (ms) => (ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`)
