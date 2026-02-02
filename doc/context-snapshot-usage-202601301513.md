# ContextSnapshot 类职责与使用说明

## 1. 类职责（定位）
`ContextSnapshot` 是运行期上下文的结构化快照，承载任务执行时的核心信息，用于：
- 统一组织“任务意图、记忆、工具、预算、审计”等多维信息。
- 作为提示词装配与模型调用的上下文来源。
- 支撑裁剪、压缩、审计与事件追踪。

> 备注：类注释写的是“六层上下文”，但当前实现包含 9 组信息（运行元信息、边界、意图、工作记忆、领域知识、长期记忆、工具状态、预算状态、审计元数据）。


## 2. 构建与流转流程（关键链路）
1) `DefaultContextBuilder.build(...)` 创建并填充 `ContextSnapshot`。
2) `AgentRuntime.applyContextSnapshot(...)` 将快照写入运行时 `context`（键：`contextSnapshot`）。
3) 规划与提示词组装阶段读取快照：
   - `PlannerService.buildPromptAssemblyInput(...)`
   - `DefaultContextAssembler` / `DefaultPromptAssembler`
4) 工具调用与证据同步阶段读取/更新快照：
   - `ToolExecutor.syncSnapshotEvidence(...)`
5) 预算裁剪、压缩阶段基于快照做处理：
   - `DefaultContextPruner`、`DefaultContextTrimmer`、`ContextCompressionController`
6) 事件追踪与摘要统计：
   - `ContextEventPublisher` 生成快照摘要与统计信息。


## 3. 属性说明（功能 + 来源 + 使用位置）
### 3.1 snapshotId
- **功能**：快照唯一标识，用于事件追踪与审计定位。
- **来源**：`DefaultContextBuilder` 内部生成 UUID。
- **使用位置**：`AgentRuntime.applyContextSnapshot(...)` 注入 `context`；`ContextEventPublisher` 输出事件。

### 3.2 runtimeMeta
- **功能**：运行元信息（租户、用户、会话、工作流、请求链路、语言偏好、输出格式、token 预算、允许工具等）。
- **来源**：`DefaultContextBuilder.buildRuntimeMeta(...)`（从 `TenantContext` + `TaskRequest` + `context` 读取）。
- **使用位置**：
  - `PlannerService` 取 `tenantId`/`workflowId` 组装提示词。
  - `ContextCompressionController` 兜底获取 `tenantId`。
  - `ContextEventPublisher` 生成摘要统计。

### 3.3 roleBoundary
- **功能**：安全与合规边界（系统/开发策略、禁用动作、数据范围、风险等级、审批开关）。
- **来源**：`DefaultContextBuilder.buildRoleBoundary(...)`（读取 `context`：`systemPolicyId`、`developerPolicyId`、`forbiddenActions`、`dataScopes`、`riskLevel`、`requiresApproval`）。
- **使用位置**：
  - `DefaultPromptTemplate` 将 `riskLevel` 注入开发者提示。
  - `ContextEventPublisher` 摘要统计（风险等级、审批标志）。
  - 裁剪/压缩阶段用于 token 估算。

### 3.4 taskIntent
- **功能**：任务意图与成功标准（输入、成功准则、失败策略、输出要求、约束）。
- **来源**：`DefaultContextBuilder.buildTaskIntent(...)`（`query` + `context` 的 `successCriteria` / `failurePolicy` / `requiredOutput` / `constraints`）。
- **使用位置**：
  - `DefaultContextAssembler.resolveUserText(...)` 在无用户文本时用 `inputText`。
  - 裁剪/压缩阶段 token 估算。
  - 事件摘要统计。

### 3.5 workingMemory
- **功能**：工作记忆摘要（召回结果、关键事实、计划步骤、下一步、工具调用摘要、证据包）。
- **来源**：
  - `DefaultContextBuilder.buildWorkingMemory(...)` 基于记忆召回与观察结果构建。
  - `ToolExecutor.syncSnapshotEvidence(...)` 将证据包注入。
  - `ContextCompressionController` 可用压缩摘要覆盖工作记忆。
- **使用位置**：
  - 提示词装配与模型上下文。
  - 裁剪/压缩中作为重点裁剪对象。
  - `ContextEventPublisher` 统计摘要长度、关键事实、证据数量。

### 3.6 domainKnowledge
- **功能**：领域知识引用（如研究引用、外部资料）。
- **来源**：`DefaultContextBuilder.buildDomainKnowledge(...)`（读取 `context` 的 `citations` 或 `researchCitations`）。
- **使用位置**：
  - 裁剪/压缩、事件摘要统计。

### 3.7 longTermMemory
- **功能**：长期记忆引用列表（记忆引用、摘要片段、过期时间等）。
- **来源**：
  - `DefaultContextBuilder.buildLongTermMemory(...)` 从记忆召回结果与 `context.longTermMemoryRefs` 生成。
  - `ContextCompressionController` 会追加压缩记忆引用。
- **使用位置**：
  - 裁剪/压缩策略。
  - 事件摘要统计。

