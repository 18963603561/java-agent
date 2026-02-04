# 配置层快速缓解（阈值、重试、回退）

## 适用位置
- 在 `src/main/resources/application.yml` 的 `agent:` 下新增或修改。
- 如使用环境变量，可按 Spring Boot 规则映射，例如 `AGENT_CIRCUIT_FAILURE_THRESHOLD`。

## 熔断阈值与窗口
```yaml
agent:
  circuit:
    # 失败阈值，达到后打开熔断
    failure-threshold: 10
    # 熔断打开时长（秒）
    open-seconds: 60
```
说明：
- `failure-threshold` 对应 `agent.circuit.failure-threshold`（默认 3）。
- `open-seconds` 对应 `agent.circuit.open-seconds`（默认 30）。

## 重试次数（避免单次请求放大失败计数）
```yaml
agent:
  llm-step:
    tool-retry:
      # LLM 步骤内工具重试次数
      max-attempts: 1
      base-delay-ms: 100
      max-delay-ms: 1000
      jitter-ratio: 0.2
  mcp:
    retry:
      # MCP 远端调用重试次数
      max-attempts: 1
      base-delay-ms: 100
      max-delay-ms: 1000
      jitter-ratio: 0.2
```
说明：
- `agent.llm-step.tool-retry.max-attempts` 默认 2。
- `agent.mcp.retry.max-attempts` 默认 2。
- 设为 1 表示只尝试一次，不再重试。

## 回退策略（远端失败时走本地）
```yaml
agent:
  mcp:
    # 远端失败后回退本地
    fallback-to-local: true
    # 调用策略：remote-first / local-first / remote-only / local-only
    call-strategy: local-first
    # 远端开关
    remote-enabled: true
```
说明：
- `fallback-to-local=true` 仅在本地存在工具时生效。
- `call-strategy=local-first` 可优先本地，远端仅作兜底。
- 如需临时绕开远端，可使用 `call-strategy=local-only`。

## 配置生效建议
- 修改后重启服务，确保配置载入。
- 若依旧触发熔断，建议先降低重试次数，再调整阈值。