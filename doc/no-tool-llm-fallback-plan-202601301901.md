# 无工具时默认走大模型的方案分析

## 1. 需求概述
希望实现以下行为：
- 请求中**明确要求不使用工具**时，不自动指定默认工具。
- 大模型自动检测工具**未选择/未匹配到合适工具**时，不回退到默认工具。
- 在无工具可用的情况下，**直接调用大模型生成答案并正常返回**。

## 2. 当前实现的关键行为与问题
### 2.1 规划阶段（PlannerService）
- `buildHeuristicPlan(...)` 在未命中其它策略时，**总会生成一个 `TOOL` 步骤**。
- 该 `TOOL` 步骤即使没有指定工具名，也会被后续运行时解析为默认工具。

### 2.2 运行时工具兜底（AgentRuntime）
- `resolveToolName(...)` 当未指定工具时**返回固定默认值 `demo_tool`**。
- 结果是：即使调用方未指定工具或模型未选择工具，也会执行 `demo_tool`。

### 2.3 模型工具注入（ModelToolResolver）
- 若请求未显式设置 `toolChoice`，且工具列表非空，会自动设置 `toolChoice=auto`。
- 即使调用方希望“无工具”，仍可能被注入工具候选。

### 2.4 本地兜底规划（DefaultModelProvider）
- `buildLocalPlan(...)` 在本地兜底时默认将 `tool` 设置为 `demo_tool`。
- 这会在无工具场景下继续触发工具执行。

## 3. 触发场景与现象
### 3.1 明确不使用工具的请求
- 已支持 `TaskRequest.toolChoice` 传入 `ModelToolChoice.none()`，但实际执行仍会进入工具步骤。

### 3.2 模型未选择工具
- LLM 规划结果若返回 `TOOL` 步骤但不含 tool 名称，最终仍会落到 `demo_tool`。
- 或 LLM 未返回工具步骤，但规则规划仍会补一个工具步骤。

## 4. 目标行为（建议定义）
1) **显式禁用工具**：
   - 请求含 `toolChoice=none` 或 `context.disableTools=true` 时：
     - 不注入工具定义
     - 不生成 `TOOL` 步骤
     - 直接走大模型输出
2) **无工具可用或未匹配工具**：
   - 若工具名为空/不可用/不在允许列表：
     - 不回退默认工具
     - 走大模型输出

## 5. 需要修改的关键位置（建议方案）
### 5.1 请求层：明确无工具标记
- 建议支持以下任一方式：
  - `TaskRequest.toolChoice = none`
  - `TaskRequest.context.disableTools = true`
  - `TaskRequest.context.toolChoice = "none"`（可选）
- 需要在入口处对 `context.toolChoice` 做解析并映射到 `TaskRequest.toolChoice`（如需兼容）。

### 5.2 模型工具注入（ModelToolResolver）
- 若解析到 `toolChoice=none`：
  - **跳过工具注入**（`request.setTools(empty)` 或不设置 tools）。
  - **禁止默认 auto**。
- 避免“工具候选存在但被强行注入”的场景。

### 5.3 规划阶段（PlannerService）
- 在 `buildHeuristicPlan(...)` 进入默认 `TOOL` 步骤前，增加判断：
  - 若 `toolChoice=none` 或 `disableTools=true`：
    - **不生成工具步骤**。
    - 返回空步骤计划或新建 `LLM`/`ANSWER` 步骤（推荐）。

### 5.4 运行时工具兜底（AgentRuntime）
- 修改 `resolveToolName(...)`：
  - **取消 `demo_tool` 默认兜底**。
  - 未解析到工具时返回 `null`。
- 在执行 `TOOL` 步骤前加入分支：
  - 若工具为空，直接调用大模型输出（走 `FinalOutputService`）。

### 5.5 本地兜底规划（DefaultModelProvider）
- `buildLocalPlan(...)` 不应默认 `demo_tool`。
- 若无工具，应生成空步骤或 `LLM` 步骤。

## 6. 建议落地路径
### 方案 A（最小改动）
1) 支持 `toolChoice=none` 作为显式无工具标记。
2) `PlannerService.buildHeuristicPlan`：检测到 `toolChoice=none` 时返回空计划。
3) `AgentRuntime` 在 plan 为空时，直接调用 `FinalOutputService` 并返回。
4) `resolveToolName` 去掉 `demo_tool` 默认兜底。

优点：改动最少、实现快。  
缺点：空计划场景需要在 `AgentRuntime` 增加专门处理。

### 方案 B（结构化改动，推荐）
1) 新增 `StepType=LLM` / `ANSWER` 步骤。

2) `PlannerService` 在无工具场景生成 `LLM` 步骤。
3) `AgentRuntime.executeStep` 中新增 `LLM` 分支，调用模型直接生成输出。
4) 最终输出汇总时，若最后一步已经包含 `answer`，可直接返回或跳过再次总结。

优点：执行路径清晰、可观测性更好。  
缺点：涉及新增步骤类型与执行逻辑。

## 7. 风险与兼容性
- 现有默认 `demo_tool` 可能被测试或流程依赖，移除需明确评估。
- 建议新增配置开关：
  - `agent.tool.default-enabled=true/false`（默认保持旧行为，可逐步切换）。

## 8. 示例请求（显式无工具）
```json
{
  "query": "请总结一下今天的会议重点",
  "toolChoice": {"mode": "none"}
}
```

## 9. 结论
当前系统会在无工具指令下**强制回落到 `demo_tool`**，与“无工具时走大模型”的目标不一致。  
若要实现期望行为，需要在**工具注入、规划生成、运行时兜底、本地兜底规划**四个关键节点进行联动修改。推荐采用 **方案 B**（新增 `LLM` 步骤）以获得更清晰的执行路径与更低的维护成本。

