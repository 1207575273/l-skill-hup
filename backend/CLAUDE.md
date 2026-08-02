# backend/CLAUDE.md(后端细则)

上层规则见根 `.claude/CLAUDE.md`,本文件只讲后端。中文,禁 emoji。

栈:**JDK 21 + Spring Boot 4.1.0 + mybatis-spring-boot-starter 4.1.0 + Flyway + PostgreSQL**。基础包 `com.landray.skillhub`。

## 一、轻量 DDD 分层

```
com.landray.skillhub/
├── interfaces/rest/        Controller + Request/Response DTO + @RestControllerAdvice
├── application/            用例编排、事务边界(@Transactional 只出现在这一层)
├── domain/
│   ├── model/              实体、值对象
│   ├── service/            领域服务(只在业务确实跨实体时才建)
│   └── repository/         仓储接口,只有接口
├── infrastructure/
│   ├── persistence/        MyBatis Mapper 接口 + PO + Converter,实现 domain 的仓储接口
│   ├── storage/            ArtifactStorage 的本地文件夹 / MinIO 实现
│   └── config/             Spring 配置类
└── shared/                 通用异常、常量、工具
```

**依赖方向单向,唯一不许破的规矩**:

```
interfaces -> application -> domain <- infrastructure
```

- `domain` **不依赖任何外层,不出现任何框架注解**(没有 `@Component`、没有 `@Entity`、没有 MyBatis 注解)。纯 Java,能脱离 Spring 单独跑单测。
- `infrastructure` 依赖 `domain`(实现它定义的仓储接口),反向不成立。
- `interfaces` 不许直接调 `infrastructure`,也不许直接调 `domain.repository`。

**这些规矩由 ArchUnit 测试强制执行,不靠自觉**(见 8.2)。文字约束拦不住"某天有人图省事在 Controller 里直接注入 Mapper",一条会失败的测试可以。

## 二、"轻量"到底轻在哪(黑名单,不许自行加码)

**只保留四样东西,这就是本项目 DDD 的全部**:

1. 分层与**依赖方向单向**(第一节)。
2. 领域模型**带业务规则**,不是一袋 getter/setter 的贫血对象。
3. 仓储**接口在 `domain`、实现在 `infrastructure`**。
4. **事务边界只在 `application`**。

**以下战术模式一律不做。有人提议时直接指这一节,不重新讨论**:

| 不做 | 理由 |
| --- | --- |
| 聚合根 / `AggregateRoot` 基类 / 聚合边界一致性 | 这个业务里没有需要靠聚合守护的跨实体不变量 |
| 领域事件 / 事件总线 / 最终一致性 | 单体单库,一个事务就够,引事件只是给自己加异步 bug |
| CQRS、读写模型分离、Command/Query Handler | 读写量级差着数量级才有价值,这里没有 |
| Specification 规约模式 | 查询条件用 MyBatis 动态 SQL 表达,更直白 |
| `Factory` 工厂类 | 静态工厂方法或构造器足够 |
| 仓储泛型基类 `Repository<T, ID>` | 抽象出来省不了几行,却让每次改动都要读两层 |
| 满天飞的 ID 包装类(`SkillId` / `VersionId` / `BundleId`) | 值对象只给**真正带业务规则**的概念做,例如 `SkillName`(字符集限制是安全边界)。裸 `Long` 不丢人 |
| DTO / DO / PO 三层转换 | 只保留两层:`interfaces` 的 DTO + `domain` 的领域模型。只有表结构确实跟领域模型对不上(字段拆分、历史遗留列)时,才在 `infrastructure/persistence` 加 PO + Converter |
| 六边形架构的完整仪式(`XxxPort` / `XxxAdapter` 命名后缀) | 分层已经表达了同一件事,再套一层命名只是仪式感 |
| 防腐层 ACL | 没有外部系统要防 |

**简单 CRUD 允许 `application` 直接调仓储**,不为了"符合 DDD"造一个只有一行转发的领域服务。

**判定标准**:想引入任何一个 DDD 概念,先回答一句 —— **"不引它,现在具体哪里痛?"** 答不上来就不引。真要引,必须在 `docs/` 留一份决策记录写清痛点,不许悄悄加。

## 三、MyBatis 规约

- **禁用 MyBatis-Plus**。不引、不讨论。
- Mapper 接口放 `infrastructure/persistence/mapper/`,SQL 写在 `resources/mapper/*.xml`。
- 注解 SQL(`@Select` 等)只允许用于单行无条件的简单语句;**带条件、带 join、带动态拼接的一律进 XML**。
- Mapper 接口是基础设施细节,**不许泄漏到 `application` 或 `interfaces`**;它们只认 `domain.repository` 的接口。
- 动态 SQL 用 `<where>` / `<if>` / `<foreach>`,禁止字符串拼接 SQL。
- 分页统一走 `LIMIT/OFFSET` + 独立 count 语句,不引分页插件。

