import DOMPurify from 'dompurify'
import { marked } from 'marked'

/**
 * 把 Markdown 渲染成**可以安全插进页面**的 HTML。
 *
 * <p>两道关,缺一不可:
 *
 * 1. `marked` 把 Markdown 变成 HTML。
 * 2. `DOMPurify` 把不该出现的东西剥掉。
 *
 * **第 2 步不是"加固",是必需的。** CommonMark 规范要求 Markdown 里内嵌的原始 HTML 原样透传,
 * 所以 `marked` 见到 `<script>` 会老老实实输出 `<script>` —— 那是它遵守规范,不是缺陷。而这里渲染的
 * 是**别人上传的 README / SKILL.md**,等于把攻击者写的 HTML 插进每一个浏览者的页面,其中包括能认证
 * skill、能禁用账号的管理员。
 *
 * 关于为什么不用 React 的 `dangerouslySetInnerHTML` 之外的方案:把 HTML 字符串转成 React 元素树
 * (react-markdown 那条路)确实能绕开 `dangerouslySetInnerHTML`,但代价是一整棵 unified/remark/rehype
 * 依赖树,而**安全性并不因此提高** —— 那条路同样要靠 rehype-sanitize 白名单,只是白名单换了个地方写。
 * 用 marked + DOMPurify 依赖少、白名单就在眼前。
 */

/**
 * 标签白名单。**用白名单而不是黑名单**:黑名单要求你穷举所有危险标签,漏一个就是漏洞;白名单漏一个
 * 只是少渲染一种元素,失败方向是安全的。
 *
 * 这份清单就是 Markdown(含 GFM)能生成的全部元素,没有多余项。`input` 在里面是因为 GFM 的任务列表
 * (`- [x]`)会渲染成只读复选框。
 */
const ALLOWED_TAGS = [
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'p',
  'br',
  'hr',
  'strong',
  'em',
  'del',
  'code',
  'pre',
  'blockquote',
  'ul',
  'ol',
  'li',
  'a',
  'img',
  'table',
  'thead',
  'tbody',
  'tr',
  'th',
  'td',
  'input',
]

/**
 * 属性白名单。
 *
 * - `class` 必须留:代码块的 `language-bash` 靠它,现在没有语法高亮,但将来接高亮器要认这个类名。
 * - `align` 留给表格对齐。
 * - **`style` 刻意不在里面**:允许行内样式等于允许把一个 `position:fixed;inset:0` 的假登录框盖满整页。
 *   代价是表格的部分对齐样式会丢,值得。
 */
const ALLOWED_ATTR = [
  'href',
  'src',
  'alt',
  'title',
  'class',
  'align',
  'type',
  'checked',
  'disabled',
]

/**
 * 给站外链接补 `target` 与 `rel`。
 *
 * 不加 `rel="noopener"` 时,新标签页里的文档能通过 `window.opener` 把**原页面**导航到任意地址
 * (反向 tabnabbing):用户点了 README 里一个链接,回头发现原来那个标签页变成了钓鱼的登录页。
 *
 * 锚点链接(`#xxx`)排除在外 —— 页内跳转开新标签页是纯粹的干扰。
 */
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (!(node instanceof HTMLAnchorElement)) {
    return
  }
  const href = node.getAttribute('href')
  if (!href || href.startsWith('#')) {
    return
  }
  node.setAttribute('target', '_blank')
  node.setAttribute('rel', 'noopener noreferrer')
})

export function renderMarkdownToSafeHtml(markdown: string): string {
  if (markdown.trim() === '') {
    return ''
  }

  // async: false 让返回类型收敛成 string —— marked 默认签名是 `string | Promise<string>`,
  // 因为它支持异步扩展。本项目没有异步扩展,显式关掉比在调用处 await 或断言类型都干净。
  const rawHtml = marked.parse(markdown, { async: false, gfm: true })

  return DOMPurify.sanitize(rawHtml, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    // 不允许任何 data:/blob: URI。data: 能塞进一个带脚本的 SVG,而 Markdown 里正当的图片引用
    // 用不着它 —— DOMPurify 默认的 URI 白名单(http/https/mailto/tel 等)本就不含 data:,
    // 这里只是把"依赖默认值"变成"显式选择",免得哪天默认值放宽了没人察觉。
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto|tel):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
  })
}
