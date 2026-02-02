# LLM 步骤 Prompt 结构与返回格式完善方案（支持工具调用）

## 目标
在 executeLlmStep 的独立 LLM 分支中，定义清晰的 Prompt 结构与返回格式，使模型可返回工具调用指令，并支持后续执行与回写。

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

## 工具调用执行流程（建议）
1) LLM 步骤返回 `mode=tool_call`。
2) 通过 `ToolExecutor` 或 `McpToolClient` 执行工具调用。
3) 将工具执行结果回写到上下文：
   - `context.lastToolResult`
   - `context.lastToolError`
   - `context.selectedTools`
4) 可选：执行二次 LLM 调用，生成最终 LLM 步骤输出（不走 FinalOutputService）。

## 工具注入触发条件建议
- `toolChoice.mode = AUTO` 或 toolChoice 为空 -> 注入工具。
- `toolChoice.mode = NONE` 或 `context.disableTools=true` -> 不注入工具。

## 解析与容错建议
- 输出必须是单一 JSON，解析失败时走兜底回答。
- 若 tool.name 不在可用列表内，记录警告并回退为 answer 模式。
- 若 tool.arguments 缺失，提示模型重试或回退为 answer 模式。

## 需要配套的代码改动点（提示）
- `LlmStepService` 新增：
  - prompt 组装与 JSON 解析
  - 工具调用解析与执行
  - 二次生成（可选）
- `AgentRuntime.executeLlmStep(...)`：
  - 选择 LLM 新分支
  - 根据返回模式执行工具或直接回答

## 下一步确认
- 是否需要执行“工具调用后再总结”的二次 LLM 输出。
- 是否需要为工具调用新增统一的错误码与重试策略。