## 四、Flyway 规约

- 迁移脚本放 `resources/db/migration/`,命名 `V<序号>__<描述>.sql`,序号四位递增(`V0001__init_schema.sql`)。
- **已执行过的迁移脚本禁止修改**。改错了就再加一个新脚本修正。
- 一个迁移只做一件事,DDL 与数据订正分开。
- 表和字段必须写 `COMMENT`;PostgreSQL 用 `COMMENT ON COLUMN`。
- 建表即建索引,按实际查询场景设计,不无脑加。

## 五、事务、异常、REST

- **事务边界只在 `application` 层**,`@Transactional` 不许出现在 Controller、领域服务、仓储实现上。
- 业务异常在 `domain` / `application` 里抛(继承 `shared` 里的基类,带业务错误码),由 `interfaces/rest` 的 `@RestControllerAdvice` 统一映射成 HTTP 状态码与错误响应体。**禁止在 Controller 里 try-catch 业务异常**。
- **HTTP 方法只用 GET / POST**:查询 `@GetMapping`,一切写操作 `@PostMapping`。更新 `POST /{id}/update`,删除 `POST /{id}/delete`。**禁止 `@PutMapping` / `@PatchMapping` / `@DeleteMapping`**。路径 `/api/v1/**`(实际带 context-path,完整为 `/skillhub/api/v1/**`)。
- 响应体统一结构,错误响应带 `code` / `message` / `traceId`。
- Jackson 是 **3.x**,import 走 `tools.jackson.*`,详见根 CLAUDE.md 踩坑点 8.1。

## 六、日志(双通道,已锁定)

**控制台给人看,文件给机器看。** 这是唯一形态,不再讨论。

| 通道 | 格式 | 谁消费 |
| --- | --- | --- |
| 控制台 stdout | 人类可读单行文本,带颜色、带 traceId | 开发者肉眼 |
| 文件 `logs/skill-hub.jsonl` | **一行一个 JSON(JSONL),ECS 字段规范** | grep / jq / 将来的 ELK、Loki |

实现由 **Spring Boot 4 原生结构化日志**提供(`logging.structured.format.file: ecs`),配置全在 `application.yml`。

- **不引 `logstash-logback-encoder`**,Boot 4 自带的够用。
- **不写 `logback-spring.xml`**。一旦引入 XML,`logging.*` 那批属性大半失效,后来人改配置改不动却找不到原因。能用配置表达的就不要用 XML。
- 用 ECS 而不是自定义字段名:`@timestamp` / `log.level` / `log.logger` / `error.stack_trace` 是行业通用约定,将来接任何日志系统都不用改应用侧。

滚动策略:按天 + 单文件 100MB 双触发,保留 30 天且总量封顶 3GB,滚完 gzip。**两个上限都要设** —— 只设天数挡不住某天被刷爆磁盘,只设总量又可能刚出问题就被滚掉。

### 6.1 门面与声明

SLF4J,固定写法(本项目不引 Lombok,手写):

```java
private static final Logger log = LoggerFactory.getLogger(SkillApplicationService.class);
```

字段名固定 `log`,`private static final`,类型 `org.slf4j.Logger`。由 ArchUnit 强制。

### 6.2 级别语义(判定标准,不靠感觉)

| 级别 | 判定 | 谁看 |
| --- | --- | --- |
| ERROR | 已经影响到用户或数据,**需要人介入** | 值班,配告警 |
| WARN | 出了异常但已被重试/降级兜住,不用立刻介入,但要看趋势 | 定期巡检 |
| INFO | 系统状态变更 + 关键业务事件,**量必须可控** | 事后追溯 |
| DEBUG | 排查用的中间态,dev 开 prod 关 | 开发 |
| TRACE | 本项目不用 | - |

口诀:**半夜会不会因为它把人叫起来?会 -> ERROR;不会但明天得有人看 -> WARN;只是记录发生过 -> INFO。**

最常见的三个误判,写死:

- **参数校验失败是 WARN,不是 ERROR** —— 那是用户输错了,不是系统坏了。
- **业务异常被 `@RestControllerAdvice` 正常映射成 4xx,不打 ERROR**。ERROR 打滥了,真出事时告警会被淹没。
- **未预期异常 / 5xx 必须 ERROR 且带栈**,`log.error("...", e)` 的 `e` 不能省。

### 6.3 写法硬规矩

