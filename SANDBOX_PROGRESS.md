# FATE OJ 沙箱改造进度

更新时间：2026-05-15

## 当前状态

本轮已把代码沙箱从教学版执行方式改造成独立 Linux judge worker 方向。后端仍保持 `judge-service -> code-sandbox` 的 HTTP 调用形态，降低业务侧改造成本；沙箱服务内部改为基于 `isolate` 编译和运行代码。

## 已完成

- `ExecuteRequest` 增加 `timeLimit`、`memoryLimit`、`stackLimit`，兼容原有 `code`、`inputList`、`language` 字段。
- `judge-service` 从题目 `judgeConfig` 读取资源限制，并传给沙箱。
- 判题策略优先信任沙箱返回的非 AC 状态，避免超时、超内存、编译错误被后续输出比对覆盖。
- 移除 Java 固定时间补偿逻辑，不再使用硬编码 Java 时间偏移。
- `question-service` 暂时只开放 Java 和 C++ 提交。
- 沙箱服务新增 `LanguageExecutor` 抽象，并实现 Java、C++ 执行器。
- 沙箱服务新增 `IsolateSandboxExecutor`：
  - 编译和运行都在 `isolate` box 内完成。
  - 每次提交创建独立临时目录。
  - 每个测试用例独立运行。
  - 收集 stdout、stderr、exitCode、time、memory 和 isolate meta。
  - 支持编译错误、运行错误、超时、超内存、输出超限、系统错误等状态。
- `/executeCode` 作为新推荐入口。
- `/executeCode/docker` 保留兼容旧调用，但内部已切换为 `isolate`。
- `/executeCode/native` 和 `/executeCode/remote` 已禁用，避免继续执行不安全路径。
- 沙箱鉴权从硬编码 `auth: fate` 改为配置项。
- 新增 `fate_oj-code-sandbox/Dockerfile`，镜像内安装 OpenJDK、G++ 和从源码构建的 `isolate`。
- 新增 `docker-entrypoint.sh`，Linux worker 启动时自动拉起 `isolate-cg-keeper`。
- `.gitignore` 增加 Maven `target/` 和沙箱临时目录忽略规则。

## 已验证

在当前 Windows + Docker Desktop 环境完成：

```bash
cd fate_oj-backend-microservice
mvn -q -DskipTests compile
```

结果：通过。

```bash
cd fate_oj-code-sandbox
mvn -q -DskipTests compile
```

结果：通过。

```bash
cd fate_oj-code-sandbox
docker build -t fate-oj-code-sandbox:local .
```

结果：通过。

```bash
docker run --rm --privileged --entrypoint isolate fate-oj-code-sandbox:local --version
```

结果：通过，镜像内 `isolate` 版本为 2.5。

## 当前限制

Windows Docker Desktop 下可以完成镜像构建和 `isolate` 二进制验证，但无法完整验证 `isolate --cg --init` 的真实判题执行。测试时即使使用：

```bash
--privileged --cgroupns=host -v /sys/fs/cgroup:/sys/fs/cgroup:rw
```

仍会遇到 cgroup 写入限制，例如：

```text
Cannot write to .../cgroup.subtree_control: Device or resource busy
```

这符合本轮设计假设：真实沙箱第一版以 Linux Docker judge worker 为目标，不兼容 Windows 本地完整执行。

## Linux 验收建议

在 Linux Docker 环境执行：

```bash
docker build -t fate-oj-code-sandbox:local fate_oj-code-sandbox
```

启动沙箱：

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

最小 Java 请求：

```bash
curl -X POST http://localhost:8090/executeCode \
  -H 'Content-Type: application/json' \
  -H 'auth: fate' \
  -d '{"language":"java","code":"import java.util.*; public class Main { public static void main(String[] args) { Scanner sc = new Scanner(System.in); System.out.println(sc.nextInt() + sc.nextInt()); } }","inputList":["1 2"],"timeLimit":3000,"memoryLimit":262144,"stackLimit":65536}'
```

期望返回：

```json
{
  "outputList": ["3"],
  "message": "Accepted",
  "status": 1
}
```

## 后续待办

- 在 Linux worker 上补齐 Java/C++ 的 AC、WA、CE、RE、TLE、MLE、输出超限测试。
- 增加安全测试：网络访问、读取敏感路径、fork bomb、死循环。
- 为 `IsolateSandboxExecutor` 增加更细的单元测试和 meta 解析测试。
- 接入正式部署编排，确保沙箱只在内网访问。
- 将 `SANDBOX_AUTH_SECRET` 改为部署环境变量或密钥管理，不在生产配置中使用默认值。
- 根据真实 Linux 环境决定是否固定 `--cg`，或增加非 cgroup 降级模式用于开发环境。
