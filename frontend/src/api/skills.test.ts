import type { Mock } from 'vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken } from './client'
import {
  buildSkillDownloadUrl,
  fetchSkillDetail,
  fetchSkillList,
  fetchSkillVersions,
  precheckSkillPackage,
  publishSkill,
  publishSkillVersion,
  relistSkill,
  unlistSkill,
  updateSkillMetadata,
} from './skills'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/**
 * `FormData.append(name, blob)` 在 spec 里会把 Blob 包成一个新的 File 对象(默认文件名 `blob`),
 * 取出来的值**不会**与原始 Blob 引用相等,只能比内容/大小/类型(WHATWG XHR 规范
 * "create an entry" 步骤:non-File Blob 被强制转换成 File)。
 */
async function expectFormDataHoldsBlob(
  formData: FormData,
  key: string,
  original: Blob,
): Promise<void> {
  const uploaded = formData.get(key)
  expect(uploaded).toBeInstanceOf(Blob)
  const uploadedBlob = uploaded as Blob
  expect(uploadedBlob.size).toBe(original.size)
  expect(await uploadedBlob.text()).toBe(await original.text())
}

describe('skills 资源函数', () => {
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

  it('should_request_skill_list_with_serialized_filter_when_fetchSkillList_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { items: [], total: 0, page: 1, size: 20 }))

    await fetchSkillList({
      keyword: 'pdf',
      tagIds: [1, 2],
      certifiedOnly: true,
      sort: 'downloads',
      page: 2,
      size: 10,
    })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const parsed = new URL(url)
    expect(init.method).toBe('GET')
    expect(parsed.pathname).toBe('/skillhub/api/v1/skills')
    expect(parsed.searchParams.get('keyword')).toBe('pdf')
    expect(parsed.searchParams.getAll('tagIds')).toEqual(['1', '2'])
    expect(parsed.searchParams.get('certifiedOnly')).toBe('true')
    expect(parsed.searchParams.get('sort')).toBe('downloads')
    expect(parsed.searchParams.get('page')).toBe('2')
    expect(parsed.searchParams.get('size')).toBe('10')
    expect(parsed.searchParams.has('source')).toBe(false)
    expect(parsed.searchParams.has('ownerUserId')).toBe(false)
  })

  it('should_request_skill_detail_by_id_when_fetchSkillDetail_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 42 }))

    await fetchSkillDetail(42)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/42')
    expect(init.method).toBe('GET')
  })

  it('should_request_skill_versions_by_id_when_fetchSkillVersions_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []))

    await fetchSkillVersions(7)

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/7/versions')
  })

  it('should_send_multipart_file_field_when_precheckSkillPackage_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { valid: true }))
    const packageFile = new Blob(['zip content'], { type: 'application/zip' })

    await precheckSkillPackage(packageFile)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/precheck')
    expect(init.method).toBe('POST')
    const formData = init.body as FormData
    await expectFormDataHoldsBlob(formData, 'file', packageFile)
  })

  it('should_build_form_data_with_all_fields_when_publishSkill_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 1 }))
    const packageFile = new Blob(['zip content'], { type: 'application/zip' })

    await publishSkill({
      packageFile,
      version: '1.0.0',
      displayName: 'PDF 转换',
      source: 'INTERNAL',
      curatedTagIds: [1, 2],
      freeTagNames: ['自动化'],
    })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills')
    const formData = init.body as FormData
    await expectFormDataHoldsBlob(formData, 'file', packageFile)
    expect(formData.get('version')).toBe('1.0.0')
    expect(formData.get('displayName')).toBe('PDF 转换')
    expect(formData.get('source')).toBe('INTERNAL')
    expect(formData.getAll('curatedTagIds')).toEqual(['1', '2'])
    expect(formData.getAll('freeTagNames')).toEqual(['自动化'])
  })

  it('should_omit_display_name_field_when_publishSkill_called_without_display_name', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 1 }))

    await publishSkill({
      packageFile: new Blob(['zip']),
      version: '1.0.0',
      source: 'COMMUNITY',
    })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const formData = init.body as FormData
    expect(formData.has('displayName')).toBe(false)
    expect(formData.getAll('curatedTagIds')).toEqual([])
  })

  it('should_send_file_and_version_fields_when_publishSkillVersion_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 1 }))
    const packageFile = new Blob(['zip content'])

    await publishSkillVersion(9, { packageFile, version: '2.0.0' })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/9/versions')
    const formData = init.body as FormData
    await expectFormDataHoldsBlob(formData, 'file', packageFile)
    expect(formData.get('version')).toBe('2.0.0')
  })

  it('should_post_json_body_to_update_endpoint_when_updateSkillMetadata_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 3 }))

    await updateSkillMetadata(3, {
      displayName: '新展示名',
      source: 'INTERNAL',
      curatedTagIds: [5],
      freeTagNames: [],
    })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/3/update')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      displayName: '新展示名',
      source: 'INTERNAL',
      curatedTagIds: [5],
      freeTagNames: [],
    })
  })

  it('should_post_to_unlist_endpoint_when_unlistSkill_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 3 }))

    await unlistSkill(3)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/3/unlist')
    expect(init.method).toBe('POST')
  })

  it('should_post_to_relist_endpoint_when_relistSkill_called', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 3 }))

    await relistSkill(3)

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(new URL(url).pathname).toBe('/skillhub/api/v1/skills/3/relist')
  })

  it('should_build_download_url_without_fetching_when_buildSkillDownloadUrl_called', () => {
    const url = buildSkillDownloadUrl(3, '1.0.0')

    expect(url).toBe('/skillhub/api/v1/skills/3/versions/1.0.0/download')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
