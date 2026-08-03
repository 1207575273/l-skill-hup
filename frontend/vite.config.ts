import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  // 必须与后端 server.servlet.context-path 对齐。
  // 改错的症状是"本地 dev 一切正常、打包部署后白屏",排查成本很高。
  base: '/skillhub/',

  // Tailwind 4 走 Vite 插件,没有 tailwind.config.ts,也不需要 PostCSS 配置。
  plugins: [react(), tailwindcss()],

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
