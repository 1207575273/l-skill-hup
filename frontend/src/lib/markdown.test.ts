import { describe, expect, it } from 'vitest'
import { renderMarkdownToSafeHtml } from './markdown'

/**
 * 这份测试里安全用例的比重刻意高于渲染用例。
 *
 * README 与 SKILL.md 是**别人上传的内容**,把它变成 HTML 插进页面,就是存储型 XSS 的标准入口:
 * 攻击者上传一个带脚本的 skill,任何浏览它详情页的人都会中招 —— 包括管理员,而管理员的会话能
 * 认证 skill、能禁用账号。根 CLAUDE.md 第七节那句"安全上没有内部工具所以无所谓这一说"在这里
 * 是字面意义上的。
 *
 * 所以渲染这条路上有两道关:marked 负责把 Markdown 变成 HTML,DOMPurify 负责把不该出现的东西
 * 剥掉。**marked 自己不是安全边界** —— 它默认就允许 Markdown 里内嵌原始 HTML 并原样透传,
 * 那是 CommonMark 规范要求的行为,不是它的缺陷。
 */
describe('renderMarkdownToSafeHtml', () => {
  describe('渲染', () => {
    it('should_render_heading_when_markdown_has_hash_prefix', () => {
      expect(renderMarkdownToSafeHtml('# 标题')).toContain('<h1>标题</h1>')
    })

    it('should_render_fenced_code_block_when_markdown_has_backticks', () => {
      const html = renderMarkdownToSafeHtml('```bash\nnpm install\n```')

      expect(html).toContain('<pre>')
      // 保留 language-* 类名:现在没有语法高亮,但将来接高亮器要靠它认语言,
      // 而 sanitize 的属性白名单一旦漏了 class,这个信息就永久丢了。
      expect(html).toContain('language-bash')
    })

    it('should_render_table_when_markdown_uses_gfm_syntax', () => {
      const html = renderMarkdownToSafeHtml('| 名称 | 说明 |\n| --- | --- |\n| a | b |')

      expect(html).toContain('<table>')
      expect(html).toContain('<td>a</td>')
    })

    it('should_render_task_list_when_markdown_uses_gfm_checkbox', () => {
      const html = renderMarkdownToSafeHtml('- [x] 已完成\n- [ ] 未完成')

      expect(html).toContain('type="checkbox"')
      // 只读:渲染出来的复选框不该能点,它是内容不是控件。
      expect(html).toContain('disabled')
    })

    it('should_return_empty_string_when_input_is_empty', () => {
      expect(renderMarkdownToSafeHtml('')).toBe('')
    })

    it('should_return_empty_string_when_input_is_only_whitespace', () => {
      expect(renderMarkdownToSafeHtml('   \n  \n')).toBe('')
    })
  })

  describe('安全', () => {
    it('should_strip_script_tag_when_markdown_embeds_raw_html', () => {
      const html = renderMarkdownToSafeHtml('正文\n\n<script>alert(1)</script>')

      expect(html).not.toContain('<script')
      expect(html).not.toContain('alert(1)')
    })

    it('should_strip_event_handler_when_img_carries_onerror', () => {
      const html = renderMarkdownToSafeHtml('<img src=x onerror="alert(1)">')

      expect(html).not.toContain('onerror')
      expect(html).not.toContain('alert(1)')
    })

    it('should_drop_javascript_scheme_when_link_href_is_script', () => {
      const html = renderMarkdownToSafeHtml('[点我](javascript:alert(1))')

      expect(html).not.toContain('javascript:')
    })

    it('should_drop_javascript_scheme_when_href_uses_html_entity_obfuscation', () => {
      // `javascript&colon;` 这类实体混淆能骗过只做字符串前缀匹配的过滤,
      // 但骗不过按 DOM 解析后再判断 URL 的 sanitize。
      const html = renderMarkdownToSafeHtml('<a href="javascript&colon;alert(1)">点我</a>')

      expect(html).not.toContain('javascript')
    })

    it('should_strip_iframe_when_markdown_embeds_one', () => {
      const html = renderMarkdownToSafeHtml('<iframe src="https://evil.example.com"></iframe>')

      expect(html).not.toContain('<iframe')
    })

    it('should_strip_style_tag_when_markdown_embeds_one', () => {
      // 允许 <style> 等于允许改写整个页面的外观,可以把伪造的登录框覆盖到真页面上。
      const html = renderMarkdownToSafeHtml('<style>body{display:none}</style>')

      expect(html).not.toContain('<style')
    })

    it('should_strip_inline_style_attribute_when_element_carries_one', () => {
      const html = renderMarkdownToSafeHtml('<p style="position:fixed;inset:0">覆盖层</p>')

      expect(html).not.toContain('style=')
    })

    it('should_mark_links_as_noopener_when_rendered', () => {
      const html = renderMarkdownToSafeHtml('[外链](https://example.com)')

      // 新标签页打开时不加 noopener,被打开的页面能通过 window.opener 把原页面导航走
      // (反向 tabnabbing),用来钓登录页。
      expect(html).toContain('rel="noopener noreferrer"')
      expect(html).toContain('target="_blank"')
    })
  })
})
