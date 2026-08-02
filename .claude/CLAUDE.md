# CLAUDE.md(项目工程宪法)

项目规则优先于全局规则。全程中文,**禁 emoji 与装饰字符**:状态用 `[PASS]/[FAIL]/[WARN]/[SKIP]/[INFO]`,箭头用 `->`,分隔线用 `=` 或 `-` 重复。

## 一、这个项目是什么

**skill-hub** —— 团队/公司内部的 Agent Skill 分发平台,解决 skill 在团队内的流通问题。

一句话链路:**门户网站选 skill -> 服务端渲染安装脚本 -> 本地一条命令装进 Claude Code / Codex / WorkBuddy 的 skills 目录**。

关键判断,写死不再重议:

- **不做本地常驻程序**。不写 CLI、不编译跨平台二进制、不做代码签名与自动更新。本地那一侧就是服务端动态渲染的一段 `.ps1` / `.sh`,零安装零依赖。
- 三个目标客户端的 skill 包**格式完全一致**(Anthropic Agent Skills 规范:`SKILL.md` + 可选 `references/` `scripts/` `assets/`),差异只在落盘根目录。所以客户端支持做成一张**适配表**,新增客户端只加一行。

| 客户端 | 全局 | 项目级 |
| --- | --- | --- |
| Claude Code | `~/.claude/skills/` | `<项目>/.claude/skills/` |
| Codex | `~/.codex/skills/` | `<项目>/.codex/skills/` |
| WorkBuddy | `~/.workbuddy/skills/` | 待本地实测确认,不许猜 |

项目级安装不需要服务端知道你的项目路径:**用户 cd 到项目目录再跑脚本**,脚本按当前工作目录落盘,写入前打印目标路径并要求确认。

## 二、技术栈(已锁定,不再讨论)

| 层 | 选型 |
| --- | --- |
| JDK | 21 |
| 后端 | Spring Boot 4.1.x + MyBatis(`mybatis-spring-boot-starter` 4.0.1)+ Flyway |
| 数据库 | PostgreSQL |
| 前端 | React 18 + TypeScript strict + Vite + Tailwind CSS |
| 前端状态 | TanStack Query(服务端数据)+ Zustand(客户端状态) |
| 架构 | 轻量 DDD 锁代码形态,TDD 锁质量 |
| 交付 | monorepo + Docker Compose |

**明确不用**:MyBatis-Plus、Nginx、任何 UI 组件库(Tailwind 裸奔自己写组件)、根目录 pnpm workspace。

前端产物直接打进后端 jar 同源托管,不需要 Nginx。后端挂在 `server.servlet.context-path: /skillhub` 下,前端 `vite.config.ts` 的 `base` 同步为 `/skillhub/`。

**HTTP 方法只用 GET / POST**:查询走 GET,一切写操作走 POST。更新 `POST /{id}/update`,删除 `POST /{id}/delete`。禁止 PUT / PATCH / DELETE。

## 三、目录结构

```
l-skill-hup/
├── .claude/CLAUDE.md       本文件,全项目宪法
├── backend/                Spring Boot,自带 CLAUDE.md
├── frontend/               React + Vite,自带 CLAUDE.md;pnpm 只待在这里
├── scripts/*.mjs           统一入口,零依赖,node 直接跑
├── docker/
│   ├── Dockerfile                  多阶段:node 构建前端 -> maven 构建后端 -> jre 运行
│   └── docker-compose.yml          就这一个文件,dev 只起 postgres,prod 起全部
└── docs/                   持久化文档,归集规范见 docs/README.md
```

`backend/CLAUDE.md` 与 `frontend/CLAUDE.md` 是各自领域的细则,动对应目录前必读。

## 四、通用编码规范

- **命名**用业务语义,禁无意义缩写(循环 `i/j/k` 除外)。
- **卫语句优先**,嵌套不超过 3 层。
- **禁吞异常、禁空 catch**,异常信息必须带上下文。
- **魔法值**提常量或枚举。
- **注释解释 Why 不解释 What**。
- **日志**打关键参数,敏感字段脱敏。
- 高内聚低耦合,一个模块/函数只做一件事。`if-else > 3` 或复杂状态判断改策略映射。

