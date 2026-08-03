/**
 * 开发服的 base 路径归一化:决定一个请求要不要被重定向到 `base`。
 *
 * <p><b>为什么需要它。</b>`base` 是 `/skillhub/`(必须与后端 context-path 对齐),而 Vite 只认带尾斜杠的
 * 完整前缀。于是两种手输地址都会撞上那句提示:
 *
 * <ul>
 *   <li>`http://localhost:5173/` —— 根本没带 base
 *   <li>`http://localhost:5173/skillhub` —— <b>少一个尾斜杠</b>,不匹配 `/skillhub/` 前缀
 * </ul>
 *
 * 第二种最难受:地址看起来完全正确,手动刷新页面就会撞上,而"少一个斜杠"这件事从提示文字里看不出来
 * (它只是把正确地址原样复述了一遍)。
 *
 * <p><b>逻辑独立成纯函数而不是写在中间件里</b>,是为了能直接测:这段判断的风险不在"跳不跳",而在
 * <b>跳过了头</b> —— 前缀匹配会把 HMR 的 websocket 握手、`/@vite/*`、`/node_modules/*` 一起重定向掉,
 * 症状是热更新静默失效,而那种失效不会报错,只会让人以为"改了代码没反应"。所以这里只精确匹配两个路径,
 * 其余一律放行,并且有测试钉住。
 *
 * <p>prod 不需要同样处理:那边由 Servlet 容器按 context-path 自己补尾斜杠。
 */
export function resolveBaseRedirect(url: string | undefined, base: string): string | null {
  if (url === undefined || url === '') {
    return null
  }

  // 只按路径判断,查询串原样带走 —— 否则从带参数的链接进来会丢参数,而那种丢失极难联想到重定向。
  const queryStart = url.indexOf('?')
  const pathname = queryStart === -1 ? url : url.slice(0, queryStart)
  const query = queryStart === -1 ? '' : url.slice(queryStart)

  const baseWithoutTrailingSlash = base.endsWith('/') ? base.slice(0, -1) : base

  if (pathname === '/' || pathname === baseWithoutTrailingSlash) {
    return base + query
  }
  return null
}
