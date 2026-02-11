# Quickstart: 001-agent-core-spec

**Date**: 2026-01-25

## 运行前准备
- 安装 `Java 17`
- 准备 `PostgreSQL` 与 `Redis`
- 统一配置 `application.yml` 中的数据库、缓存、`SSE` 与预算策略

## 关键配置项
- `server.port`
- `spring.r2dbc.url` 或 `spring.datasource.url`
- `spring.data.redis.host` / `spring.data.redis.port`
- `agent.sse.timeoutSeconds`
- `agent.budget.enabled`
- `agent.runtime.maxIterations`
- `agent.runtime.minIterations`
- `agent.runtime.observationWindow`
- `agent.mcp.allowlist`
- `agent.hook.enabled`
- `agent.policy.opaUrl`
- `agent.sandbox.wasi.enabled`
- `agent.model.fallback.enabled`

说明：
- `agent.runtime.maxIterations` 为 ReAct 最大轮次上限。
- `agent.runtime.minIterations` 为 ReAct 最小轮次下限。
- `agent.runtime.observationWindow` 为观察窗口条数。

## 启动应用
- 使用 `Maven` 启动：`./mvnw spring-boot:run`

## 可执行验证步骤（接口与错误码）

1. 提交任务获取 `workflowId`：
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/tasks \
     -d '{"query":"call_tool_and_fail","sessionId":"s-001","context":{"tool":"demo_tool"},"idempotencyKey":"idem-001"}'
   ```
   说明：`idempotencyKey` 可选，省略或空白时每次提交都会创建新任务；提供时重复提交复用历史任务。
   期望关键字段：`data.taskId`、`data.workflowId`、`data.status`，`workflowId` 取自 `TaskResponse.workflowId`。

2. 建立 `SSE` 订阅（包含 `types` 与断线续传）：
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     -H "Last-Event-ID: <streamId:seq>" \
     "http://localhost:8080/api/v1/stream/sse?workflow_id=<workflowId>&types=WORKFLOW_STARTED,STEP_STARTED,TOOL_INVOKED,TOOL_OBSERVATION,TOOL_ERROR,STEP_FAILED,STEP_COMPLETED,WORKFLOW_COMPLETED&last_event_id=<streamId:seq>"
   ```
   期望关键字段：`id` 为 `eventId`，格式为 `streamId:seq`；`event` 为事件类型；`data` 为 `StreamEvent`。`Last-Event-ID` 优先于 `last_event_id`。

3. 查询步骤时间线 `/api/v1/timeline/steps`：
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     "http://localhost:8080/api/v1/timeline/steps?workflowId=<workflowId>&cursor=<cursor>&size=20"
   ```
   期望关键字段：`data.workflowId`、`data.steps[].stepId`、`data.steps[].status`、`data.steps[].stepSeq`（对应 `seq`）、`data.nextCursor`、`data.hasMore`。`eventId` 从 `SSE` 事件 `id` 获取，用于与 `stepSeq` 对齐。

4. 调用 `/api/v1/mcp/tools/list` 获取工具清单：
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/mcp/tools/list \
     -d '{"serverId":"mcp-default","cursor":"","size":20}'
   ```
   期望关键字段：`data.tools[].name`、`data.tools[].version`、`data.tools[].inputSchema`、`data.tools[].outputSchema`、`data.nextCursor`、`data.hasMore`。
   说明：链路追踪可补充 `X-Trace-Id` 与 `X-Request-Id`。