## 五、TDD(锁质量)

严格 **Red -> Green -> Refactor**:先写失败测试 -> 最小实现让它过 -> 重构。

- 测试命名 `should_<行为>_when_<前置>`,一个测试只验证一个行为。
- Mock 外部依赖(DB/HTTP/存储),**不 mock 被测对象自身的方法**。
- 后端 JUnit 5 + Mockito + Testcontainers(起真 PostgreSQL,**不用 H2 冒充**)。
- 前端 Vitest + Testing Library。

三个维度各管各的,别混为一谈:

| 工具 | 回答的问题 |
| --- | --- |
| JUnit 5 / Vitest | 行为对不对 |
| JaCoCo | 测到了多少(闸门 >= 80%) |
| **ArchUnit** | **结构有没有烂掉** |

覆盖率高不代表架构没腐化,架构干净也不代表行为正确。第三节那条"依赖方向单向"由 **ArchUnit 测试强制执行,不靠自觉**——文字约束拦不住有人图省事在 Controller 里直接注入 Mapper,一条会失败的测试可以。**新增架构约定时同步加 ArchUnit 规则**,否则那条约定三个月后必然失效。

## 六、质量防线

| 侧 | 命令 | 检查项 |
| --- | --- | --- |
| 后端 | `cd backend && mvn verify` | Spotless 格式 + 编译 + JUnit + **ArchUnit** + JaCoCo 覆盖率 |
| 前端 | `cd frontend && pnpm check` | Biome lint/format + `tsc --noEmit` + Vitest |
| 全量 | `node scripts/check.mjs` | 依次跑上面两条,汇总结果 |

自查用于自省,不拦提交。**全绿才算任务完成**,不许拿"只是小改动"绕过。

## 七、安全底线(这个项目的性质决定,不许省)

skill-hub 分发的是**会被 Agent 执行的内容**,安全上没有"内部工具所以无所谓"这一说。

1. **前端校验不是安全边界**。浏览器端的 skill 包校验只为即时反馈,**服务端必须用同一套规则完整重做一遍**。图省事跳过服务端校验的代码一律不合并。
2. **解包必须防 zip-slip**:拒绝含 `..`、绝对路径、控制字符的条目;拒绝符号链接。
3. **防 zip 炸弹**:解压后总大小、文件数、嵌套深度都要有上限。
4. **制品 sha256 在渲染脚本时内嵌**,脚本下载后当场校验,不匹配立即中止。
5. 安装脚本**只写既定的 skills 目录**,不碰任何其他路径;覆盖同名 skill 前先备份成 `<name>.bak.<时间戳>`。
6. `install_bundle` 的 token 有有效期,防止旧命令在群里流传半年后还在装过期版本。

## 八、踩坑点(遇到新坑往这里补,格式:现象 -> 根因 -> 处理)

### 8.1 Spring Boot 4 用 Jackson 3,包名变了

- **现象**:抄网上 Spring Boot 3 的代码片段,`import com.fasterxml.jackson.*` 编译不过。
- **根因**:Spring Boot 4 基线换成 Jackson 3,包名迁移到 `tools.jackson.*`。网上绝大多数教程仍是 Jackson 2。
- **处理**:一律用 `tools.jackson.*`。搜到的示例默认当作 Spring Boot 3 的,先核对 import 再用。

### 8.2 Spring Boot 4 自动配置 JAR 已拆分

- **现象**:按 3.x 经验排查依赖或找自动配置类,位置对不上。
- **根因**:Spring Boot 4 把自动配置拆成多个 JAR,starter 的依赖组织跟 3.x 不同。
- **处理**:排依赖用 `mvn dependency:tree`,不要凭 3.x 记忆推断。

### 8.3 MyBatis 与 Spring Boot 4 的版本对应

- **现象**:`mybatis-spring-boot-starter` 3.0.x 在 Spring Boot 4 上启动失败。
- **根因**:3.0.x 只支持 Spring Boot 3.2-3.5,支持 Spring Boot 4 的是 **4.0.0+**。
- **处理**:锁 `4.0.1` 或更高,不要跟着老教程写 3.0.x。

