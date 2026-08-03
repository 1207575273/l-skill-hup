import type { Mock } from 'vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildInstallScriptUrl, createInstallBundle, fetchInstallClients } from './install'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('install 资源函数', () => {
  let fetchMock: Mock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('should_request_clients_endpoint_when_fetchInstallClients_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []))

    await fetchInstallClients()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.method).toBe('GET')
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/clients')
  })

  it('should_post_items_client_and_scope_when_createInstallBundle_called', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, {
        token: 't1',
        expiresAt: '2026-09-01T00:00:00Z',
        client: 'claude',
        scope: 'global',
        powerShellCommand: 'irm https://x/i/t1.ps1 | iex',
        bashCommand: 'curl -fsSL https://x/i/t1.sh | sh',
      }),
    )

    const result = await createInstallBundle([{ skillId: 1, versionId: 10 }], 'claude', 'global')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/bundles')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      items: [{ skillId: 1, versionId: 10 }],
      client: 'claude',
      scope: 'global',
    })
    expect(result.powerShellCommand).toContain('t1.ps1')
  })

  it('should_build_powershell_script_url_without_fetching_when_kind_is_ps1', () => {
    const url = buildInstallScriptUrl('abc123', 'ps1')

    expect(url).toBe('/skillhub/i/abc123.ps1')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('should_build_bash_script_url_without_fetching_when_kind_is_sh', () => {
    const url = buildInstallScriptUrl('abc123', 'sh')

    expect(url).toBe('/skillhub/i/abc123.sh')
  })
})
