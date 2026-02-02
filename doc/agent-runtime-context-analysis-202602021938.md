# AgentRuntime 运行上下文与步骤输入关系分析

## 1. 范围说明
- 分析对象：`AgentRuntime.run` 及其内部对 `runtimeContext`、`stepInput`、`TaskRequest` 的使用与关系。
- 目标：解释三者的职责边界、数据流向、使用场景与差异。
- 依据代码：`src/main/java/com/example/agent/runtime/AgentRuntime.java`。

## 2. 关键对象定义

### 2.1 `runtimeContext`
- 类型：`Map<String, Object>`。
- 定位：运行期共享上下文，贯穿整个任务执行生命周期。
- 主要来源：
  - `request.getContext()` 作为基础上下文。
  - `request.getToolChoice()` 写入 `toolChoice`。
  - `workflowId` 写入 `workflowId`。
  - 记忆召回结果通过 `applyMemoryContext(...)` 注入。
  - 上下文构建结果通过 `applyContextSnapshot(...)` 注入。
- 常见写入内容：
  - `contextSnapshot` 与 `snapshotId`。
  - `contextBudget`、`contextPrune`。
  - 证据包 `EvidencePackService.CONTEXT_EVIDENCE_PACK`。
  - 最近步骤信息：`lastStepId`、`lastStepType`、`lastStepSummary`、`lastStepOutput`、`lastOutputSize`。
  - 审批相关标记：`evaluationApprovalGranted`。
- 核心作用：
  - 作为全流程共享状态容器。
  - 作为每个步骤输入合并的基座。
  - 为后续步骤提供“上一阶段摘要”。

### 2.2 `TaskRequest` 与 `effectiveRequest`
- `TaskRequest`：外部请求载体，包含 `query`、`sessionId`、`skillName`、`idempotencyKey`、`toolChoice`、`context` 等字段。
- `effectiveRequest`：由 `buildRequestWithContext(request, runtimeContext)` 构造的副本。
  - 目的：避免修改原始请求对象。
  - 关键差异：`effectiveRequest.setContext(runtimeContext)`，上下文改为运行期共享上下文。
- 核心作用：
  - 作为规划与步骤执行的标准输入对象。
  - 作为记忆写入、最终输出汇总的“运行期请求”。

### 2.3 `stepInput`
- 类型：`Map<String, Object>`。
- 生成方式：`mergeStepInput(step, runtimeContext)`。
  - 先写入 `runtimeContext`。
  - 再写入 `step.getInput()`，步骤输入覆盖同名键。
  - 调用 `promoteApprovalFields(...)` 将嵌套审批字段提升到顶层。
- 核心作用：
  - 作为单个步骤的执行输入。
  - 传入 `llmStepService.run(...)`、`executeToolStep(...)` 等执行路径。
  - 在 `ReactLoopService` 场景下可被转换为新的 `TaskRequest`。

## 3. `run` 方法中的关键流程

1) 初始化 `runtimeContext`
- 合并 `request.getContext()`。
- 写入 `toolChoice` 与 `workflowId`。

2) 记忆召回与上下文快照
- 通过 `memoryRecallService.recall(...)` 获取召回结果。
- `applyMemoryContext(...)` 将召回信息写入 `runtimeContext`。
- `buildContextSnapshot(...)` 构建快照与裁剪结果。
- `applyContextSnapshot(...)` 将 `contextSnapshot`、`snapshotId`、`contextBudget`、`contextPrune` 写入 `runtimeContext`。

3) 构造运行期请求
- `effectiveRequest = buildRequestWithContext(request, runtimeContext)`。
- `effectiveRequest` 替代原请求参与规划与执行。

4) 执行步骤循环
- 每个步骤调用 `mergeStepInput(step, runtimeContext)` 得到 `stepInput`。
- `stepInput` 进入执行控制与审批逻辑。
- 步骤执行后调用 `updateRuntimeContext(...)`，写入最近步骤摘要与输出信息。
- 若触发重规划，使用 `rebuildRequestForReplan(...)` 生成新请求再规划。

5) 反应式循环的特殊请求构造
- `ReactLoopService` 路径中使用 `buildRequestWithContext(request, stepInput)`，将步骤输入作为新的请求上下文。
- 该路径强调步骤级上下文与原请求的组合使用。

## 4. 对象关系与差异

### 4.1 关系链条
- `TaskRequest.context` → 合并到 `runtimeContext`。
- `runtimeContext` → 合并生成 `stepInput`。
- `runtimeContext` → 注入 `effectiveRequest.context`。
- `stepInput` → 作为步骤执行输入，并在部分场景转换为新的 `TaskRequest.context`。

### 4.2 差异总结
| 对象 | 生命周期 | 来源 | 主要用途 |
| --- | --- | --- | --- |
| `runtimeContext` | 全流程 | 请求上下文与运行期补充 | 共享状态、快照与预算信息、步骤摘要积累 |
| `TaskRequest` | 入参级 | 外部调用方 | 任务初始载体与对外参数封装 |
| `effectiveRequest` | 运行期 | `TaskRequest` 副本 | 运行期执行的标准请求对象 |
| `stepInput` | 步骤级 | `runtimeContext` + `step.getInput()` | 单步执行输入、审批判断、工具参数合并 |

### 4.3 关键差异点
- `runtimeContext` 是“可变共享状态”，贯穿整个流程并在每步后更新。
- `stepInput` 是“只读视图”，以 `runtimeContext` 为基座并叠加步骤输入，覆盖同名键。
- `TaskRequest` 代表“请求边界”，原始对象不被修改，执行期间使用 `effectiveRequest`。

## 5. 使用场景说明
- `runtimeContext` 使用场景：
  - 规划前的上下文快照构建与预算裁剪结果承载。
  - 跨步骤的审批状态与摘要信息保留。
  - 为后续步骤提供统一上下文入口。
- `stepInput` 使用场景：
  - 作为步骤执行输入，参与 `LLM`、工具调用、研究、反思等流程。
  - 在反应式循环中作为新的上下文载体。
- `TaskRequest` 使用场景：
  - 作为外部调用接口与日志、持久化的请求凭据。
  - 通过副本 `effectiveRequest` 在运行期被系统内部使用。

## 6. 关键方法与位置
- `run(...)`：主流程入口。
- `buildRequestWithContext(...)`：构造运行期请求副本。
- `mergeStepInput(...)`：合并步骤输入与运行期上下文。
- `applyContextSnapshot(...)`：将快照与预算裁剪结果注入运行期上下文。
- `updateRuntimeContext(...)`：写入最近步骤摘要与输出信息。

## 7. 结论
- `runtimeContext` 是运行期主上下文，负责在全流程内积累与共享状态。
- `stepInput` 是每步执行的局部输入视图，强调步骤级覆盖与审批字段提升。
- `TaskRequest` 是请求边界对象，运行期通过 `effectiveRequest` 与 `runtimeContext` 绑定。
- 三者形成“请求输入 → 运行上下文 → 步骤输入”的明确数据通路。