### 8.4 Windows 是主开发环境

- 命令默认按 **Git Bash** 给;Git Bash 搞不定再降级 PowerShell,并明确标注。
- 安装脚本 **PowerShell 是一等公民**,`.ps1` 与 `.sh` 两份都要写、都要测,不许只测一份。

## 九、Agent 与协作

**简单需求直接在主会话干完,不启动流水线。** 仅当多模块、需求模糊或工作量大时才拆:

```
requirements-analyst -> architect -> [backend-engineer | frontend-engineer] -> test-engineer
```

走流水线时各 Agent 产出任务文档到 `docs/<YYYYMMDDHHMMSS-任务名>/{需求,架构,后端,前端,测试}/`,成规模工作必留档并配架构图/流程图/用例图(Mermaid)。归集规范以 `docs/README.md` 为单一事实源。

## 十、环境与运行

**只有两个环境**,对应 Spring Profile `dev` 与 `prod`,没有 test/staging。

| | dev | prod |
| --- | --- | --- |
| 位置 | 本机 | 内网服务器 |
| 基座 | 本地 Docker 起 PG | 服务器 Docker 起 PG |
| 应用 | IDE 或 `mvn spring-boot:run` 直接跑,热重载 | 容器内跑打好的 jar |
| 前端 | Vite dev server + proxy | 已打进 jar,同源托管 |

**dev 和 prod 各跑各的实例,一份 `docker/docker-compose.yml` 两边共用**(同一个 PG 版本、同一套初始化)。就一个文件,不拆、不用 profiles、不用 external 卷 —— dev 起 `postgres` 一个 service,prod 起全部。

**严禁 dev 连 prod 的数据库,哪怕只是换个库名。** 理由是 Flyway:迁移在应用启动时自动执行 DDL,共用实例时配置写错一次就直接改了生产库结构,而 Flyway 铁律是"已执行的脚本不能改",污染 `flyway_schema_history` 后清理极其痛苦。这是迟早会发生一次的事故,不是靠小心能避免的。何况 Testcontainers 本来就要求本地有 Docker,多起一个 PG 容器的边际成本接近零。

```
起基座   docker compose -f docker/docker-compose.yml up -d postgres
开发     node scripts/dev.mjs          并行起后端 + Vite,带端口探测
自查     node scripts/check.mjs        后端 + 前端全量检查
生产     docker compose -f docker/docker-compose.yml up -d --build
```

数据卷用普通命名卷。`down` 不删数据,`down -v` 会删 —— 现阶段库里是 skill 制品与元数据,真删了让大家重新上传即可,不值得为此加部署复杂度。等哪天数据变得不可重建了(有了安装统计、审核记录、用户归属),再回头做备份或改 external。

生产走多阶段 Dockerfile:node 阶段构建前端 -> 产物拷进 maven 阶段 -> `mvn package` -> jre 阶段运行。宿主机不需要装 JDK / Node / Maven。

连接信息一律走**环境变量**,不硬编码进 `application-*.yml`。

## 十一、制品存储

制品访问一律走 `ArtifactStorage` 接口,业务代码不许直连文件系统或存储 SDK。

- **当前(P0/P1)**:`LocalFsArtifactStorage`,落在挂载卷的文件夹里。这个量级(skill zip 几十 KB 到几 MB,总量几百 MB)不需要对象存储。
- **将来真需要对象存储时**:只写 **S3 标准 API**(AWS SDK for Java),不碰任何厂商特有接口。这样 MinIO / RustFS 之间换只是改一行 endpoint 配置。
- **选型待定,到时候再决**:RustFS 是 Apache 2.0、Rust 无 GC、内存占用低,但截至 2026-08 仍是 `1.0.0-beta.12`,GA 跳票;MinIO 成熟但社区版已停止接受新变更且是 AGPL v3。等真要上时看 RustFS GA 到了没。

## 十二、提交

遵循 Conventional Commits(`feat:` / `fix:` / `refactor:` / `docs:` / `chore:`),**作者 codeYang,提交信息里不得出现 Claude / Co-Authored-By: Claude 之类标记**。
