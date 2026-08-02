# backend/CLAUDE.md(后端细则)

上层规则见根 `.claude/CLAUDE.md`,本文件只讲后端。中文,禁 emoji。

栈:**JDK 21 + Spring Boot 4.1.x + MyBatis 4.0.1 + Flyway + PostgreSQL**。基础包 `com.landray.skillhub`。

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

**这些规矩由 ArchUnit 测试强制执行,不靠自觉**(见第七节)。文字约束拦不住"某天有人图省事在 Controller 里直接注入 Mapper",一条会失败的测试可以。

## 二、"轻量"体现在哪(三条取舍,不许自行加码)

1. **不强制聚合根 / 领域事件 / CQRS**。YAGNI,业务复杂到真需要时再引,并在 `docs/` 记决策。
2. **简单 CRUD 允许 `application` 直接调仓储**,不为了"符合 DDD"造一个只有一行转发的领域服务。
3. **不搞 DTO/DO/PO 三层转换**。只保留两层:`interfaces` 的 DTO + `domain` 的领域模型。只有当表结构确实跟领域模型对不上(字段拆分、历史遗留列)时,才在 `infrastructure/persistence` 里引入 PO + Converter。三层转换在这个体量的项目里纯属自我消耗。

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

## 六、加一个新实体的标准动作(以 Foo 为例)

1. `resources/db/migration/V000N__create_foo.sql` 建表 + 索引 + 注释。
2. `domain/model/Foo.java` 领域模型(纯 Java)。
3. `domain/repository/FooRepository.java` 仓储接口(纯接口)。
4. `infrastructure/persistence/mapper/FooMapper.java` + `resources/mapper/FooMapper.xml`。
5. `infrastructure/persistence/FooRepositoryImpl.java` 实现仓储接口。
6. `application/FooApplicationService.java` 用例编排 + `@Transactional`。
7. `interfaces/rest/FooController.java` + Request/Response DTO。
8. 每一步都**先写失败测试**,见下。

## 七、测试(TDD)

三个维度各管各的,别混为一谈:

| 工具 | 回答的问题 |
| --- | --- |
| JUnit 5 + Mockito | 行为对不对 |
| JaCoCo | 测到了多少(闸门 >= 80%) |
| ArchUnit | 结构有没有烂掉 |

覆盖率高不代表架构没腐化,架构干净也不代表行为正确。

### 7.1 分层测试策略

分层对应分层,这是 DDD 分层最实在的回报:

- **domain**:纯单测,不起 Spring,不碰 DB。领域规则(kebab-case 校验、SemVer 比较、路径安全判断)全在这层测,又快又稳,是覆盖率主力。
- **application**:Mockito mock 掉仓储接口,只验编排逻辑与事务语义。
- **infrastructure/persistence**:**Testcontainers 起真 PostgreSQL** 测 Mapper,不用 H2 冒充(方言差异会让测试骗人)。
- **interfaces/rest**:`@WebMvcTest` + MockMvc,mock 掉 application 层,只验参数绑定、状态码、错误映射。

命名 `should_<行为>_when_<前置>`,一个测试只验一个行为,不 mock 被测对象自身。

### 7.2 架构约束测试(ArchUnit)

放 `src/test/java/**/architecture/`,依赖 `com.tngtech.archunit:archunit-junit5`。把第一节那些规矩变成会失败的测试:

1. `domain` 不许依赖 `application` / `infrastructure` / `interfaces`。
2. `domain` 不许依赖 `org.springframework..` / `org.apache.ibatis..` / `tools.jackson..`,保证它是纯 Java。
3. `@Transactional` 只许声明在 `..application..` 包内。
4. 名字以 `Mapper` 结尾的类型只许被 `..infrastructure..` 依赖。
5. 整体 `layeredArchitecture()`:`Interfaces` 不被任何层访问,`Application` 只被 `Interfaces` 访问,`Infrastructure` 不被任何层访问。

**新增一条架构约定时,同步加一条 ArchUnit 规则**,否则那条约定三个月后必然失效。

### 7.3 安全用例

涉及上传、解包、落盘的代码,只写正常路径不算写完。zip-slip、zip 炸弹、缺 `SKILL.md`、frontmatter 非法、sha256 不匹配、绕过前端直接打 API 上传恶意包 —— 这些失败用例必须有。

## 八、收尾自查(每次改完必跑,全绿才算完成)

```
cd backend && mvn verify
```

Spotless 格式 + 编译 + JUnit + JaCoCo(>= 80%)。确属基础设施、测不到的代码用 JaCoCo 排除规则精确排除并注明理由,不许整包关闭。