### 3.8 toolState
- **功能**：工具信息与使用状态（可用工具、已选工具、最近错误）。
- **来源**：
  - `DefaultContextBuilder.buildToolState(...)` 从 `ToolCatalog` 与 `context.selectedTools` / `lastToolError` 生成。
- **使用位置**：
  - 裁剪/压缩与事件摘要统计。
  - 可用于提示词装配时引导工具使用。

### 3.9 budgetState
- **功能**：token 预算状态（分配、已用、剩余）。
- **来源**：`DefaultContextBuilder.buildBudgetState(...)` 基于预算分配结果。
- **使用位置**：`ContextEventPublisher` 输出预算状态与摘要。

### 3.10 auditMetadata
- **功能**：审计元数据（版本、来源、创建时间）。
- **来源**：`DefaultContextBuilder.buildAuditMetadata(...)`。
- **使用位置**：`ContextEventPublisher` 事件载荷。


## 4. 如何使用（建议实践）
1) **入口即设置关键意图信息**：
   - 在 `TaskRequest.context` 中明确 `successCriteria`、`requiredOutput`、`constraints`，有助于规划与输出一致性。
2) **在合规场景设置边界**：
   - 使用 `riskLevel`、`requiresApproval`、`forbiddenActions`、`dataScopes` 控制行为边界，并在提示词中显式体现。
3) **尽量用“引用”而非大对象**：
   - 长文本、原始资料建议放在外部存储，`ContextSnapshot` 中只保留摘要/引用，避免提示词膨胀。
4) **保持会话与记忆一致性**：
   - 稳定使用 `sessionId`，确保召回结果能转化为 `workingMemory` 和 `longTermMemory`。
5) **工具策略明确化**：
   - 在 `context.selectedTools` 配置工具白名单；必要时配合 `skillName` 与 `toolChoice`。
6) **预算控制与压缩配合**：
   - 预算紧张时依赖 `ContextTrimmer` 与 `ContextCompressionController`，确保 `workingMemory` 可持续。


## 5. 不同场景示例
### 场景 A：普通问答（轻量上下文）
- **目标**：仅携带任务输入，不额外限制。
```json
{
  "query": "帮我总结今天的会议纪要",
  "sessionId": "s-001",
  "context": {
    "workflowId": "wf-001",
    "successCriteria": "输出三条要点+待办事项",
    "requiredOutput": "结构化列表"
  }
}
```
**效果**：构建 `taskIntent` 与 `runtimeMeta`，`workingMemory` 基于召回结果填充。

### 场景 B：合规审批（高风险）
- **目标**：风险显式、审批触发、限制动作。
```json
{
  "query": "导出客户数据并发送邮件",
  "sessionId": "s-002",
  "context": {
    "riskLevel": "high",
    "requiresApproval": true,
    "approvalSource": "policy",
    "forbiddenActions": ["export_raw_pii"],
    "dataScopes": ["customer_profile"],
    "successCriteria": "审批通过后生成脱敏报告"
  }
}
```
**效果**：`roleBoundary` 写入风险与审批标记，提示词会携带风险等级，事件链路可追踪审批。

### 场景 C：研究检索（带引用）
- **目标**：注入研究引用与工具状态。
```json
{
  "query": "调研三家竞品的定价",
  "sessionId": "s-003",
  "context": {
    "selectedTools": ["web.search", "web.open"],
    "researchCitations": [
      {"source": "example.com", "title": "pricing", "snippet": "..."}
    ]
  }
}
```
**效果**：`domainKnowledge` 持有引用，`toolState` 记录工具信息，利于后续提示词与审计追踪。

### 场景 D：长会话压缩（预算紧张）
- **目标**：保证长对话可持续，避免上下文爆炸。
```json
{
  "query": "继续上次方案，补充风险控制",
  "sessionId": "s-004",
  "context": {
    "contextPolicy": {
      "maxMemoryCount": 6,
      "retrievalPriority": ["SUMMARY", "RECENT"]
    },
    "planSteps": ["收集风险项", "评估影响", "制定对策"],
    "nextStep": "评估影响"
  }
}
```
**效果**：`workingMemory` 强化摘要，`ContextCompressionController` 在超预算时自动压缩并写入 `longTermMemory` 引用。


## 6. 常见误区与注意事项
- **不要在 `context` 中塞入大对象**：`context` 会进入工具参数与提示词装配，过大将导致预算超限。
- **不要覆盖 `contextSnapshot`**：它是运行时注入对象，应只读或谨慎增量修改。
- **风险与审批字段要成对配置**：`requiresApproval` 建议配合 `approvalSource` 使用，便于审计。
- **引用与证据优先**：长文本建议转为 `Citation` 或 `MemoryRef`，避免直接放入 `workingMemory.summary`。


## 7. 相关代码位置
- `src/main/java/com/example/agent/context/ContextSnapshot.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
- `src/main/java/com/example/agent/context/DefaultContextAssembler.java`
- `src/main/java/com/example/agent/model/DefaultPromptTemplate.java`
- `src/main/java/com/example/agent/agentcore/ToolExecutor.java`
- `src/main/java/com/example/agent/budget/ContextCompressionController.java`
- `src/main/java/com/example/agent/streaming/ContextEventPublisher.java`