# 服务查询用例测试报告-20260128

## 基本信息
- 测试目标：覆盖 `gateway/controller` 下所有接口并验证可用性
- `OpenAPI` 文件：`specs/001-agent-core-spec/contracts/openapi.yaml`
- 服务地址：`http://localhost:8080`
- 公共请求头：`X-API-Key: demo-key`，`X-Tenant-Id: demo-tenant`
- 依赖容器：`java-agent-postgres`(`5433`)、`java-agent-redis`(`6379`)、`java-agent-qdrant`(`6333`)
- 关键配置：`application.yml` 已切换为真实依赖（`agent.storage.mode=postgres`、`spring.datasource.url=jdbc:postgresql://localhost:5433/agent`、`spring.data.redis.host=localhost`）

## 前置数据
- 任务：`task-demo-1`，工作流：`workflow-demo-1`
- 事件日志：`workflow-demo-1:1`
- 步骤记录：`step-demo-1`
- 预算记录：`usage-demo-1`
- 调度任务：`schedule-demo-1`
- 记忆记录：`memory-demo-1`

## 用例明细
### 任务接口
#### 提交任务
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/tasks
```

- 输入数据：

```json
{
    "query":  "demo task",
    "idempotencyKey":  "idem-task-report-1",
    "toolChoice":  {
                       "mode":  "AUTO"
                   },
    "sessionId":  "session-demo-1"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"taskId":"8dbde197-f00e-48b2-b8de-c9842a054c32","workflowId":"29ddcbab-3dcb-4470-b5b6-6223f05e24ee","status":"SUBMITTED"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 查询任务列表
- 请求方法：

```
GET
```

- 请求 URL：

```
http://localhost:8080/api/v1/tasks?size=5
```

- 输入数据：

```json
size=5
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"tasks":[{"taskId":"task-demo-1","workflowId":"workflow-demo-1","status":"COMPLETED","updatedAt":"2026-01-28T04:02:29.720508Z","result":{"finalOutput":"ok"}}],"nextCursor":null,"hasMore":false,"total":1},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 查询任务详情
- 请求方法：

```
GET
```

- 请求 URL：

```
http://localhost:8080/api/v1/tasks/task-demo-1
```

- 输入数据：

```json
taskId=task-demo-1
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"taskId":"task-demo-1","workflowId":"workflow-demo-1","status":"COMPLETED","updatedAt":"2026-01-28T04:02:29.720508Z","result":{"finalOutput":"ok"}},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### 记忆接口
#### 保存记忆
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/memory/save
```

- 输入数据：

```json
{
    "summary":  "test summary",
    "content":  "test memory content",
    "sessionId":  "session-demo-1"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"memoryId":"2a7f1f42-5474-4835-9c08-bf7c952165db","sessionId":"session-demo-1","taskId":null,"content":"test memory content","summary":"test summary","embeddingRef":null,"tenantId":"demo-tenant","layer":"recent","createdAt":"2026-01-28T04:31:27.025722100Z"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 搜索记忆
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/memory/search
```

- 输入数据：

```json
{
    "query":  "test",
    "limit":  5,
    "sessionId":  "session-demo-1"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"records":[{"memoryId":"memory-demo-1","sessionId":"session-demo-1","taskId":null,"content":"test memory content","summary":"test summary","embeddingRef":null,"tenantId":"demo-tenant","layer":"recent","createdAt":"2026-01-28T04:17:43.320410Z"}]},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 压缩记忆
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/memory/compress
```

- 输入数据：

```json
{
    "sessionId":  "session-demo-1",
    "strategy":  "default"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"memoryId":"899276be-b65a-4843-a51d-7bb4dfa6d548","sessionId":"session-demo-1","taskId":null,"content":null,"summary":"test memory content","embeddingRef":null,"tenantId":"demo-tenant","layer":"compressed","createdAt":"2026-01-28T04:31:27.112212600Z"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### 预算接口
#### 记录预算使用
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/budget/usage
```

- 输入数据：

```json
{
    "usageId":  "usage-demo-1",
    "costUsd":  0.25,
    "inputTokens":  10,
    "agentId":  "planner",
    "taskId":  "task-demo-1",
    "model":  "planner",
    "outputTokens":  5,
    "provider":  "local",
    "totalTokens":  15
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"recordId":"2502335b-c1b4-452a-96b7-4fb75b2c6705","usageId":"usage-demo-1","taskId":"task-demo-1","agentId":"planner","model":"planner","provider":"local","inputTokens":10,"outputTokens":5,"totalTokens":15,"costUsd":0.25,"createdAt":"2026-01-28T04:31:27.148672200Z","tenantId":"demo-tenant"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 查询预算汇总
- 请求方法：

```
GET
```

- 请求 URL：

```
http://localhost:8080/api/v1/budget/summary?taskId=task-demo-1
```

- 输入数据：

```json
taskId=task-demo-1
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"taskId":"task-demo-1","totalTokens":15,"totalCostUsd":0.25,"byModel":{"planner":15},"byProvider":{"local":0.25}},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### 策略接口
#### 策略评估
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/policy/evaluate
```

- 输入数据：

```json
{
    "input":  {
                  "risk":  "low"
              },
    "resource":  "task",
    "policyId":  "policy-demo",
    "action":  "read"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"policyId":"policy-demo","decision":"ALLOW","reason":"ok","evaluationId":"a1031357-ca11-4056-9be4-d8ccf722305c","matchedRules":["default_allow"]},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### MCP接口
#### 工具列表
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/mcp/tools/list
```

- 输入数据：

```json
{
    "cursor":  null,
    "serverId":  "mcp-default",
    "size":  5
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"tools":[{"name":"demo_tool","version":"v1","description":"示例工具","inputSchema":{"type":"object"},"outputSchema":{"type":"object"},"tags":["demo"]}],"nextCursor":null,"hasMore":false},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 工具调用
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/mcp/tools/call
```

- 输入数据：

```json
{
    "serverId":  "mcp-default",
    "arguments":  {
                      "workflowId":  "workflow-demo-1",
                      "input":  "demo"
                  },
    "callId":  "call-demo-1",
    "toolName":  "demo_tool"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"callId":"call-demo-1","status":"SUCCESS","result":{"message":"demo_tool_ok","echo":{"workflowId":"workflow-demo-1","input":"demo"}},"error":null},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### 审批接口
#### 审批决策
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/workflows/workflow-demo-1/approval/decision
```

- 输入数据：

```json
{
    "decision":  "approve",
    "reason":  "demo"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"workflowId":"workflow-demo-1","state":"RUNNING","decision":"approve"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### 时间线接口
#### 事件日志查询
- 请求方法：

```
GET
```

- 请求 URL：

```
http://localhost:8080/api/v1/events?workflowId=workflow-demo-1&size=10
```

- 输入数据：

```json
workflowId=workflow-demo-1,size=10
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"events":[{"eventId":"workflow-demo-1:1","workflowId":"workflow-demo-1","type":"WORKFLOW_STARTED","timestamp":"2026-01-28T04:17:43.298398Z","payload":{"message":"demo"},"tenantId":"demo-tenant"}],"nextCursor":null,"hasMore":false},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 时间线查询
- 请求方法：

```
GET
```

- 请求 URL：

```
http://localhost:8080/api/v1/timeline?workflowId=workflow-demo-1&mode=summary
```

- 输入数据：

```json
workflowId=workflow-demo-1,mode=summary
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"workflowId":"workflow-demo-1","mode":"summary","events":[{"eventId":"workflow-demo-1:1","workflowId":"workflow-demo-1","type":"WORKFLOW_STARTED","timestamp":"2026-01-28T04:17:43.298398Z","payload":{"message":"demo"},"tenantId":"demo-tenant"}],"stats":{"mode":"summary","total":1}},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 步骤时间线查询
- 请求方法：

```
GET
```

- 请求 URL：

```
http://localhost:8080/api/v1/timeline/steps?workflowId=workflow-demo-1&size=10
```

- 输入数据：

```json
workflowId=workflow-demo-1,size=10
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"workflowId":"workflow-demo-1","steps":[{"stepId":"step-demo-1","workflowId":"workflow-demo-1","stepSeq":1,"type":"TOOL","status":"COMPLETED","attempt":1,"input":{"input":"demo"},"output":{"result":"ok"},"errorCode":null,"tenantId":"demo-tenant","startedAt":"2026-01-28T04:17:43.300975Z","completedAt":"2026-01-28T04:17:43.300975Z"}],"nextCursor":null,"hasMore":false},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### 调度接口
#### 创建调度
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/schedules
```

- 输入数据：

```json
{
    "scheduleId":  "schedule-api-create-1",
    "cron":  "0 0 0 1 1 *",
    "timezone":  "UTC",
    "status":  "ACTIVE",
    "idempotencyKey":  "idem-api-create-1"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"scheduleId":"schedule-api-create-1","status":"ACTIVE"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 更新调度
- 请求方法：

```
PUT
```

- 请求 URL：

```
http://localhost:8080/api/v1/schedules
```

- 输入数据：

```json
{
    "scheduleId":  "schedule-demo-1",
    "cron":  "0 0 0 1 1 *",
    "timezone":  "UTC",
    "status":  "ACTIVE",
    "idempotencyKey":  "idem-demo-1"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"scheduleId":"schedule-demo-1","status":"ACTIVE"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 查询调度列表
- 请求方法：

```
GET
```

- 请求 URL：

```
http://localhost:8080/api/v1/schedules?size=5
```

- 输入数据：

```json
size=5
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"schedules":[{"scheduleId":"schedule-demo-1","status":"ACTIVE"}],"nextCursor":null,"hasMore":false},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 暂停调度
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/schedules/schedule-demo-1/pause
```

- 输入数据：

```json
scheduleId=schedule-demo-1
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"scheduleId":"schedule-demo-1","status":"PAUSED"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 恢复调度
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/schedules/schedule-demo-1/resume
```

- 输入数据：

```json
scheduleId=schedule-demo-1
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"scheduleId":"schedule-demo-1","status":"ACTIVE"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 取消调度
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/schedules/schedule-demo-1/cancel
```

- 输入数据：

```json
scheduleId=schedule-demo-1
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"scheduleId":"schedule-demo-1","status":"CANCELLED"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

#### 删除调度
- 请求方法：

```
DELETE
```

- 请求 URL：

```
http://localhost:8080/api/v1/schedules/schedule-demo-1
```

- 输入数据：

```json
scheduleId=schedule-demo-1
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":null,"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

### 回放接口
#### 任务回放
- 请求方法：

```
POST
```

- 请求 URL：

```
http://localhost:8080/api/v1/replay
```

- 输入数据：

```json
{
    "mode":  "full",
    "fromStepId":  null,
    "toStepId":  null,
    "taskId":  "task-demo-1"
}
```

- 输出数据（HTTP 200）：

```json
{"code":"OK","message":"success","data":{"replayId":"6d727841-674e-4cd9-8a1f-6e2993f595dd","status":"COMPLETED","startedAt":"2026-01-28T04:31:27.824298700Z","completedAt":"2026-01-28T04:31:27.847165Z"},"traceId":null,"requestId":null}
```

- 结论：成功，返回 200

## 问题分析与修复
- 前次测试存在 `12` 个接口返回非 `200` 的情况，已完成原因分析与修复
- 修复后已重新执行全部接口调用，当前均返回 `200`
- 详细原因与修复过程见 `doc/interface-non200-analysis-20260128.md`

## 总结结论
- 已覆盖 `gateway/controller` 下所有接口并完成调用
- 全部接口返回 `200` 且响应结构符合预期
