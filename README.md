# skill-hub

团队内部的 Agent Skill 分发平台,解决 skill 在团队里的流通问题。

一句话链路:**门户网站选 skill -> 服务端渲染安装脚本 -> 本地一条命令装进 Claude Code / Codex / WorkBuddy 的 skills 目录**。

本地那一侧没有常驻程序,不装 CLI、不发二进制 —— 就是服务端动态渲染的一段 `.ps1` / `.sh`,零安装零依赖。

## 支持的客户端

三个客户端的 skill 包格式完全一致(Anthropic Agent Skills 规范:`SKILL.md` + 可选 `references/` `scripts/` `assets/`),差异只在落盘根目录,所以客户端支持是一张适配表,新增客户端只加一行。

| 客户端 | 全局 | 项目级 |
| --- | --- | --- |
| Claude Code | `~/.claude/skills/` | `<项目>/.claude/skills/` |
| Codex | `~/.codex/skills/` | `<项目>/.codex/skills/` |
| WorkBuddy | `~/.workbuddy/skills/` | 待本地实测确认 |

项目级安装不需要服务端知道你的项目路径:用户 cd 到项目目录再跑脚本,脚本按当前工作目录落盘,写入前打印目标路径并要求确认。

## 技术栈

| 层 | 选型 |
| --- | --- |
| JDK | 21 |
| 后端 | Spring Boot 4.1.0 + MyBatis + Flyway |
| 数据库 | PostgreSQL 17 |
| 前端 | React 19 + TypeScript strict + Vite 8 + Tailwind CSS 4 |
| 前端状态 | TanStack Query(服务端数据)+ Zustand(客户端状态) |
| 交付 | monorepo + Docker Compose,前端产物打进后端 jar 同源托管 |

## 上手

前置:Windows + Git Bash,Node 22+,JDK 21,Maven,pnpm,以及一个能用的 Docker(本机是 WSL 里的 dockerd,见下)。

```bash
# 1. 配置(可选,dev 用默认值即可;dev.mjs 会自动从模板生成)
cp docker/.env.example docker/.env

# 2. 起基座 + 并行跑后端与 Vite
node scripts/dev.mjs

#    后端 http://localhost:18080/skillhub
#    前端 http://localhost:5173/skillhub/
```

`dev.mjs` 会自己做完这几件事,不用手动排查:探测 docker(不通就唤醒 WSL)-> 拉起 postgres 并等到 healthy -> 检查端口占用(占了会告诉你是哪个容器或哪个 PID)-> 并行启动两端,输出带 `[backend]` / `[frontend]` 标签 -> Ctrl+C 时连同 java / node 子进程一起收掉。

常用参数:`--backend-only`、`--frontend-only`、`--no-docker`。

## 命令

| 目的 | 命令 |
| --- | --- |
| 开发 | `node scripts/dev.mjs` |
| 全量自查 | `node scripts/check.mjs`(`--backend` / `--frontend` 可只跑一侧) |
| 只查后端 | `cd backend && mvn verify` |
| 只查前端 | `cd frontend && pnpm check` |
| 只起基座 | `docker compose -f docker/docker-compose.yml up -d postgres` |
| dev 档(应用也进容器) | `docker compose -f docker/docker-compose.yml --profile dev up -d --build` |
| prod 档 | `docker compose -f docker/docker-compose.yml --profile prod up -d --build` |
| 停 | `docker compose -f docker/docker-compose.yml --profile <档> down`(加 `-v` 会删数据) |

应用分 `app-dev` / `app-prod` 两档,共用同一个镜像和同一个 postgres,只有 `SPRING_PROFILES_ACTIVE` 不同,数据卷各自独立。两档端口相同、互斥,不该同时起。

**停的时候必须带 `--profile`** —— 不带时 compose 看不见带 profile 的服务,`down` 会静默地什么都没停,而命令本身返回 0。

`scripts/*.mjs` 零第三方依赖,`node` 直接跑。

**全绿才算任务完成**,不许拿"只是小改动"绕过。

## 目录