1. **参数化占位**:`log.info("skill 上传完成, name={}, size={}", name, size)`。**禁字符串拼接**、禁 `String.format`。
2. **异常放最后一个参数,不占位符**:`log.error("解包失败, skillName={}", name, e)`。写成 `{}` 传 `e` 会丢栈。
3. **禁 `System.out` / `System.err` / `e.printStackTrace()` / `java.util.logging`**。由 ArchUnit 强制。
4. **禁止打整个对象**。`toString()` 不可控,可能巨大、可能含敏感字段。要什么字段打什么字段。
5. **昂贵拼装用 `log.isDebugEnabled()` 守卫**。占位符本身不昂贵,不用守;守的是拼装参数时要做的计算(遍历、序列化)。
6. **循环里不打 INFO**。要打就在循环外打一条汇总(`处理完成, total={}, failed={}`)。
7. **一个异常只记一次**。catch 里要么打日志、要么原样抛给上层,**不许又打又抛** —— 那会在日志里留下同一个栈的多份副本。
8. **日志表达式不许有副作用**,不许在参数里调会改状态的方法。
9. 消息文本用中文没问题,但**变量不许拼进文本**,一律走占位符字段化 —— 否则 JSONL 那侧没法按字段聚合。

### 6.4 traceId 与 MDC

- **每个请求一个 traceId**,由最外层 `TraceIdFilter` 放进 MDC,`finally` 里 **`MDC.clear()`**。不清会随线程池复用串到下一个请求上,这类串号极难排查。
- 优先复用上游传入的 `X-Trace-Id` 请求头,没有就生成;**响应头一律回写 `X-Trace-Id`**,用户报障时直接把它贴过来。
- 错误响应体带 `traceId`(见第五节)。
- **异步 / 线程池里必须显式传递 MDC**(`MDC.getCopyOfContextMap()` 传进去,任务开头 `setContextMap`),否则子线程日志没有 traceId。

MDC key 用 lowerCamelCase,**固定这几个,新增要在这张表登记**,避免同一含义出现两个名字:

`traceId` `event` `operator` `clientIp` `skillName` `skillVersion` `durationMs` `artifactSha256`

### 6.5 脱敏与日志注入

**绝不落盘**:密码、数据库连接串中的密码、`install_bundle` token、`Authorization` 头、Cookie、任何凭据。

- token / 密钥类只打**前 8 位 + 长度**,例如 `token=a1b2c3d4...(len=64)`。
- **日志注入**:用户可控字符串(skill 名、文件名、上传的描述)进日志前必须去掉 `\r` `\n` 和控制字符,否则能伪造出整行假日志。JSONL 通道因为 JSON 转义天然免疫,**控制台通道不免疫** —— 所以清洗要在代码里做,不能指望 encoder。
- 清洗统一走 `shared` 里的工具方法,不要每处自己写。

### 6.6 这个项目必须打的日志

- **启动时一条**:生效 profile、端口、context-path、DB 地址(**不含密码**)、制品根目录。排障第一句话就是"你连的哪个库",让它自己说出来。
- **每个请求一条 access 日志**:`method` `path` `status` `durationMs` `traceId` `clientIp`。**健康检查与静态资源要过滤掉**,否则真实请求会被淹没。
- **关键业务事件**,固定 logger name `skillhub.audit`,带 `event` 字段:skill 上传、版本发布、`install_bundle` 生成、制品下载。现在不单独做审计系统,**将来要审计直接从 JSONL 捞** —— 这是轻量方案能成立的前提,所以这些事件的字段现在就得打全。
- **安全事件一律 WARN 起步**:zip-slip 命中、超解压上限、缺 `SKILL.md`、frontmatter 非法、sha256 不匹配、token 过期或无效、skill 名非法。这些是**根 CLAUDE.md 第七节安全底线**的可观测面,漏打等于安全措施没生效也没人知道。
- **慢操作耗时**:解包、存储读写,统一用 `durationMs` 字段。

**不许打的**:每次 DB 查询一条(MyBatis SQL 日志 dev 开 prod 关)、健康检查的 access 日志、正常路径上的 DEBUG 刷屏。

### 6.7 验收

改完日志相关代码后,起一次应用,确认两件事:

1. 控制台是人能读的单行,traceId 在,同一请求的多条日志 traceId 一致。
2. `logs/skill-hub.jsonl` **每一行都是合法 JSON**:`cat logs/skill-hub.jsonl | jq -c . > /dev/null` 不报错。

## 七、加一个新实体的标准动作(以 Foo 为例)

1. `resources/db/migration/V000N__create_foo.sql` 建表 + 索引 + 注释。
2. `domain/model/Foo.java` 领域模型(纯 Java)。
3. `domain/repository/FooRepository.java` 仓储接口(纯接口)。
4. `infrastructure/persistence/mapper/FooMapper.java` + `resources/mapper/FooMapper.xml`。
5. `infrastructure/persistence/FooRepositoryImpl.java` 实现仓储接口。
6. `application/FooApplicationService.java` 用例编排 + `@Transactional`。
7. `interfaces/rest/FooController.java` + Request/Response DTO。
8. 每一步都**先写失败测试**,见下。

