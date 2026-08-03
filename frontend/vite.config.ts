import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

/**
 * 访问根路径时跳到 `/skillhub/`。
 *
 * 因为 `base` 是 `/skillhub/`,直接开 `http://localhost:5173/` 只会看到 Vite 那句
 * "The server is configured with a public base URL of /skillhub/ - did you mean to visit /skillhub/ instead?"
 * —— 它是提示不是错误,但每个人第一次打开都会撞上,而且得手动补路径。
 *
 * 只跳精确的 `/`,不碰 `/skillhub/*` 与 `/@vite/*`、`/node_modules/*` 这些 Vite 自己的内部请求 ——
 * 前缀匹配会把 HMR 的 websocket 握手也一起重定向掉,症状是热更新静默失效。
 */
function redirectRootToBase(base: string): Plugin {
  return {
    name: 'skillhub:redirect-root-to-base',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url === '/') {
          res.writeHead(302, { Location: base })
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
  plugins: [react(), tailwindcss(), redirectRootToBase('/skillhub/')],

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
