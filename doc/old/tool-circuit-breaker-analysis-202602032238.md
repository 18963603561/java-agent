# 熔断开启原因分析报告（2026-02-03 22:38）

## 现象
```
toolStatus:FAILED
toolErrorCode:TOOL_UNAVAILABLE
confidence:0.4
toolErrorMessage:熔断已开启
outputDigest:...
```

## 结论摘要
- 熔断由 `McpToolClient#callToolRemoteWithRetry` 中的 `CircuitBreakerManager#allow` 直接拒绝触发。
- `toolErrorCode` 显示为 `TOOL_UNAVAILABLE`，是 `LlmStepService#mapToolErrorCode` 将 `CIRCUIT_OPEN` 映射后的结果。
- 并发大小不是触发条件，失败计数和内部重试更容易导致单次请求触发熔断。

## 关键代码流程
1. `LlmStepService` 发起工具调用，底层通过 `EnforcementGateway` 进入工具执行链路。
2. `McpToolClient#callTool` 计算 `rateKey = tenantId + ":" + toolName` 并选择远端调用路径。
3. `McpToolClient#callToolRemoteWithRetry` 调用前执行 `CircuitBreakerManager#allow(rateKey)`：
   - 返回 `false` 时直接抛出 `ErrorCodeException`。
   - 抛出的错误码为 `CIRCUIT_OPEN`，错误信息为“熔断已开启”。
4. `LlmStepService#mapToolErrorCode` 将 `CIRCUIT_OPEN` 映射为 `TOOL_UNAVAILABLE`，写入工具错误上下文。

## 错误码映射说明
```
CIRCUIT_OPEN
```
中文解释：熔断已开启，由熔断器拒绝本次请求。
```
TOOL_UNAVAILABLE
```
中文解释：运行时对 `CIRCUIT_OPEN` 与 `MCP_UNAVAILABLE` 的统一抽象错误码。

## 为什么在“只有 1 个请求”也会触发
1. 失败计数为进程级累积，并不会自动衰减或过期重置。
   - `CircuitBreakerManager` 只有在调用成功时才会清零失败次数。
2. 单次请求存在多层重试，失败次数会被快速累计。
   - 外层：`LlmStepService` 的工具重试（默认 2 次）。
   - 内层：`McpToolClient` 的远端重试（默认 2 次）。
   - 组合后单个请求最多触发 4 次失败记录，超过默认阈值 3。
3. 熔断后仍会继续计失败，导致熔断窗口被延长。
   - 当 `allow` 返回 `false` 时，后续仍会执行 `recordFailure`，并刷新 `openedAt`。
   - 这会让每次被拒绝的请求都延长熔断持续时间。
4. 熔断维度为 `tenantId + toolName`，未区分 `serverId`。
   - 某个远端服务异常可能导致同名工具在所有服务端一起被熔断。

## outputDigest 与熔断的关系
- `outputDigest` 来自 `StepOutputSummaryBuilder` 对输出结果的摘要封装，不参与熔断判断。
- 熔断发生在工具调用前置的 `CircuitBreakerManager#allow` 阶段，`outputDigest` 仅是失败结果的后续表现。

## 解决建议
### 立即缓解
- 等待 `agent.circuit.open-seconds` 到期并确保下一次调用成功，让熔断计数被清零。
- 远端服务恢复后再调用，避免失败继续累积。

### 配置层调整
- 调高失败阈值：`agent.circuit.failure-threshold`。
- 调短或调长熔断时间：`agent.circuit.open-seconds`。
- 适当降低重试次数：`agent.llm-step.tool-retry.max-attempts`、`agent.mcp.retry.max-attempts`。
- 具备本地工具时，可开启回退：`agent.mcp.fallback-to-local=true`。

### 代码层修复建议
1. 熔断已开启时不再累计失败，避免续租式延长：
   - 在 `callToolRemoteWithRetry` 中对 `allow=false` 的情况不调用 `recordFailure`。
2. 引入半开状态与探测机制：
   - 熔断超时后只放行一次探测请求，成功即关闭，失败才重新打开。
3. 失败计数增加衰减或按时间窗口重置：
   - 例如超过 `openSeconds` 后清零失败次数。
4. 熔断维度包含 `serverId`：
   - 让同名工具在不同远端隔离熔断。
5. 完善可观测性：
   - 在熔断开启与关闭时记录日志或事件（可复用 `EventType.CIRCUIT_OPENED`）。

## 验证思路
- 先将远端服务恢复为正常，确保一次成功调用能清零失败。
- 人为制造 3 次失败，观察熔断是否按配置时间打开与自动恢复。
- 调整重试次数后，验证单次请求是否仍能触发熔断。