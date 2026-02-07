# 事件流订阅与事件生产使用说明

## 结论摘要
- `SseStreamController` 是事件消费输出入口，用于订阅服务器发送事件流。
- 事件生产入口不止在 `gateway/controller` 包内，核心生产源在运行时链路与编排服务中。
- `gateway/controller` 中部分接口会直接发布事件，典型为 `TaskController`、`McpController`、`ApprovalController`。

## 事件生产源头分析
### 主要生产链路
1. 任务提交入口
- 文件：`src/main/java/com/example/agent/gateway/controller/TaskController.java`
- 关键链路：`TaskController` -> `TaskOrchestrator` -> `ApplicationEventPublisher`
- 事件类型示例：`WORKFLOW_STARTED`、`WORKFLOW_COMPLETED`、`ERROR_OCCURRED`
- 说明：任务提交后由 `TaskOrchestrator` 在编排阶段发布工作流事件。

2. 运行时与步骤执行
- 文件：`src/main/java/com/example/agent/runtime/AgentRuntime.java`
- 文件：`src/main/java/com/example/agent/runtime/ReactLoopService.java`
- 文件：`src/main/java/com/example/agent/runtime/StepRuntimeService.java`
- 说明：运行时执行过程会持续发布规划、步骤、反思、观察、行动等事件。

3. 工具与审批事件
- 文件：`src/main/java/com/example/agent/agentcore/EnforcementGateway.java`
- 文件：`src/main/java/com/example/agent/gateway/controller/McpController.java`
- 文件：`src/main/java/com/example/agent/gateway/controller/ApprovalController.java`
- 说明：工具调用、观察与审批决策会直接发布事件。

4. 上下文快照事件
- 文件：`src/main/java/com/example/agent/streaming/ContextEventPublisher.java`
- 说明：上下文快照、裁剪、压缩阶段通过专用发布器输出事件。

### 事件发布总线
- 文件：`src/main/java/com/example/agent/streaming/EventStreamService.java`
- 说明：`EventStreamService` 通过 `@EventListener` 监听 `StreamEvent` 并分发到订阅端，同时写入 Redis 事件流用于断线续传。

## 事件消费源头分析
1. 流式订阅入口
- 文件：`src/main/java/com/example/agent/streaming/SseStreamController.java`
- 入口：`GET /api/v1/stream/sse`
- 作用：将 `EventStreamService.stream` 的事件封装为服务器发送事件格式输出。

2. 游标与续传
- 入参：`last_event_id` 查询参数或 `Last-Event-ID` 请求头
- 行为：
  - 启用游标时先读取 Redis 历史事件，再拼接实时事件流。
  - 游标不合法会触发 `STREAM_GAP` 异常，返回冲突错误。

3. 首事件超时
- 配置：`agent.sse.timeoutSeconds`
- 行为：超时后生成 `ERROR_OCCURRED` 事件并写入索引，用于提醒订阅端超时。

4. 其他消费入口
- 文件：`src/main/java/com/example/agent/gateway/controller/TimelineController.java`
- 入口：`GET /api/v1/events`、`GET /api/v1/timeline`
- 作用：面向查询与回放，不是实时流式订阅入口。

## 交互使用说明
### 步骤一：提交任务以产生事件
```bash
curl -X POST "http://localhost:8080/api/v1/tasks" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d "{\"query\":\"请给出本周工作计划\",\"sessionId\":\"session-1\",\"idempotencyKey\":\"idem-001\"}"
```

响应示例：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "taskId": "task-123",
    "workflowId": "workflow-123",
    "status": "SUBMITTED"
  },
  "traceId": "trace-001",
  "requestId": "req-001"
}
```

### 步骤二：订阅事件流
```bash
curl -N "http://localhost:8080/api/v1/stream/sse?workflow_id=workflow-123" \
  -H "Accept: text/event-stream" \
  -H "X-API-Key: your-api-key"
```

### 步骤三：按类型过滤事件
```bash
curl -N "http://localhost:8080/api/v1/stream/sse?workflow_id=workflow-123&types=PLAN_GENERATED,TOOL_INVOKED,CONTEXT_SNAPSHOT_STAGE" \
  -H "Accept: text/event-stream" \
  -H "X-API-Key: your-api-key"
```

### 步骤四：断线续传
- 推荐优先使用请求头方式传入游标。
```bash
curl -N "http://localhost:8080/api/v1/stream/sse?workflow_id=workflow-123" \
  -H "Accept: text/event-stream" \
  -H "X-API-Key: your-api-key" \
  -H "Last-Event-ID: workflow-123:18"
```

### 步骤五：触发工具或审批事件（可选）
工具调用：
```bash
curl -X POST "http://localhost:8080/api/v1/mcp/tools/call" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d "{\"toolName\":\"demo_tool\",\"callId\":\"call-1\",\"arguments\":{\"workflowId\":\"workflow-123\",\"input\":\"ping\"}}"
```

审批决策：
```bash
curl -X POST "http://localhost:8080/api/v1/workflows/workflow-123/approval/decision" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d "{\"decision\":\"APPROVED\",\"reason\":\"ok\"}"
```

### 订阅输出示例
```text
id: workflow-123:1
event: WORKFLOW_STARTED
data: {"eventId":"workflow-123:1","type":"WORKFLOW_STARTED","workflowId":"workflow-123","payload":{"message":"workflow started"}}

id: workflow-123:2
event: PLAN_GENERATED
data: {"eventId":"workflow-123:2","type":"PLAN_GENERATED","workflowId":"workflow-123","payload":{"planSummary":"..."}}
```

## 常见问题与注意事项
1. 如果收到 `STREAM_GAP`，说明游标已过期或缺失，需要重新订阅或调整游标。
2. `agent.sse.max-stream-size` 控制可续传的事件数量，超过后旧事件被淘汰。
3. 事件类型以 `EventType` 枚举为准，建议按需设置 `types` 过滤。