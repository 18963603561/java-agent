# LLM 步骤 Prompt 结构与返回格式完善方案（支持工具调用）

## 目标
在 executeLlmStep 的独立 LLM 分支中，定义清晰的 Prompt 结构与返回格式，使模型可返回工具调用指令，并支持后续执行、重试与二次总结输出。

## 设计原则
- 与最终合并输出分离，避免复用 FinalOutputService 的总结逻辑。
- 输出必须是单一 JSON 对象，便于解析与执行。
- 显式约束工具调用格式，便于兼容本地工具与 MCP 远端工具。
- 允许“纯回答”或“工具调用”两种模式共存。

## LLM 步骤 Prompt 结构（建议模板）
### Prompt 结构
```
你是任务执行助手（LLM Step Runner），你的任务是根据用户问题与上下文决定：
1) 直接给出回答；或
2) 选择合适的工具并返回工具调用指令。

规则：
- 只能输出一个 JSON 对象，不能包含任何其他文本。
- 如果需要调用工具，必须输出 tool_call 结构。
- 如果不需要工具，必须输出 answer 结构。

输出 JSON 规范：
{
  "mode": "answer" | "tool_call",
  "answer": "...",
  "tool": {
    "name": "工具名称",
    "arguments": { ... }
  },
  "reason": "选择工具或直接回答的简短理由",
  "confidence": 0.0 ~ 1.0
}

约束：
- mode=answer 时必须有 answer，tool 必须为空或省略。
- mode=tool_call 时必须有 tool.name 与 tool.arguments，answer 可为空。
- 工具名称必须来自给定工具列表，不可杜撰。

输入上下文（JSON）：
LLM_STEP_CONTEXT_JSON:%s
```

### LLM_STEP_CONTEXT_JSON 建议结构
```
{
  "query": "用户问题",
  "toolChoice": { "mode": "AUTO" },
  "availableTools": [
    {
      "name": "tool_a",
      "description": "...",
      "tags": ["..."]
    }
  ],
  "constraints": {
    "disableTools": false,
    "allowedTools": ["tool_a", "tool_b"]
  },
  "runtime": {
    "tenantId": "...",
    "sessionId": "...",
    "traceId": "..."
  }
}
```

## 返回格式规范（支持工具调用）
### 1) 直接回答
```
{
  "mode": "answer",
  "answer": "这里是直接回答内容",
  "reason": "不需要工具即可回答",
  "confidence": 0.62
}
```

### 2) 工具调用
```
{
  "mode": "tool_call",
  "tool": {
    "name": "query_user",
    "arguments": {
      "keyword": "h"
    }
  },
  "reason": "需要查询用户信息，调用查询工具",
  "confidence": 0.78
}
```

## 工具调用执行与二次 LLM 总结流程（推荐）
1) LLM 步骤返回 `mode=tool_call`。
2) 通过 `ToolExecutor` 或 `McpToolClient` 执行工具调用。
3) 将工具执行结果回写到上下文：
   - `context.lastToolResult`
   - `context.lastToolError`
   - `context.selectedTools`
4) 执行二次 LLM 调用（同一 LLM 步骤场景，不走 FinalOutputService）：
   - 输入包含：原始问题 + 工具结果摘要 + 失败原因（如有）。
   - 输出为最终 LLM 步骤回答 JSON。

## 二次 LLM 总结 Prompt（建议模板）
```
你是任务执行助手（LLM Step Runner）。
请基于工具执行结果生成最终回答。

规则：
- 只能输出一个 JSON 对象，不能包含任何其他文本。
- 如果工具执行失败，必须说明失败原因并给出可执行的下一步建议。

输出 JSON 规范：
{
  "answer": "...",
  "highlights": "关键证据或失败原因摘要",
  "confidence": 0.0 ~ 1.0
}

输入上下文（JSON）：
LLM_STEP_TOOL_RESULT_JSON:%s
```

## 工具注入触发条件建议
- `toolChoice.mode = AUTO` 或 toolChoice 为空 -> 注入工具。
- `toolChoice.mode = NONE` 或 `context.disableTools=true` -> 不注入工具。

## 工具调用统一错误码与重试策略（建议）
### 错误码建议
- `TOOL_INVALID_REQUEST`：请求参数缺失或格式错误。
- `TOOL_NOT_FOUND`：工具未注册或不可用。
- `TOOL_TIMEOUT`：工具调用超时。
- `TOOL_RATE_LIMITED`：触发限流。
- `TOOL_UNAVAILABLE`：工具服务不可用或网络异常。
- `TOOL_EXECUTION_FAILED`：工具内部执行异常。

### 重试策略建议
- `TOOL_TIMEOUT`、`TOOL_UNAVAILABLE`、`TOOL_RATE_LIMITED`：允许重试。
- `TOOL_INVALID_REQUEST`、`TOOL_NOT_FOUND`：不重试，直接失败并回退。
- 重试次数与退避：沿用现有 `RetryPolicy` 机制，默认 2 次，指数退避 + 抖动。

## 解析与容错建议
- 输出必须是单一 JSON，解析失败时走兜底回答。
- 若 tool.name 不在可用列表内，记录警告并回退为 answer 模式。
- 若 tool.arguments 缺失，提示模型重试或回退为 answer 模式。

## 需要配套的代码改动点（提示）
- `LlmStepService` 新增：
  - prompt 组装与 JSON 解析
  - 工具调用解析与执行
  - 二次总结调用
  - 统一错误码映射
- `AgentRuntime.executeLlmStep(...)`：
  - 选择 LLM 新分支
  - 根据返回模式执行工具或直接回答

## 下一步确认
- 二次总结输出是否需要与最终输出的 schema 对齐。
- 工具错误码是否需要落地到对外 API 的响应结构中。
