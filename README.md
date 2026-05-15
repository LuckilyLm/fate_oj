# FATE OJ

FATE OJ 是一个基于 Spring Cloud 微服务、RabbitMQ、Vue 3 和独立代码沙箱的在线判题系统。当前重点已从教学版 `Runtime.exec` / `docker-java` 沙箱，升级为更接近真实 OJ 的 Linux judge worker：沙箱服务通过 HTTP 接收判题请求，在容器内使用 `isolate` 编译和运行用户代码。

## 模块划分

| 模块 | 说明 |
| --- | --- |
| `fate_oj-backend-microservice` | Spring Cloud 微服务后端，包含用户、题目、判题、网关、公共模块 |
| `fate_oj-code-sandbox` | 独立代码沙箱服务，当前使用 Linux `isolate` 执行用户代码 |
| `fate_oj-frontend` | Vue 3 + TypeScript 前端 |
| `fate_oj` | 早期 Spring Boot 单体版本 |

## 技术栈

- 后端：Java 8、Spring Boot、Spring Cloud、Spring Cloud Alibaba、OpenFeign、Gateway
- 存储与中间件：MySQL、Redis、RabbitMQ、Nacos
- ORM 与工具：MyBatis-Plus、Knife4j / Swagger、Hutool
- 前端：Vue 3、TypeScript、Arco Design、Monaco Editor、OpenAPI 生成客户端
- 沙箱：Docker Linux worker、`isolate`、OpenJDK、G++

## 判题链路

1. 用户在前端提交代码。
2. `question-service` 校验题目、语言和提交内容，写入 `question_submit`。
3. `question-service` 发送 RabbitMQ 判题消息。
4. `judge-service` 消费消息，读取题目 `judgeCase` 和 `judgeConfig`。
5. `judge-service` 构造 `ExecuteRequest`，把 `timeLimit`、`memoryLimit`、`stackLimit` 传给沙箱。
6. `code-sandbox` 使用 `isolate` 编译并逐个测试用例运行代码。
7. `judge-service` 根据信息更新提交状态和题目通过数。

## 沙箱能力

当前沙箱第一版只开放：

- `java`：写入 `Main.java`，使用 `javac` 编译，使用 `java Main` 运行。
- `c++` / `cpp`：写入 `Main.cpp`，使用 `g++ -std=c++17 -O2 -pipe -static -s` 编译，运行生成的二进制。

沙箱限制包括：

- CPU 时间、真实时间、内存、栈、进程数
- 输出大小、文件大小
- 每次提交独立临时目录
- 每个测试用例独立执行
- 默认不提供网络能力
- 旧 `/executeCode/docker` 保留兼容，但内部已改为 `isolate`
- 旧 `/executeCode/native` 和 `/executeCode/remote` 已禁用

## 沙箱镜像构建

在 `fate_oj-code-sandbox` 目录执行：

```bash
docker build -t fate-oj-code-sandbox:local .
```

在 Linux Docker judge worker 上推荐以内网方式运行：

```bash
docker run -d \
  --name fate-oj-code-sandbox \
  --privileged \
  --cgroupns=host \
  -v /sys/fs/cgroup:/sys/fs/cgroup:rw \
  -p 8090:8090 \
  -e SANDBOX_AUTH_SECRET=fate \
  fate-oj-code-sandbox:local
```

说明：`isolate --cg` 对 Linux cgroup 权限有要求。Windows Docker Desktop 可以构建镜像并验证 `isolate` 二进制，但通常无法完整验证真实判题执行，最终应在 Linux Docker 环境中验收。

## 关键配置

`judge-service`：

```yaml
codesandbox:
  type: remote
  url: http://localhost:8090
  auth-secret: fate
```

`code-sandbox`：

```yaml
sandbox:
  auth-header: auth
  auth-secret: fate
  default-time-limit: 3000
  default-memory-limit: 262144
  default-stack-limit: 65536
  compile-time-limit: 10000
  compile-memory-limit: 524288
  max-output-bytes: 1048576
  max-processes: 32
  max-file-size: 65536
```

## 本地验证

```bash
cd fate_oj-backend-microservice
mvn -q -DskipTests compile

cd ../fate_oj-code-sandbox
mvn -q -DskipTests compile

docker build -t fate-oj-code-sandbox:local .
docker run --rm --privileged --entrypoint isolate fate-oj-code-sandbox:local --version
```

更多改造进度见 [SANDBOX_PROGRESS.md](./SANDBOX_PROGRESS.md)。