## 八、测试(TDD)

三个维度各管各的,别混为一谈:

| 工具 | 回答的问题 |
| --- | --- |
| JUnit 5 + Mockito | 行为对不对 |
| JaCoCo | 测到了多少(闸门 >= 80%) |
| ArchUnit | 结构有没有烂掉 |

覆盖率高不代表架构没腐化,架构干净也不代表行为正确。

### 8.1 分层测试策略

分层对应分层,这是 DDD 分层最实在的回报:

- **domain**:纯单测,不起 Spring,不碰 DB。领域规则(kebab-case 校验、SemVer 比较、路径安全判断)全在这层测,又快又稳,是覆盖率主力。
- **application**:Mockito mock 掉仓储接口,只验编排逻辑与事务语义。
- **infrastructure/persistence**:**Testcontainers 起真 PostgreSQL** 测 Mapper,不用 H2 冒充(方言差异会让测试骗人)。
- **interfaces/rest**:`@WebMvcTest` + MockMvc,mock 掉 application 层,只验参数绑定、状态码、错误映射。

命名 `should_<行为>_when_<前置>`,一个测试只验一个行为,不 mock 被测对象自身。

### 8.2 架构约束测试(ArchUnit)

放 `src/test/java/**/architecture/`,依赖 `com.tngtech.archunit:archunit-junit5`。把前面那些规矩变成会失败的测试:

**分层**(对应第一节):

1. `domain` 不许依赖 `application` / `infrastructure` / `interfaces`。
2. `domain` 不许依赖 `org.springframework..` / `org.apache.ibatis..` / `tools.jackson..`,保证它是纯 Java。
3. `@Transactional` 只许声明在 `..application..` 包内。
4. 名字以 `Mapper` 结尾的类型只许被 `..infrastructure..` 依赖。
5. 整体 `layeredArchitecture()`:`Interfaces` 不被任何层访问,`Application` 只被 `Interfaces` 访问,`Infrastructure` 不被任何层访问。

**日志**(对应第六节。这几条最容易在赶进度时破防,靠 review 拦不住):

6. 禁止访问 `System.out` / `System.err`(`GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS`)。
7. 禁止调用 `Throwable.printStackTrace()`。
8. 禁止使用 `java.util.logging`(`NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING`)。
9. `org.slf4j.Logger` 类型的字段必须是 `private static final` 且命名为 `log`。

**新增一条架构约定时,同步加一条 ArchUnit 规则**,否则那条约定三个月后必然失效。

### 8.3 安全用例

涉及上传、解包、落盘的代码,只写正常路径不算写完。zip-slip、zip 炸弹、缺 `SKILL.md`、frontmatter 非法、sha256 不匹配、绕过前端直接打 API 上传恶意包 —— 这些失败用例必须有。

## 九、收尾自查(每次改完必跑,全绿才算完成)

```
cd backend && mvn verify
```

Spotless 格式 + 编译 + JUnit + ArchUnit + JaCoCo(>= 80%)。确属基础设施、测不到的代码用 JaCoCo 排除规则精确排除并注明理由,不许整包关闭。

**行尾报错先看 `.gitattributes`,不要去关 Spotless**。Spotless 报一大片 diff 但两边内容一模一样时,那是 CRLF/LF 的问题,`mvn spotless:apply` 一次性归正即可。详见根 CLAUDE.md 踩坑点 8.8。

### 9.1 `.mvn/` 里的项目级 Maven 配置

`backend/.mvn/` 下两个文件,在本目录跑任何 `mvn` 命令时自动生效,**不用手敲参数**:

| 文件 | 作用 |
| --- | --- |
| `maven.config` | 自动追加 `-s .mvn/settings.xml` 与 `-Dmaven.legacyLocalRepo=true` |
| `settings.xml` | 一条 `mirrorOf=*` 的阿里云镜像,把所有仓库请求导向公网 |

**为什么要有**:开发机全局 `settings.xml` 里的 `landray-nexus` profile 把公司内网私库排在 central 前面,而那台机器在当前网络连不通,每个依赖先撞一次 10s 超时。实测 `mvn verify` 从约 10 分钟降到 **17 秒**。

**刻意不写 `localRepository`**:`-s` 指定的是**用户级** settings,全局 settings 仍会合并且用户级优先;`localRepository` 恰好定义在全局那份里,留空即可继承。这样仓库里不出现任何人的私人磁盘路径,团队各用各的本地仓库。**凭据同理,一律不进仓库。**

`maven.config` 有两个反直觉的地方,改之前先知道:**一行只能放一个参数**(`-s xxx` 写一行会被当成一个带空格的怪值),**不支持 `${maven.projectBasedir}` 展开**(但相对路径本来就按项目根目录解析,直接写 `.mvn/settings.xml` 即可)。
