# `TaskRequest` 参数与上下文说明（更新）

## 1. `TaskRequest` 定位
`TaskRequest` 是任务提交入口的请求体结构，贯穿任务编排与执行全流程，用于承载：
- 用户输入（`query`、`sessionId`）
- 任务控制参数（`skillName`、`toolChoice`、`idempotencyKey`）
- 运行上下文（`context`）

主要入口：`TaskController.submitTask(...)` -> `TaskSubmissionService` -> `TaskOrchestrator` -> `AgentRuntime`。


## 2. 字段与使用场景
### 2.1 `query`（必填）
- **用途**：任务主问题/指令，直接用于规划与推理；也会作为工具调用参数与记忆写入内容。
- **使用位置**：`PlannerService`、`ToolExecutor`、`MemoryWriteService`。

### 2.2 `sessionId`（可选）
- **用途**：会话维度的上下文聚合与记忆召回。
- **使用位置**：`MemoryRecallService`、`MemoryWriteService`、`DefaultContextBuilder`。
- **建议**：同一用户/会话保持稳定，便于跨请求长期记忆。

### 2.3 `skillName`（可选）
- **用途**：指定“技能”以约束可用工具与工具选择策略。
- **使用位置**：`ModelToolResolver.resolveSkillName(...)` -> `SkillRegistry` -> `SkillDefinition`。

### 2.4 `context`（可选）
- **用途**：携带任务运行上下文与控制参数，是“任务元数据”和“运行期快照”的承载容器。
- **传递方式**：
  1) 入口请求携带 `context`
  2) `AgentRuntime.run(...)` 合并为 `runtimeContext`
  3) 运行中不断写入新上下文
  4) 工具调用时将 `context` 合并为工具参数（见 `ToolExecutor.buildArguments(...)`）

### 2.5 `idempotencyKey`（可选）
- **用途**：幂等提交保障，相同 key 可避免重复创建任务。
- **使用位置**：`TaskOrchestrator`（数据库或 Redis 幂等索引）。

### 2.6 `toolChoice`（可选，`ModelToolChoice`）
- **用途**：控制模型对工具的使用策略，覆盖默认自动选择。
- **使用位置**：`ModelToolResolver` -> `DefaultModelProvider`。


## 3. `context` 参数扫描结果（按功能分组）
> 本节基于工程内 `context.get("...")` 等读取点做全量扫描归纳。

### 3.1 规划与执行策略
这些字段直接影响 `PlannerService` 的规划与执行模式，**可以通过 `TaskRequest.context` 传入**：
- `mode`：规划模式，例如 `react` 可触发 `ReAct` 规划分支。
- `strategy`：高层策略提示，与 `cognitive_strategy` 互为替补。
- `cognitive_strategy`：认知策略提示（如 `tree_of_thoughts`、`reflection`）。
- `executionStrategy`：执行策略提示（如 `sequential`）。
- `react` / `reactEnabled`：显式启用 `ReAct` 模式的开关（布尔或字符串）。

常见取值与效果：
- `mode=deep_research` 或 `strategy=research`：插入 `RESEARCH` 步骤。
- `strategy=multi_agent`：插入 `MULTI_AGENT` 步骤。
- `strategy=debate`：插入 `DEBATE` 步骤。
- `cognitive_strategy=tree_of_thoughts` 或 `cognitive_strategy=tot`：插入 `THOUGHT_TREE` 步骤。
- `mode|strategy|cognitive_strategy` 为 `cot` / `chain_of_thought` / `chain-of-thought`：直接进入 `CHAIN_OF_THOUGHT` 步骤。

**示例：通过 `mode` 启用 `ReAct`**
```json
{
  "query": "分析异常并给出修复方案",
  "sessionId": "s-100",
  "context": {
    "mode": "react",
    "reactEnabled": true,
    "strategy": "reflection"
  }
}
```

**示例：启用深度研究并使用多智能体**
```json
{
  "query": "分析竞品功能差异并输出对比表",
  "context": {
    "mode": "deep_research",
    "strategy": "multi_agent"
  }
}
```

### 3.2 工具选择与调用控制
- `tool` / `toolName`：指定默认工具名。
- `fallbackTool`：指定兜底工具。
- `selectedTools`：已选工具列表，用于提示或限制。
- `allowedTools`：允许的工具列表（进入上下文快照）。
- `mcpServerId`：指定服务端标识（`ToolExecutor.resolveServerId(...)`）。
- `lastToolError`：最近工具错误信息（影响 `ToolState` 展示）。

**示例：限制工具并指定兜底**
```json
{
  "query": "查询库存",
  "context": {
    "tool": "inventory.search",
    "fallbackTool": "inventory.lookup",
    "selectedTools": ["inventory.search"],
    "allowedTools": ["inventory.search", "inventory.lookup"]
  }
}
```

### 3.3 预算与能力评估
这些字段用于能力评估与预算推理：
- `tokenBudget`：上下文预算覆盖值（影响上下文预算分配）。
- `budgetThresholdTokens`：能力评估阈值（`PlannerService` -> `CapabilityEvaluationInput`）。
- `failureTypes`：失败类型列表，用于能力评估提示。
- `planSummary`：规划摘要，用于能力评估输入。

