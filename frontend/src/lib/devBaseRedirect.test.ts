import { describe, expect, it } from 'vitest'
import { resolveBaseRedirect } from './devBaseRedirect'

const BASE = '/skillhub/'

describe('resolveBaseRedirect', () => {
  it('should_redirect_when_path_is_root', () => {
    expect(resolveBaseRedirect('/', BASE)).toBe('/skillhub/')
  })

  it('should_redirect_when_base_lacks_trailing_slash', () => {
    // 最难受的一种:地址看起来完全正确,手动刷新就撞上 Vite 那句提示,
    // 而"少一个斜杠"从提示文字里看不出来——它只是把正确地址原样复述了一遍。
    expect(resolveBaseRedirect('/skillhub', BASE)).toBe('/skillhub/')
  })

  it('should_keep_query_string_when_redirecting', () => {
    // 丢参数极难联想到是重定向干的
    expect(resolveBaseRedirect('/skillhub?token=abc', BASE)).toBe('/skillhub/?token=abc')
  })

  it('should_not_redirect_when_already_under_base', () => {
    expect(resolveBaseRedirect('/skillhub/', BASE)).toBeNull()
    expect(resolveBaseRedirect('/skillhub/manage', BASE)).toBeNull()
  })

  it('should_not_redirect_vite_internal_requests', () => {
    // 这条是这个模块真正的风险点:前缀匹配会把 HMR 握手和依赖预构建请求一起重定向掉,
    // 症状是热更新静默失效——不报错,只是"改了代码没反应"。
    expect(resolveBaseRedirect('/@vite/client', BASE)).toBeNull()
    expect(resolveBaseRedirect('/@react-refresh', BASE)).toBeNull()
    expect(resolveBaseRedirect('/node_modules/.vite/deps/react.js', BASE)).toBeNull()
  })

  it('should_not_redirect_paths_that_merely_start_with_base_name', () => {
    // /skillhubx 与 /skillhub 只差一个字符,但它不是 base,跳过去会把用户送到一个他没要的地方。
    expect(resolveBaseRedirect('/skillhubx', BASE)).toBeNull()
  })

  it('should_ignore_missing_url', () => {
    expect(resolveBaseRedirect(undefined, BASE)).toBeNull()
    expect(resolveBaseRedirect('', BASE)).toBeNull()
  })
})
