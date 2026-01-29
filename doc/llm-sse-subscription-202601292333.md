# LLM 事件订阅固定开启方式

## 目的
固定订阅 `LLM_PROMPT` 与 `LLM_OUTPUT` 事件，用于排查提示词内容与模型输出。

## 结论
服务端默认会发布 `LLM_PROMPT` 与 `LLM_OUTPUT`，无需额外开关。
固定开启的关键在于：对每个任务的 `workflowId` 建立并保持 `SSE` 订阅。

## 前提条件
- 服务已启动并监听 `http://localhost:8080`
- 已知可用 `X-API-Key` 与 `X-Tenant-Id`
- 任务提交后能获取 `workflowId`

## 操作步骤
### 1. 提交任务并获取 `workflowId`
示例请求：
```json
{
  "query": "告诉我你是谁",
  "idempotencyKey": "idem-llm-verify-<guid>",
  "sessionId": "session-verify-1",
  "context": {
    "mode": "chain_of_thought"
  }
}
```

### 2. 订阅 `SSE` 事件流
使用命令行保持连接不退出。
```bash
curl.exe -N \
  -H "X-API-Key: demo-key" \
  -H "X-Tenant-Id: demo-tenant" \
  "http://localhost:8080/api/v1/stream/sse?workflow_id=<workflowId>&types=LLM_PROMPT,LLM_OUTPUT"
```

### 3. 断线续传
`SSE` 事件中的 `id` 形如 `workflowId:seq`，断线后使用 `Last-Event-ID` 恢复。
```bash
curl.exe -N \
  -H "X-API-Key: demo-key" \
  -H "X-Tenant-Id: demo-tenant" \
  -H "Last-Event-ID: <workflowId:seq>" \
  "http://localhost:8080/api/v1/stream/sse?workflow_id=<workflowId>&types=LLM_PROMPT,LLM_OUTPUT"
```

## 事件字段说明
- `LLM_PROMPT` 的 `payload.prompt` 包含最终拼装提示词
- `LLM_OUTPUT` 的 `payload.content` 为模型输出
- `payload.phase` 可区分调用阶段，例如 `plan`、`finalize`、`cot`

## 常见问题
1. 未看到 `LLM_PROMPT` 或 `LLM_OUTPUT`
   - 检查 `workflowId` 是否正确
   - 确认任务确实触发了模型调用
2. 返回提示词为空
   - 检查 `promptAssemblyInput` 是否为空
   - 检查提示词裁剪是否触发

## 建议
- 对每次任务都单独建立 `SSE` 订阅
- 需要持续观察时，保持连接窗口不要关闭