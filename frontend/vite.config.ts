import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'
import { resolveBaseRedirect } from './src/lib/devBaseRedirect'

/**
 * 把 `/` 与 `/skillhub`(缺尾斜杠)都归一到 `/skillhub/`。
 *
 * `base` 是 `/skillhub/`,而 Vite 只认带尾斜杠的完整前缀,所以这两种手输地址都会撞上
 * "The server is configured with a public base URL of /skillhub/ - did you mean to visit /skillhub/ instead?"。
 * 第一版只处理了 `/`,漏掉了缺尾斜杠这种 —— 而后者更常见:地址看起来完全正确,**手动刷新页面就会撞上**。
 *
 * 判断逻辑在 `src/lib/devBaseRedirect.ts`,有测试。放在那里而不是内联进这个中间件,是因为这段判断的
 * 风险不在"跳不跳"而在**跳过了头**:前缀匹配会把 HMR 握手与 `/@vite/*` 一起重定向掉,症状是热更新
 * 静默失效 —— 不报错,只是"改了代码没反应"。
 */
function redirectToBase(base: string): Plugin {
  return {
    name: 'skillhub:redirect-to-base',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const target = resolveBaseRedirect(req.url, base)
        if (target !== null) {
          res.writeHead(302, { Location: target })
          res.end()
          return
        }
        next()
      })
    },
  }
}

export default defineConfig({
  // 必须与后端 server.servlet.context-path 对齐。
  // 改错的症状是"本地 dev 一切正常、打包部署后白屏",排查成本很高。
  base: '/skillhub/',

  // Tailwind 4 走 Vite 插件,没有 tailwind.config.ts,也不需要 PostCSS 配置。
  plugins: [react(), tailwindcss(), redirectToBase('/skillhub/')],

  server: {
    port: 5173,
    proxy: {
      '/skillhub/api': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
      // 安装脚本端点刻意不挂在 /api/v1 下(业务建模文档 6.2:GET /i/{token}.ps1|.sh),
      // 所以上面那条代理规则盖不住它。少了这条,dev 下点"复制安装命令"拿到的链接会 404,
      // 而症状看起来像后端没起来。
      '/skillhub/i': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
    },
  },

  build: {
    outDir: 'dist',
    // 产物由 Dockerfile 拷进后端 src/main/resources/static/ 同源托管
    emptyOutDir: true,
  },
})
