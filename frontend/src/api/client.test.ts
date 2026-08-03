import type { Mock } from 'vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  API_BASE_URL,
  ApiError,
  apiGet,
  apiPost,
  apiPostFormData,
  getAuthToken,
  NETWORK_ERROR_CODE,
  NO_TRACE_ID,
  setAuthToken,
  UNKNOWN_ERROR_CODE,
} from './client'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiGet / apiPost', () => {
  let fetchMock: Mock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    setAuthToken(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('should_return_parsed_body_when_response_ok', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { items: [], total: 0, page: 1, size: 20 }))

    const result = await apiGet<{ items: unknown[]; total: number; page: number; size: number }>(
      '/skills',
    )

    expect(result).toEqual({ items: [], total: 0, page: 1, size: 20 })
  })

  it('should_throw_ApiError_with_traceId_when_error_body_matches_contract', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, {
        code: 'SKILL_NAME_DUPLICATED',
        message: '已存在同名 skill',
        traceId: '8f3c1e2a9b4d',
        details: { skillId: 42 },
      }),
    )

    const rejection = apiPost('/skills', {})

    await expect(rejection).rejects.toBeInstanceOf(ApiError)
    await expect(rejection).rejects.toMatchObject({
      code: 'SKILL_NAME_DUPLICATED',
      message: '已存在同名 skill',
      traceId: '8f3c1e2a9b4d',
      details: { skillId: 42 },
    })
  })

  it('should_fallback_to_unknown_error_when_error_body_does_not_match_contract', async () => {
    fetchMock.mockResolvedValue(new Response('<html>502 Bad Gateway</html>', { status: 502 }))

    const rejection = apiGet('/skills')

    await expect(rejection).rejects.toBeInstanceOf(ApiError)
    await expect(rejection).rejects.toMatchObject({
      code: UNKNOWN_ERROR_CODE,
      traceId: NO_TRACE_ID,
    })
  })

  it('should_attach_authorization_header_when_token_set', async () => {
    setAuthToken('abc123')
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await apiGet('/auth/me')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer abc123')
  })

  it('should_omit_authorization_header_when_token_not_set', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await apiGet('/skills')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined()
  })

  it('should_persist_token_across_calls_when_setAuthToken_invoked', () => {
    setAuthToken('xyz')
    expect(getAuthToken()).toBe('xyz')
    expect(window.localStorage.getItem('skillhub.authToken')).toBe('xyz')

    setAuthToken(null)
    expect(getAuthToken()).toBeNull()
    expect(window.localStorage.getItem('skillhub.authToken')).toBeNull()
  })

  it('should_serialize_query_params_and_skip_undefined_when_get_with_filter', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await apiGet('/skills', {
      query: { keyword: 'pdf', page: 1, certifiedOnly: undefined },
    })

    const [url] = fetchMock.mock.calls[0] as [string]
    const parsed = new URL(url)
    expect(parsed.pathname).toBe('/skillhub/api/v1/skills')
    expect(parsed.searchParams.get('keyword')).toBe('pdf')
    expect(parsed.searchParams.get('page')).toBe('1')
    expect(parsed.searchParams.has('certifiedOnly')).toBe(false)
  })

  it('should_throw_ApiError_with_network_code_when_fetch_itself_fails', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const rejection = apiGet('/skills')

    await expect(rejection).rejects.toBeInstanceOf(ApiError)
    await expect(rejection).rejects.toMatchObject({
      code: NETWORK_ERROR_CODE,
      traceId: NO_TRACE_ID,
    })
  })

  it('should_rethrow_original_error_when_request_aborted', async () => {
    const aborted = new DOMException('The operation was aborted.', 'AbortError')
    fetchMock.mockRejectedValue(aborted)

    // 取消是调用方主动发起的正常控制流,TanStack Query 靠 AbortError 识别请求作废。
    // 包成 ApiError 会让每一次翻页/输入联想的取消都冒充成一次真实的网络故障。
    await expect(apiGet('/skills')).rejects.toBe(aborted)
  })

  it('should_serialize_array_query_param_as_repeated_keys_when_get_with_array_filter', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await apiGet('/skills', { query: { tagIds: [1, 2, 3] } })

    const [url] = fetchMock.mock.calls[0] as [string]
    const parsed = new URL(url)
    expect(parsed.searchParams.getAll('tagIds')).toEqual(['1', '2', '3'])
  })

  it('should_omit_query_param_when_array_filter_is_empty', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await apiGet('/skills', { query: { tagIds: [] } })

    const [url] = fetchMock.mock.calls[0] as [string]
    const parsed = new URL(url)
    expect(parsed.searchParams.has('tagIds')).toBe(false)
  })

  it('should_expose_default_api_base_url_for_building_non_json_links', () => {
    expect(API_BASE_URL).toBe('/skillhub/api/v1')
  })
})

describe('apiPostFormData', () => {
  let fetchMock: Mock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    setAuthToken(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('should_send_form_data_body_without_content_type_header_when_uploading', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { valid: true }))
    const formData = new FormData()
    formData.append('file', new Blob(['skill package'], { type: 'application/zip' }), 'skill.zip')

    const result = await apiPostFormData<{ valid: boolean }>('/skills/precheck', formData)

    expect(result).toEqual({ valid: true })
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/precheck')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(formData)
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
  })

  it('should_attach_authorization_header_when_token_set_and_uploading', async () => {
    setAuthToken('abc123')
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await apiPostFormData('/skills', new FormData())

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer abc123')
  })

  it('should_throw_ApiError_when_upload_rejected_with_contract_error_body', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, {
        code: 'SKILL_NAME_DUPLICATED',
        message: '已存在同名 skill',
        traceId: '8f3c1e2a9b4d',
      }),
    )

    const rejection = apiPostFormData('/skills', new FormData())

    await expect(rejection).rejects.toBeInstanceOf(ApiError)
    await expect(rejection).rejects.toMatchObject({ code: 'SKILL_NAME_DUPLICATED' })
  })
})
