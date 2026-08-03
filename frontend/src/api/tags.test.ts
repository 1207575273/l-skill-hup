import type { Mock } from 'vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchTags } from './tags'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('tags 资源函数', () => {
  let fetchMock: Mock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('should_request_all_tags_without_kind_param_when_fetchTags_called_without_kind', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []))

    await fetchTags()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const parsed = new URL(url)
    expect(init.method).toBe('GET')
    expect(parsed.pathname).toBe('/skillhub/api/v1/tags')
    expect(parsed.searchParams.has('kind')).toBe(false)
  })

  it('should_request_tags_filtered_by_kind_when_fetchTags_called_with_kind', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []))

    await fetchTags('CURATED')

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).searchParams.get('kind')).toBe('CURATED')
  })
})
