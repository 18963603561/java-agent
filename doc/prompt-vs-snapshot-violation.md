# 提示词与 ContextSnapshot 关联路径确认

## 0. 结论摘要
- 当前提示词渲染仅显式使用 `ContextSnapshot.roleBoundary.riskLevel` 与 `ContextSnapshot.taskIntent.inputText`，其余字段未直接写入提示词。
- 但 `PromptTemplate.render(ContextSnapshot)` 在多条链路上被调用，完整快照对象被传入模板层，存在运行元数据被模板扩展误引入提示词的风险。
- 规划阶段已通过 `DefaultContextAssembler` 生成 `PromptAssemblyInput`，运行阶段多数走 `DefaultPromptAssembler.buildLegacy`，由模板直接生成 system/developer 文本并拼接用户提示。

## 1. ContextSnapshot 到提示词的路径清单
### 1.1 规划阶段（主路径）
- `AgentRuntime.applyContextSnapshot(...)` 将快照写入运行时上下文 `contextSnapshot`。
  - `src/main/java/com/example/agent/runtime/AgentRuntime.java:1071`
- `PlannerService.buildPromptAssemblyInput(...)` 调用 `resolveContextSnapshot(context)` 获取快照。
  - `src/main/java/com/example/agent/planning/PlannerService.java:1206`
- `DefaultContextAssembler.fillSystemDeveloper(...)` 调用 `PromptTemplate.render(snapshot)` 生成 system/developer。
  - `src/main/java/com/example/agent/context/DefaultContextAssembler.java:59`
- `DefaultContextAssembler.resolveUserText(...)` 在用户提示为空时使用 `snapshot.taskIntent.inputText`。
  - `src/main/java/com/example/agent/context/DefaultContextAssembler.java:79`
- `PlannerService` 将 `promptAssemblyInput` 写入 `assemblyContext` 并调用 `PromptAssembler.build(...)`。
  - `src/main/java/com/example/agent/planning/PlannerService.java:1110`
- `DefaultPromptAssembler.fillSystemDeveloper(...)` 在 system/developer 缺失时再次调用 `PromptTemplate.render(snapshot)`。
  - `src/main/java/com/example/agent/model/DefaultPromptAssembler.java:182`

### 1.2 运行阶段（决策/总结/最终输出）
- `DefaultPromptAssembler.resolveSnapshot(...)` 仅读取 `stepInput.contextSnapshot` 或 `taskRequest.context.contextSnapshot`。
  - `src/main/java/com/example/agent/model/DefaultPromptAssembler.java:132`
- `LlmStepService` 的决策与总结提示均调用 `promptAssembler.build(...)`，通常由 `taskRequest.context` 提供快照。
  - `src/main/java/com/example/agent/runtime/LlmStepService.java:560`
- `FinalOutputService` 通过 `taskRequest.context` 获取快照。
  - `src/main/java/com/example/agent/runtime/FinalOutputService.java:462`
- `ReactLoopService` 通过 `taskRequest.context` 获取快照；但该 `taskRequest` 来自 `buildRequestWithContext(request, stepInput)`，若 `stepInput` 未显式包含 `contextSnapshot`，快照会丢失。
  - `src/main/java/com/example/agent/runtime/AgentRuntime.java:745`
  - `src/main/java/com/example/agent/runtime/ReactLoopService.java:177`

### 1.3 其它调用入口（默认不会使用快照）
- `ReflectionService` / `ChainOfThoughtService` / `MultiAgentCoordinator`：仅传入 `stepInput`，除非上游显式塞入 `contextSnapshot`，否则不会触发模板渲染。
  - `src/main/java/com/example/agent/reflection/ReflectionService.java:454`
  - `src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java:1045`
  - `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java:406`
- `JsonOutputRepairService` / `DebateCoordinator` / `ResearchPipeline`：`build(prompt, null, null)`，不会使用快照。
  - `src/main/java/com/example/agent/repair/JsonOutputRepairService.java:102`
  - `src/main/java/com/example/agent/reasoning/DebateCoordinator.java:273`
  - `src/main/java/com/example/agent/research/ResearchPipeline.java:291`

## 2. ContextSnapshot 当前被用于提示词的具体位置
- `DefaultPromptTemplate.buildDeveloperMessage(...)` 读取 `snapshot.roleBoundary.riskLevel` 并追加到开发者提示。
  - `src/main/java/com/example/agent/model/DefaultPromptTemplate.java:75-77`
- `DefaultContextAssembler.resolveUserText(...)` 使用 `snapshot.taskIntent.inputText` 作为用户提示兜底。
  - `src/main/java/com/example/agent/context/DefaultContextAssembler.java:79-81`

