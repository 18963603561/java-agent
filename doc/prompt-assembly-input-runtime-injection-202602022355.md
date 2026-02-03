# 运行阶段注入 PromptAssemblyInput 的代码改动分析

时间：2026-02-02 23:55

## 1. 现状结论
- `DefaultPromptAssembler.build(...)` 只会从 `stepInput` 或 `taskRequest.context` 读取 `promptAssemblyInput`。
- 当前仅 `PlannerService.applyPromptBundle(...)` 会生成并写入 `promptAssemblyInput`。
- 运行阶段多数调用链路没有注入 `promptAssemblyInput`，因此会走 `buildLegacy(...)`。

## 2. 运行阶段调用点梳理
以下位置会调用 `promptAssembler.build(...)`：
- `LlmStepService.applyPromptBundle(...)`：传入 `stepInput`。
- `ReactLoopService.applyPromptBundle(...)`：传入 `stepInput`。
- `ChainOfThoughtService.applyPromptBundle(...)`：传入 `stepInput`。
- `ReflectionService.applyPromptBundle(...)`：传入 `step.getInput()`，不是合并后的运行上下文。
- `MultiAgentCoordinator.applyPromptBundle(...)`：传入 `inputSummary`，通常不是运行上下文。
- `FinalOutputService.applyPromptBundle(...)`：传入 `taskRequest`，`stepInput` 为 `null`。

因此要让运行阶段走主路径，必须确保以上调用点能够拿到 `promptAssemblyInput`。

## 3. 可选改造路径与涉及修改
### 方案一：统一入口在 `DefaultPromptAssembler.build(...)`
**思路**：当 `promptAssemblyInput` 为空时，内部自行构建并转主路径。

**改动点**：
- `DefaultPromptAssembler`：
  - 新增依赖 `ContextAssembler`。
  - 在 `build(...)` 内部增加“缺失时构建”逻辑：
    - 从 `stepInput` 或 `taskRequest.context` 读取 `contextSnapshot`、`contextBudget`、`contextPrune`。
    - 调用 `ContextAssembler.assemble(...)` 生成 `PromptAssemblyInput`。
    - `userText` 传入当前 `prompt`，避免被快照输入覆盖。
  - `buildLegacy(...)` 仅在装配器不可用时兜底。

**优点**：改动集中，运行链路无需逐个改。
**风险**：需要调整 `DefaultPromptAssembler` 构造器注入，涉及依赖装配；需要确保不会破坏规划链路。

### 方案二：在运行链路显式注入（每次调用）
**思路**：在每个 `promptAssembler.build(...)` 前生成并写入 `promptAssemblyInput`。

**改动点**：
- `LlmStepService.applyPromptBundle(...)`：
  - 注入 `ContextAssembler`。
  - 在调用前构建 `PromptAssemblyInput`，写入 `stepInput`。
- `ReactLoopService.applyPromptBundle(...)`：同上。
- `ChainOfThoughtService.applyPromptBundle(...)`：同上。
- `ReflectionService.applyPromptBundle(...)`：
  - 需要能拿到运行上下文或在 `AgentRuntime` 调用处将合并后的 `stepInput` 透传进去。
- `MultiAgentCoordinator.applyPromptBundle(...)`：
  - 需要把运行上下文或 `promptAssemblyInput` 显式传入 `inputSummary`。
- `FinalOutputService.applyPromptBundle(...)`：
  - 需要把 `promptAssemblyInput` 写入 `taskRequest.context`，或改为传入 `stepInput`。

**优点**：不改 `DefaultPromptAssembler`，行为显式。
**风险**：改动点分散，维护成本高。

### 方案三：在 `AgentRuntime` 统一注入到运行上下文
**思路**：运行开始构建一次 `promptAssemblyInput`，放入 `runtimeContext`，后续 `mergeStepInput(...)` 自动带出。

**改动点**：
- `AgentRuntime`：
  - 注入 `ContextAssembler`。
  - 在 `applyContextSnapshot(...)` 后新增 `buildPromptAssemblyInputForRuntime(...)`，写入 `runtimeContext`。
  - `buildRequestWithContext(...)` 会把 `runtimeContext` 写进 `taskRequest.context`，`FinalOutputService` 也能读取。

**关键注意**：
- `DefaultContextAssembler.assemble(...)` 会在 `userText` 为空时使用 `snapshot.taskIntent.inputText` 兜底。
- 若只构建一次并写入 `runtimeContext`，后续各步骤的 `prompt` 可能被该兜底覆盖，导致提示词不一致。

**需要配套的修改**（二选一）：
1) 扩展 `ContextAssembler` 增加“不填充 `userText`”的入口，运行阶段使用该入口；
2) 在每次调用前复制 `promptAssemblyInput` 并强制 `userText = prompt`。

## 4. 建议的最小改动清单
若目标是“运行阶段全部走主路径且不改变提示词内容”，推荐以下方向：
1) 采用“统一入口在 `DefaultPromptAssembler.build(...)`”方案；
2) 新增 `ContextAssembler` 依赖并在 `build(...)` 内部完成 `PromptAssemblyInput` 构建；
3) 读取 `contextSnapshot`、`contextBudget`、`contextPrune` 仅使用现有运行上下文键，不新增结构；
4) `userText` 使用当前 `prompt` 参数，避免快照输入覆盖。

如果不希望改动 `DefaultPromptAssembler`，则必须在以下类中逐一注入并传递 `promptAssemblyInput`：
- `LlmStepService`
- `ReactLoopService`
- `ChainOfThoughtService`
- `ReflectionService`
- `MultiAgentCoordinator`
- `FinalOutputService`

## 5. 结论
- 运行阶段要走主路径，必须在运行链路显式注入 `promptAssemblyInput`。
- 最小侵入的方式是让 `DefaultPromptAssembler.build(...)` 在缺失时自行构建，统一入口最清晰。
- 若坚持在 `AgentRuntime` 预先注入，需要额外处理 `userText` 覆盖风险。