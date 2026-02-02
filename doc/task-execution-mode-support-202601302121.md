# 任务提交同步与异步能力确认

## 结论
- 当前提交任务同时支持 `ASYNC` 与 `SYNC` 两种执行模式。
- 未传 `executionMode` 时默认按 `ASYNC` 处理。
- 使用 `SYNC` 且在等待时限内完成时，接口返回 `HTTP 200` 并携带结果 `result`。
- 使用 `SYNC` 但等待超时或同步并发被限制时，接口退化为 `HTTP 202`，任务继续后台运行，需通过查询或订阅获取结果。

## 关键字段与契约
### 请求字段
- `TaskRequest.executionMode`：执行模式，可选值 `ASYNC`、`SYNC`。
- `TaskRequest.waitTimeoutMs`：同步等待超时毫秒数，仅在 `SYNC` 生效。

### 响应字段
- `TaskResponse.result`：任务结果，仅在同步完成返回时存在。
- `TaskResponse.streamUrl`：事件流订阅地址，用于异步或同步超时后的持续追踪。

## 同步与异步的处理流程
### 统一提交流程
1) 控制器接收请求，调用编排器提交任务。
2) 编排器将任务持久化，并提交到后台执行器。
3) 根据 `executionMode` 决定是否进入同步等待。

### 异步模式 `ASYNC`
- 默认模式，或请求明确指定 `executionMode=ASYNC`。
- 提交成功后直接返回 `HTTP 202`，响应包含 `taskId`、`workflowId`、`status`、`streamUrl`。
- 结果通过查询 `GET /api/v1/tasks/{taskId}` 或订阅 `streamUrl` 获取。

### 同步模式 `SYNC`
- 请求指定 `executionMode=SYNC` 时触发同步等待逻辑。
- 若任务在等待窗口内完成：
  - `HTTP 200` 返回。
  - `TaskResponse.result` 返回执行结果。
- 若等待超时、执行器未命中或并发达到上限：
  - 退化为 `HTTP 202`。
  - 返回 `taskId`、`workflowId`、`status=RUNNING`、`streamUrl`。
  - 响应错误码为 `SYNC_WAIT_TIMEOUT`（由异常处理器统一输出）。

## 同步返回结果的来源与一致性
- 同步完成时返回的 `result` 来自任务持久化记录 `TaskRecord.result`。
- 该结果由运行时输出构建，包含 `planId`、`planSummary`、`steps`、`finalOutput`。
- 由于读取同一持久化记录，`SYNC` 返回结果与最终完成事件结果保持一致。

## 超时与并发护栏
- 默认等待时长：`agent.task.sync.wait-timeout-ms`，默认 `30000` 毫秒。
- 最大等待上限：`agent.task.sync.max-wait-timeout-ms`，默认 `60000` 毫秒。
- 同步并发限制：`agent.task.sync.max-concurrency`，默认 `20`。
- 超时或并发超过限制时不会阻塞请求线程，直接返回 `HTTP 202`。

## 使用示例
### 异步提交
```json
{
  "query": "查询订单统计",
  "executionMode": "ASYNC"
}
```

### 同步提交并等待结果
```json
{
  "query": "生成摘要",
  "executionMode": "SYNC",
  "waitTimeoutMs": 30000
}
```

## 结论摘要
- 同步与异步均已支持。
- 同步在完成时会返回执行结果 `result`，超时则退化为异步并继续后台执行。