5. 调用 `/api/v1/mcp/tools/call` 触发工具执行：
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/mcp/tools/call \
     -d '{"callId":"call-001","serverId":"mcp-default","toolName":"demo_tool","arguments":{"text":"ping"},"timeoutMs":3000}'
   ```
   期望关键字段：`data.callId`、`data.status`、`data.result` 或 `data.error`。
   事件：`TOOL_INVOKED` 必须出现；成功出现 `TOOL_OBSERVATION`，失败出现 `TOOL_ERROR`；启用 `Hook` 时记录 `HOOK_PRE_TOOL`、`HOOK_POST_TOOL`。
   失败场景触发 `retry`/`decompose` 时，应看到 `STEP_FAILED` 后再次出现 `STEP_STARTED`，最终以 `STEP_COMPLETED`/`WORKFLOW_COMPLETED` 收敛。

6. 生产治理事件验证（`FR-023`）：
   - 限流治理：构造高频调用触发 `RATE_LIMITED`，期望事件流出现 `BACKPRESSURE_APPLIED` 与 `TOOL_ERROR`。
   - 熔断治理：构造连续失败触发 `CIRCUIT_OPEN`，期望事件流出现 `CIRCUIT_OPENED`、`BACKPRESSURE_APPLIED` 与 `TOOL_ERROR`。
   - 重试退避：构造可重试失败，期望事件流出现 `WAITING`，并验证 payload 包含 `delayMs`、`attempt`、`trigger`。
   - 预算高压：触发预算阈值后，期望至少出现 `BUDGET_THRESHOLD` 与 `BACKPRESSURE_APPLIED`。

7. 调用 `/api/v1/policy/evaluate`（允许与拒绝）：
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/policy/evaluate \
     -d '{"policyId":"default","action":"submit","resource":"task","input":{"risk":"low"}}'
   ```
   期望关键字段：`data.policyId`、`data.decision`、`data.evaluationId`、`data.matchedRules`。
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/policy/evaluate \
     -d '{"policyId":"default","action":"submit","resource":"task","input":{"risk":"high"}}'
   ```
   期望 `HTTP 403`，`ErrorResponse.code` 为 `POLICY_DENIED`（别名 `POLICY_DENY`），响应包含 `message`、`traceId`、`requestId`。

8. 调用 `/api/v1/replay`（成功与不存在）：
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/replay \
     -d '{"taskId":"<taskId>","fromStepId":"","toStepId":"","mode":"full"}'
   ```
   期望关键字段：`data.replayId`、`data.status`、`data.startedAt`、`data.completedAt`，并出现 `REPLAY_STARTED`、`REPLAY_COMPLETED`。
   ```bash
   curl -H "X-API-Key: <key>" \
     -H "X-Tenant-Id: <tenantId>" \
     -H "X-Trace-Id: <traceId>" \
     -H "X-Request-Id: <requestId>" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/replay \
     -d '{"taskId":"task-not-found","fromStepId":"","toStepId":"","mode":"full"}'
   ```
   期望 `HTTP 404`，`ErrorResponse.code` 为 `REPLAY_NOT_FOUND`，响应包含 `message`、`traceId`、`requestId`。

9. 错误场景覆盖（所有错误响应需包含 `code`、`message`、`traceId`、`requestId`）：
   - `TENANT_MISSING`：去掉 `X-Tenant-Id` 调用任一接口，期望 `HTTP 400`，`ErrorResponse.code=TENANT_MISSING`。
   - `AUTH_FAILED`：使用无效 `X-API-Key`，期望 `HTTP 401`，`ErrorResponse.code=UNAUTHORIZED`，审计日志记录 `AUTH_FAILED`。
   - `MCP_UNAVAILABLE`：使用不可用 `serverId` 调用 `/api/v1/mcp/tools/list` 或 `/api/v1/mcp/tools/call`，期望 `HTTP 503`，`ErrorResponse.code=MCP_UNAVAILABLE`。
   - `POLICY_DENY`：触发策略拒绝，期望 `HTTP 403`，`ErrorResponse.code=POLICY_DENIED`（别名 `POLICY_DENY`）。
   - `SANDBOX_DENY`：触发沙箱拒绝的工具调用，期望 `HTTP 403`，`ErrorResponse.code=SANDBOX_DENIED`（别名 `SANDBOX_DENY`）。
   - `HOOK_BLOCKED`：启用阻断型 `Hook` 调用 `/api/v1/mcp/tools/call`，期望 `HTTP 409`，`ErrorResponse.code=HOOK_BLOCKED`。


## ???????Chain-of-Thought
- ?? `context.strategy=chain_of_thought` ?????????????????? `stepType=CHAIN_OF_THOUGHT`
- ?????? `finalAnswer`?`stepsCount`?`confidence`?`stopReason`
- ?????? `COT_STARTED`?`COT_STEP`?`COT_COMPLETED`?`COT_STOPPED`
- ????? `agent.cot.max-steps`?`agent.cot.temperature-override`?`agent.cot.model-hint`?`agent.cot.emit-step-events`