```
l-skill-hup/
├── .claude/CLAUDE.md       全项目工程宪法,动手前必读
├── backend/                Spring Boot,自带 CLAUDE.md
│   └── .mvn/               项目级 Maven 配置(镜像源,绕开连不通的内网私库)
├── frontend/               React + Vite,自带 CLAUDE.md;pnpm 只待在这里
├── scripts/                统一入口,零依赖
│   ├── dev.mjs             开发
│   ├── check.mjs           全量自查
│   └── lib/                共用的日志 / 子进程 / docker 探测
├── docker/
│   ├── Dockerfile          多阶段:node 构建前端 -> maven 打 jar -> jre 运行
│   ├── docker-compose.yml  一个文件,dev 与 prod 共用
│   └── .env.example        prod 部署前必须复制成 .env 并改掉 DB_PASSWORD
└── docs/                   持久化文档,归集规范见 docs/README.md
```

## 环境

只有 **dev** 与 **prod** 两个 Spring Profile,没有 test / staging。

| | dev | prod |
| --- | --- | --- |
| 位置 | 本机 | 内网服务器 |
| 数据库 | 本地 Docker 起 PG,宿主端口 15432 | 服务器 Docker 起 PG |
| 应用 | IDE 或 `mvn spring-boot:run`,热重载 | 容器内跑打好的 jar |
| 前端 | Vite dev server + proxy | 已打进 jar,同源托管 |

**严禁 dev 连 prod 的数据库,哪怕只是换个库名。** Flyway 在应用启动时自动执行 DDL,配置写错一次就直接改了生产库结构,而 Flyway 铁律是"已执行的脚本不能改",污染 `flyway_schema_history` 后清理极其痛苦。

日志双通道:**控制台人类可读,文件 `logs/skill-hub.jsonl` 是一行一个 JSON(ECS 规范)**。容器里日志目录挂了命名卷,重建不丢。

## 本机 Docker 的特殊情况

Docker Engine 装在 WSL 发行版 `Ubuntu-20.04` 里,dockerd 原生监听 `127.0.0.1:12375`,Windows 侧是手工装的静态 CLI + compose 插件(**不是 Docker Desktop**,顺带避开其商业授权)。

两条纪律:

1. **只用命名卷,不许绑定挂载。** 路径由 daemon 侧解释,而 daemon 在 WSL 里,`./data:/x` 会被 CLI 展开成 `D:\...` 传过去,daemon 当 Linux 路径解析直接失败。
2. **PG 对外映射 15432,不用 5432。** WSL 里同时跑着其他项目的容器。

**WSL 会周期性自己关闭**,这是 WSL 的已知缺陷(microsoft/WSL#40363),微软没有根本修复,不是你的配置坏了 —— 断连时的报错 `connectex: 目标计算机积极拒绝` 极具误导性,**不要去改 daemon.json**。`scripts/*.mjs` 已经自带探测唤醒;手动恢复就一句 `wsl -d Ubuntu-20.04 -- true`。

完整教程与排障见 [docs/20260802193801_Windows下WSL2-Docker开发环境实践.md](docs/20260802193801_Windows下WSL2-Docker开发环境实践.md)。

## 安全底线

分发的是**会被 Agent 执行的内容**,没有"内部工具所以无所谓"这一说。

1. **前端校验不是安全边界**,服务端必须用同一套规则完整重做一遍。
2. 解包防 **zip-slip**:拒绝含 `..`、绝对路径、控制字符的条目,拒绝符号链接。
3. 防 **zip 炸弹**:解压总大小、文件数、嵌套深度都有上限。
4. 制品 **sha256 在渲染脚本时内嵌**,脚本下载后当场校验,不匹配立即中止。
5. 安装脚本**只写既定的 skills 目录**,覆盖同名 skill 前先备份成 `<name>.bak.<时间戳>`。
6. `install_bundle` 的 token 有有效期,防止旧命令在群里流传半年后还在装过期版本。

## 文档

- [.claude/CLAUDE.md](.claude/CLAUDE.md) —— 工程宪法:技术栈、目录、TDD、质量防线、安全底线、踩坑点
- [backend/CLAUDE.md](backend/CLAUDE.md) —— 后端细则:轻量 DDD 边界、MyBatis、Flyway、日志规范、ArchUnit
- [frontend/CLAUDE.md](frontend/CLAUDE.md) —— 前端细则:状态三层分工、Tailwind 4、API 层
- [docs/](docs/) —— 设计方案与技术决策记录,归集规范见 [docs/README.md](docs/README.md)
