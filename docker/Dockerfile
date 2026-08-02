# skill-hub 生产镜像:三阶段构建,宿主机不需要装 JDK / Node / Maven。
#
# 【约束】本文件必须能被**经典构建器**构建,不许用 BuildKit 专属语法
# (`RUN --mount=type=cache/secret/bind`、heredoc 等)。
# 本机 docker CLI 是手工装的静态二进制,只装了 compose 插件、没装 buildx,
# `docker compose build` 会回落到经典构建器,用了就报
# "the --mount option requires BuildKit"。详见根 CLAUDE.md 踩坑点 8.6。
# 多阶段(`COPY --from`)经典构建器是支持的,放心用。
#
#   frontend(node) -> 产物 dist/
#        |
#        v  拷进 backend/src/main/resources/static/
#   backend(maven) -> 可执行 jar
#        |
#        v
#   runtime(jre)   -> 最终镜像
#
# 构建 context 是仓库根目录(compose 里 context: ..),所以路径都从 backend/ frontend/ 起算。
#
# 手动构建:
#   docker build -f docker/Dockerfile -t skillhub/app:dev .

# 镜像 tag 提成 ARG,内网若有私有 registry 直接覆盖,不用改 Dockerfile 正文
ARG NODE_IMAGE=node:22-alpine
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine


# ============================================================
# 阶段一:构建前端
# ============================================================
FROM ${NODE_IMAGE} AS frontend

# 默认走国内镜像源(本项目是内网场景)。走官方源:--build-arg NPM_REGISTRY=https://registry.npmjs.org
ARG NPM_REGISTRY=https://registry.npmmirror.com

WORKDIR /build

# 版本号与 package.json 的 packageManager 字段保持一致,改一处要同步改另一处。
# 不锁的话镜像里可能装到与 pnpm-lock.yaml(lockfileVersion 9.0)不兼容的大版本。
#
# 刻意不用 corepack:node 基础镜像里的 corepack 版本较旧,校验新 pnpm 的签名时
# 会报 "Cannot find matching keyid" 直接构建失败,而这个错的表象和依赖问题毫无关系。
ARG PNPM_VERSION=10.33.0
RUN npm config set registry "${NPM_REGISTRY}" \
    && npm install -g "pnpm@${PNPM_VERSION}" \
    && pnpm config set registry "${NPM_REGISTRY}"

# 先只拷依赖清单:src 改动不会让 install 层失效
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ ./
# build = tsc --noEmit && vite build,类型错误在这里就炸,不会带进镜像
RUN pnpm build


# ============================================================
# 阶段二:构建后端(顺带把前端产物打进 jar)
# ============================================================
FROM ${MAVEN_IMAGE} AS backend

# 同理,走官方 central:--build-arg MAVEN_MIRROR_URL=
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public

WORKDIR /build

# 注意:这里刻意不复用宿主机的 settings.xml —— 宿主上那份指向公司内网仓库,
# 构建容器未必连得通,连不通时每个依赖要等 10s 超时,构建会慢到无法接受。
RUN if [ -n "${MAVEN_MIRROR_URL}" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s' \
        '<settings><mirrors><mirror>' \
        '<id>build-mirror</id><name>build mirror</name>' \
        "<url>${MAVEN_MIRROR_URL}</url>" \
        '<mirrorOf>central</mirrorOf>' \
        '</mirror></mirrors></settings>' > /root/.m2/settings.xml; \
    fi

# 先只拷 pom:业务代码改动不用重下依赖
COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline

COPY backend/src ./src
# 前端产物落到 static/,Spring Boot 直接同源托管,不需要 Nginx
COPY --from=frontend /build/dist ./src/main/resources/static

# 跳测试:单测依赖 Testcontainers 起真 PostgreSQL,构建容器里没有 docker daemon。
# 质量闸门在 `mvn verify`(本机 / CI)那一道,镜像构建不承担这个职责。
# 用 -DskipTests 而不是 -Dmaven.test.skip=true:测试代码仍然编译,
# 加上 validate 阶段的 Spotless 检查,格式与编译问题在这里还能拦一道。
RUN mvn -B -ntp -DskipTests package \
    && cp target/*.jar /build/app.jar


# ============================================================
# 阶段三:运行时
# ============================================================
FROM ${JRE_IMAGE} AS runtime

ARG TZ=Asia/Shanghai
ENV TZ=${TZ}

# tzdata:容器内文件时间戳与日志时间对齐;wget 用于 HEALTHCHECK(busybox 自带)
RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo ${TZ} > /etc/timezone

# 不用 root 跑应用。这个服务会解开用户上传的 zip,权限收得越紧越好。
RUN addgroup -S skillhub && adduser -S -G skillhub -h /app skillhub

WORKDIR /app
COPY --from=backend /build/app.jar /app/app.jar

# 制品目录与日志目录都挂命名卷(见 docker-compose.yml),
# 这里先建出来并交给 skillhub 用户,避免容器以非 root 启动时写不进去。
RUN mkdir -p /app/data/artifacts /app/logs && chown -R skillhub:skillhub /app

USER skillhub

EXPOSE 18080

# 镜像的默认档是 prod —— 它是生产制品,裸 `docker run` 就该按生产语义走。
# prod 的连接信息没有任何默认值,漏配会直接启动失败(响亮地失败,好过
# 悄悄连上一个 dev 默认地址)。dev 档由 compose 的 app-dev 服务显式覆盖。
#
# 【JAVA_OPTS 里为什么没有 --add-opens / --add-exports】
# JDK 17 起强封装默认开启,很多项目习惯性预先加一串 --add-opens。本项目**刻意不加**:
# 实测 Spring Boot 4.1 + JDK 21 这套栈起容器、连库、跑 Flyway、发请求全程无反射告警,
# 加了只会削弱封装,并且把"哪天真出反射问题"的信号提前屏蔽掉 —— 等真报
# InaccessibleObjectException 时再按报错精确加那一条,不要预先撒网。
#   MaxRAMPercentage    容器里按 cgroup 限额取堆,不写死 -Xmx
#   ExitOnOutOfMemoryError  OOM 直接退出让编排重启,好过半死不活地continue
#   HeapDumpOnOutOfMemoryError  退出前先留下堆转储,否则 OOM 现场什么都没有;
#                               路径落在挂了命名卷的 /app/logs,容器重建不丢
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=18080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs"

# 探活走 actuator 而不是首页:首页是静态文件,DB 挂了也照样 200,探不出真问题。
# start-period 给足 60s —— Flyway 迁移在启动时执行,冷启动比想象中慢。
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/skillhub/actuator/health" | grep -q '"status":"UP"' || exit 1

# exec 形式转发信号,容器 stop 时 JVM 能收到 SIGTERM 正常关闭
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
