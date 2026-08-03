/// <reference types="vite/client" />

/**
 * 收紧 Vite 内建的 `ImportMetaEnv`。
 *
 * 默认的 `ImportMetaEnv` 用 `Record<string, any>` 兜底任意变量名,这会让
 * `import.meta.env.VITE_XXX` 的类型退化成 `any`,与项目"禁用 any"的硬约束冲突。
 * 这里显式列出本项目用到的环境变量,新增一个就补一行。
 */
interface ImportMetaEnv {
  /**
   * 后端 API 基址,默认 `/skillhub/api/v1`(见 frontend/CLAUDE.md 第五节)。
   * dev 下留空即可,走 vite.config.ts 里的 proxy;只有需要指向非默认地址时才配置。
   */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