## 3. 运行层不应进入大模型的字段
### 3.1 必须屏蔽（纯运行/审计/预算/链路）
- `ContextSnapshot.snapshotId`
- `RuntimeMeta.*`（`tenantId`、`userId`、`sessionId`、`workflowId`、`requestId`、`traceId`、`requestTime`、`tokenBudget`、`allowedTools` 等）
- `BudgetState.*`
- `AuditMetadata.*`
- `ToolState.lastCall` / `ToolCallState.*` / `ToolState.retryCount`
- `WorkingMemory.summaryVersion` / `summaryChars` / `workingMemoryItems` / `redactionsAppliedCount` / `usedStructuredSummary`
- `WorkingMemory.evidencePack`（包含内部标识与审计信息）

### 3.2 需要脱敏后才能入模（只保留可读内容）
- `RoleBoundary.systemPolicyId` / `developerPolicyId`（仅保留可读规则，不传内部策略标识）
- `TaskIntent.taskId`
- `DomainKnowledge.citations`：保留 `title` / `snippet` / `source`，去除 `refId` / `uri` / `fetchedAt`
- `LongTermMemory.memoryRefs`：保留 `snippet`，去除 `memoryId` / `score` / `expiresAt` / `source`
- `ToolState.availableTools` / `selectedTools` / `lastError`：转为提示级别的可读约束或错误摘要，不透传内部结构

## 4. 新的 PromptAssemblyInput（决策层上下文）最小结构建议
### 4.1 目标
- 仅承载提示词决策所需信息，不携带运行元数据与审计字段。
- 与 `ContextSnapshot` 解耦，防止模板误用运行层字段。

### 4.2 最小结构（示例）
```json
{
  "policy": {
    "riskLevel": "low|medium|high",
    "forbiddenActions": ["..."],
    "dataScopes": ["..."],
    "approvalRequired": true
  },
  "task": {
    "inputText": "...",
    "successCriteria": "...",
    "requiredOutput": "...",
    "constraints": ["..."]
  },
  "memory": {
    "summary": "...",
    "keyFacts": ["..."],
    "planSteps": ["..."],
    "nextStep": "..."
  },
  "knowledge": {
    "citations": [
      {"title": "...", "source": "...", "snippet": "..."}
    ],
    "memorySnippets": ["..."]
  },
  "output": {
    "locale": "zh-CN",
    "format": "json|text|markdown"
  }
}
```

### 4.3 映射关系（从 ContextSnapshot 到新结构）
- `roleBoundary.riskLevel` -> `policy.riskLevel`
- `roleBoundary.forbiddenActions` / `dataScopes` / `approvalRequired` -> `policy.*`
- `taskIntent.inputText` / `successCriteria` / `requiredOutput` / `constraints` -> `task.*`
- `workingMemory.summary` / `keyFacts` / `planSteps` / `nextStep` -> `memory.*`
- `domainKnowledge.citations` -> `knowledge.citations`（仅保留可读字段）
- `longTermMemory.memoryRefs.snippet` -> `knowledge.memorySnippets`
- `runtimeMeta.locale` / `outputFormat` -> `output.*`

## 5. 备注
- 当前模板仅使用 `riskLevel` 与 `inputText`，但快照对象在装配链路中全量透传，后续模板扩展容易引入运行层字段；建议将模板入参收敛为“决策层上下文”。

## 6. 已落地改造
### 6.1 代码路径
- `PromptTemplate` 新增最小渲染入参 `PromptRenderContext`，模板渲染仅接收白名单字段（当前仅 riskLevel）。
- `DefaultPromptTemplate` 的 `render(ContextSnapshot)` 仅做 riskLevel 映射后转发，模板内部不再读取快照复杂结构。
- `DefaultContextAssembler.fillSystemDeveloper(...)` 与 `DefaultPromptAssembler.fillSystemDeveloper(...)` 均改为 `render(PromptRenderContext)`。
- `DefaultPromptAssembler.buildLegacy(...)` 不再使用 `render(snapshot)`，统一走最小渲染上下文。

### 6.2 新增测试
- `PromptTemplateSnapshotLeakTest`：验证模板渲染不泄露 runtimeMeta/budget/audit/snapshotId 等字段。
- `ContextAssemblerDoesNotPassSnapshotToTemplateTest`：验证上下文装配不透传运行层字段到模板。
- `DefaultPromptAssemblerLegacyPathNoLeakTest`：验证 legacy 分支渲染不泄露运行层字段。
