import type { ApiErrorBody } from './types'

/**
 * API 请求层的通用封装。**页面和组件不许直接 `fetch`**,一律走这里(frontend/CLAUDE.md 第五节)。
 *
 * 硬约束(单一事实源:docs/20260802224934_业务建模与接口契约.md 6.1):
 * - 只暴露 `apiGet` / `apiPost` 两个入口,对齐项目"只用 GET/POST"的硬约束,
 *   写操作的资源函数里自己拼 `/xxx/update`、`/xxx/delete` 路径。
 * - 基址固定 `/skillhub/api/v1`,可用 `VITE_API_BASE_URL` 覆盖,但**不硬编码后端地址**。
 * - 非 2xx 响应一律解析成 `ApiError` 抛出,`traceId` 必须能带出来。
 * - 认证走 `Authorization: Bearer <token>`,token 由 `setAuthToken` 注入,不出现在 URL 里。
 */

const DEFAULT_API_BASE_URL = '/skillhub/api/v1'

function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL
  return configured && configured.trim() !== '' ? configured : DEFAULT_API_BASE_URL
}

const API_BASE_URL = resolveApiBaseUrl()

// ---------------------------------------------------------------------------
// 认证 token:P0 的登录页尚未实现,这里先给出存取入口。
// 真正的登录流程(hooks/useAuth.ts 之类)在 /auth/login 成功后调用 setAuthToken,
// 登出或 401 时调用 setAuthToken(null)。
// ---------------------------------------------------------------------------

const AUTH_TOKEN_STORAGE_KEY = 'skillhub.authToken'

function readStoredToken(): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  return window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)
}

let authToken: string | null = readStoredToken()

export function getAuthToken(): string | null {
  return authToken
}

export function setAuthToken(token: string | null): void {
  authToken = token
  if (typeof window === 'undefined') {
    return
  }
  if (token) {
    window.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token)
  } else {
    window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
  }
}

// ---------------------------------------------------------------------------
// 错误模型
// ---------------------------------------------------------------------------

/**
 * 统一的 API 错误类型。`code` 供前端分支判断,`message` 面向人不做逻辑判断,
 * `traceId` 是排障第一入口 —— 报错提示里必须能把它展示或复制出来,吞掉它等于
 * 让用户没法说清是哪一次请求出的问题。
 */
export class ApiError extends Error {
  readonly code: string
  readonly traceId: string
  readonly details?: unknown

  constructor(params: { code: string; message: string; traceId: string; details?: unknown }) {
    super(params.message)
    this.name = 'ApiError'
    this.code = params.code
    this.traceId = params.traceId
    this.details = params.details
  }
}

/** 响应体不符合契约(网关错误页、502 等)时的兜底错误码,前端可据此提示"重试或联系管理员"。 */
export const UNKNOWN_ERROR_CODE = 'UNKNOWN_ERROR'

/** 请求根本没到服务端(断网、DNS 失败、CORS 被拒)时的错误码,与"服务端返回了错误"是两回事。 */
export const NETWORK_ERROR_CODE = 'NETWORK_ERROR'

/**
 * 没有 traceId 时的占位。服务端的错误响应体里 `traceId` 必填,所以走到这个占位
 * 只有两种情况:响应体不符合契约,或请求压根没到服务端。**不伪造一个看似合法的
 * traceId**,否则用户会拿着它去查一个不存在的请求。
 */
export const NO_TRACE_ID = 'N/A'

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const record = value as Record<string, unknown>
  return (
    typeof record.code === 'string' &&
    typeof record.message === 'string' &&
    typeof record.traceId === 'string'
  )
}

function toApiError(payload: unknown, response: Response): ApiError {
  if (isApiErrorBody(payload)) {
    return new ApiError({
      code: payload.code,
      message: payload.message,
      traceId: payload.traceId,
      details: payload.details,
    })
  }

  // 走到这里说明响应体不符合契约(traceId 本应必填),多半是网关的错误页而不是应用返回的。
  return new ApiError({
    code: UNKNOWN_ERROR_CODE,
    message: `请求失败(HTTP ${response.status})`,
    traceId: NO_TRACE_ID,
  })
}

/**
 * 把 `fetch` 自身的失败也收敛成 `ApiError`。
 *
 * 断网、DNS 失败、CORS 被拒时 `fetch` 抛的是裸 `TypeError`,不收敛的话每个调用方都要
 * 同时处理两种错误形状,错误提示迟早不一致。**唯一不收敛的是取消**:`AbortError` 是
 * 调用方主动取消的正常控制流,TanStack Query 靠它识别请求作废,包装了会被当成真错误。
 */
function toNetworkError(cause: unknown): never {
  // 不能写 `cause instanceof Error` —— 取消时抛的 DOMException 在 WebIDL 里是独立接口,
  // **不继承 Error**,浏览器与 jsdom 都是如此。按 instanceof 判会把取消误当成网络故障。
  if (isNamed(cause, 'AbortError')) {
    throw cause
  }
  throw new ApiError({
    code: NETWORK_ERROR_CODE,
    message: '网络请求失败,请检查网络连接后重试',
    traceId: NO_TRACE_ID,
    details: readMessage(cause),
  })
}

function isNamed(value: unknown, name: string): boolean {
  return typeof value === 'object' && value !== null && (value as { name?: unknown }).name === name
}

function readMessage(value: unknown): string | undefined {
  if (typeof value !== 'object' || value === null) {
    return undefined
  }
  const message = (value as { message?: unknown }).message
  return typeof message === 'string' ? message : undefined
}

// ---------------------------------------------------------------------------
// 请求核心
// ---------------------------------------------------------------------------

export type QueryParamValue = string | number | boolean | undefined

export interface RequestOptions {
  readonly query?: Record<string, QueryParamValue>
  readonly signal?: AbortSignal
}

function buildUrl(path: string, query?: Record<string, QueryParamValue>): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost'
  const url = new URL(`${API_BASE_URL}${path}`, origin)
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) {
        url.searchParams.set(key, String(value))
      }
    }
  }
  return url.toString()
}

async function request<T>(
  method: 'GET' | 'POST',
  path: string,
  body: unknown,
  options: RequestOptions | undefined,
): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getAuthToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(buildUrl(path, options?.query), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: options?.signal,
  }).catch(toNetworkError)

  if (response.status === 204) {
    return undefined as T
  }

  const payload: unknown = await response.json().catch(() => null)

  if (!response.ok) {
    throw toApiError(payload, response)
  }

  return payload as T
}

/** GET 请求。查询参数走 `options.query`,不许拼在 path 字符串里。 */
export function apiGet<T>(path: string, options?: RequestOptions): Promise<T> {
  return request<T>('GET', path, undefined, options)
}

/** POST 请求。项目宪法只用 GET/POST,更新用 `/xxx/{id}/update`,删除用 `/xxx/{id}/delete`。 */
export function apiPost<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return request<T>('POST', path, body, options)
}