**示例：为能力评估提供阈值与失败类型**
```json
{
  "query": "批量同步客户数据",
  "context": {
    "budgetThresholdTokens": 6000,
    "failureTypes": ["RATE_LIMIT", "MCP_UNAVAILABLE"],
    "planSummary": "同步前校验 -> 批量写入 -> 结果回执"
  }
}
```

### 3.4 记忆召回与写入控制
- `memoryWriteEnabled`：是否写入记忆。
- `memoryRecallEnabled` / `memoryRecallForce` / `memoryRecallLimit` / `memoryRecallMinQueryLength`
- `memoryRecallIncludeCompressed` / `memoryRecallMaxSummaryChars` / `memoryRecallMaxRecordChars`
- `contextPolicy` / `policy`：上下文策略（影响记忆召回与裁剪）。
- `contextPolicy` 子字段：`policyId`、`retrievalPriority`、`pruneOrder`、`maxEvidenceCount`、`maxMemoryCount`、`enableSensitiveMask`。
- `longTermMemoryRefs`：长期记忆引用列表（用于构建 `LongTermMemory`）。
- `nextStep`：写入工作记忆的下一步提示，供裁剪与摘要使用。

**示例：自定义记忆召回策略**
```json
{
  "query": "回顾上次会议重点",
  "context": {
    "contextPolicy": {
      "policyId": "policy-1",
      "retrievalPriority": ["SEMANTIC", "RECENT"],
      "maxMemoryCount": 5,
      "enableSensitiveMask": true
    }
  }
}
```

### 3.5 安全、审批与边界
这些字段用于 `RoleBoundary`：
- `systemPolicyId` / `developerPolicyId`
- `forbiddenActions` / `dataScopes`
- `riskLevel`
- `requiresApproval` / `approvalSource`

### 3.6 任务意图与输出偏好
这些字段进入 `TaskIntent` 与 `RuntimeMeta`：
- `successCriteria` / `failurePolicy` / `requiredOutput` / `constraints`
- `locale` / `outputFormat`

### 3.7 引用与证据
- `citations` / `researchCitations`：外部引用来源（构建 `DomainKnowledge`）。
- `evidencePack`：运行时注入的证据包（工具/记忆/引用聚合）。

### 3.8 提示词装配与内部调试
- `promptAssemblyInput`：自定义提示词装配输入。
- `context` / `query` / `question` / `output`：内部本地模式解析字段（`DefaultModelProvider` 用于本地模拟输出）。
  - 一般不建议调用方主动传入。

### 3.9 链路与标识
- `workflowId`：工作流标识，用于事件流、记忆与证据包关联，通常由编排层写入。
- `snapshotId`：上下文快照标识，用于审批与证据包关联，通常由上下文构建阶段写入。
- `tenantId`：租户标识兜底输入，仅用于提示词装配阶段兜底，一般由租户上下文提供。


## 4. 运行时注入字段（系统写入）
以下字段会在运行时写入 `context`，调用方通常无需设置：
- `workflowId`
- `toolChoice`
- `memory`（`summary` / `records` / `count` / `reason`）
- `contextSnapshot` / `snapshotId`
- `contextBudget` / `contextPrune`
- `evidencePack`
- `planSteps` / `planDependencies`
- `executionStrategy` / `cognitiveStrategy`
- `planReason` / `planAttempt`
- `lastStepId` / `lastStepType` / `lastStepOutput` / `lastOutputSize`
- `evaluationApprovalGranted`
- `capabilityScore` / `capabilityRisk`


## 5. `skillName` 使用方式
- 请求级：`TaskRequest.skillName`
- 步骤级：`stepInput.skill` 或 `stepInput.skillName`
- 配置级：`agent.skills.definitions` 定义技能、工具白名单与工具选择策略

示例（配置片段）：
```yaml
agent:
  skills:
    definitions:
      - name: research
        constraints:
          allowTools: ["web.search", "web.open"]
          toolChoice: "required"
        routes:
          - toolName: "web.search"
          - toolName: "web.open"
```


## 6. 更新结论
- `context.get("mode")` 等字段**可以由调用方在 `TaskRequest.context` 中传入**。
- 工程扫描发现新增/遗漏字段已补充到本文档，包括：
  - 规划策略类：`mode`、`strategy`、`cognitive_strategy`、`executionStrategy`、`react`、`reactEnabled`
  - 评估类：`budgetThresholdTokens`、`failureTypes`、`planSummary`
  - 记忆策略类：`contextPolicy` 子字段、`nextStep`
  - 链路标识类：`workflowId`、`tenantId`、`snapshotId`
  - 其他：`systemPolicyId`、`developerPolicyId`、`riskLevel`、`lastToolError`、`tokenBudget`、`capabilityScore`、`capabilityRisk` 等
- 建议调用方只传“控制类与意图类字段”，运行态快照与证据字段由系统维护。
