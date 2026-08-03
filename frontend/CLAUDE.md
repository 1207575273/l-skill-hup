# frontend/CLAUDE.md(前端细则)

上层规则见根 `.claude/CLAUDE.md`,本文件只讲前端。中文,禁 emoji。

栈:**React 19 + TypeScript strict + Vite 8 + Tailwind CSS 4**。包管理器 pnpm,**只待在本目录**,根目录没有 workspace。

## 一、目录结构

```
frontend/src/
├── app/            组合根:路由表、Provider 组装、QueryClient 单例
├── api/            fetch 封装 + 各资源的请求函数 + 后端 DTO 的 TS 类型
├── pages/          路由级页面,只做组合,不写业务细节
├── components/     可复用展示组件,纯 UI,不含请求
├── hooks/          业务逻辑与状态(含 Zustand store),页面从这里取数据和动作
├── lib/            纯函数工具(skill 包校验、zip 打包、路径处理等)
└── styles/         全局样式与 Tailwind 入口,除此之外不写 .css 文件
```

`app/` 只放**装配**代码,不放业务:路由表、`QueryClientProvider` / `BrowserRouter` 的组装、`QueryClient` 单例。判断标准是"删掉这个文件应用起不来,但没有任何业务行为随之消失"。放这里而不是塞进 `main.tsx`,是为了让路由表能被 `MemoryRouter` 单独包起来测。

Zustand store 归 `hooks/`(它就是"业务逻辑与状态"),**不单开 `store/` 目录** —— 组件消费它的方式和消费其他 hook 完全一样,分两个目录只会让人每次都要想一下去哪找。

## 二、硬约束

- **TypeScript strict,禁用 `any`**。第三方类型缺失时写 `unknown` + 类型守卫,或补 `.d.ts`;不许用 `any` 糊弄,不许 `@ts-ignore` 整行放行。
- **UI 与业务逻辑分离**:请求、状态、副作用一律进 `hooks/`;`components/` 只接收 props 渲染,不发请求。判断标准:组件里出现 `fetch` / `useQuery` 就是放错地方了。
- 组件单一职责;文件变长就是信号,该拆了。
- 卫语句优先,嵌套不超过 3 层;命名用业务语义。

## 三、状态管理

三层分工,**各管各的,不许越界**:

| 状态类型 | 归谁管 | 例子 |
| --- | --- | --- |
| 服务端数据 | **TanStack Query** | skill 列表、详情、版本、上传结果 |
| 跨组件的客户端状态 | **Zustand** | 安装购物车(勾选的 skill + 目标客户端 + 安装级别) |
| 单组件局部状态 | `useState` / `useReducer` | 表单输入、弹窗开关、折叠状态 |

**最容易犯的错是把服务端数据塞进 Zustand。** 一旦复制进去,就要自己管失效、自己管重新拉取、自己管并发覆盖——这些 TanStack Query 已经做完了。判断标准:**这份数据的真相在服务器上吗?是,就归 Query,Zustand 里只存它的 id 或引用。**

Zustand 用法约束:

- store 按领域切分(`useInstallCartStore`、`useUiStore`),**不搞一个巨型 store**。
- 组件用 selector 订阅(`useStore(s => s.items)`),不整个 store 取出来,否则任何字段变化都触发全量重渲染。
- action 写进 store 内部,组件不直接 `setState` 改结构。
- store 只存状态与同步 action,**异步请求不进 store**,那是 Query 的活。

## 四、Tailwind 规约(v4,与 v3 配置方式完全不同)

**本项目是 Tailwind 4。搜到的教程绝大多数是 v3,先确认版本再抄。** v4 的关键差异:

- **没有 `tailwind.config.ts`,不需要 PostCSS 配置**。走 `@tailwindcss/vite` 插件,在 `vite.config.ts` 里挂上即可。
- CSS 入口写 `@import "tailwindcss";`,**不是** v3 的 `@tailwind base/components/utilities`。
- **设计 token 用 CSS 的 `@theme {}` 块定义**,不是 JS 配置的 `theme.extend`:

```css
@import "tailwindcss";

@theme {
  --color-brand-500: oklch(0.62 0.19 259);
  --radius-card: 0.75rem;
}
```

规约:

- **不引任何 UI 组件库**,组件自己写。
- 样式一律用原子类写在 JSX 里。全局样式(字体、reset、`@theme`)只允许出现在 `styles/` 的入口文件里,**不新建其他 `.css` 文件**。
- 重复出现的类名组合抽成组件,不抽成 `@apply`。`@apply` 只用于确实无法组件化的场景。
- 颜色、间距、圆角等 token 一律进 `@theme`,**JSX 里不许出现 `text-[#1a2b3c]` 这类魔法值**。
- 深浅色支持用 `dark:` 变体。

## 五、API 层

- 所有请求走 `api/` 下的封装,**页面和组件不直接 `fetch`**。
- **只用 GET / POST**,与后端约定一致:查询 GET,写操作 POST(更新 `POST /skills/{id}/update`,删除 `POST /skills/{id}/delete`)。不发 PUT / PATCH / DELETE。
- 基础路径 `/skillhub/api/v1`,由 `import.meta.env` 提供,**不硬编码后端地址**。开发时 Vite dev server 代理到后端实际端口。
- `vite.config.ts` 的 `base` 必须是 `/skillhub/`,与后端 `context-path` 对齐,否则打进 jar 后静态资源 404。
- 后端 DTO 的 TS 类型集中在 `api/types.ts`,与后端契约一一对应;契约变了先改这里,让 tsc 把所有受影响的地方报出来。

## 六、本项目特有:skill 包的浏览器端处理

发布页要做三件事,都在 `lib/` 里,纯函数、必须有单测:

1. **目录读取**:`<input type="file" webkitdirectory>` 拿到 `FileList`,还原成目录树。
2. **完整性校验**:必须存在 `SKILL.md`;解析 frontmatter 拿到 `name` / `description` / `allowed-tools`;目录白名单 `references/` `scripts/` `assets/`;路径安全(拒绝 `..`、绝对路径、控制字符);单文件与总大小、文件数、嵌套深度上限。
3. **打包**:用 JSZip 打成 zip 再上传。

**这三件事的校验结果只用于即时反馈,不是安全边界。** 服务端会用同一套规则完整重做一遍。前端这份存在的意义是让作者上传前就看到问题,而不是让服务端省事。任何"前端已经校验过了,服务端可以略过"的想法都是错的。

## 七、测试

Vitest + Testing Library,TDD 先红后绿。

- `lib/` 的纯函数(校验、frontmatter 解析、路径安全判断)是测试重点,边界用例要齐。
- `hooks/` 用 `renderHook` 测,mock 掉 `api/` 层。
- `components/` 测渲染与交互,不测实现细节;查询用 `getByRole` / `getByLabelText`,不用测试专用 class。

## 八、收尾自查(每次改完必跑,全绿才算完成)

```
cd frontend && pnpm check
```

Biome(lint + format)+ `tsc --noEmit` + Vitest 三项全绿。
