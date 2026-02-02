# PlannerService.buildHeuristicPlan 策略解析

## 1. 适用范围与入口
`buildHeuristicPlan(...)` 是规则规划的兜底路径，仅在规划流程中出现：当 LLM 规划不可用或解析失败时，才会进入该方法生成步骤。其上游入口为 `PlannerService.plan(...)`。  
相关实现位置：
- `src/main/java/com/example/agent/planning/PlannerService.java`

## 2. 策略触发顺序（实际执行顺序）
该方法按固定顺序决定步骤类型，先命中先返回或继续累积：
1) **链式推理（COT）**：如果显式请求 COT，直接生成 `CHAIN_OF_THOUGHT` 步骤并返回。
2) **思维树（TOT）**：若需要思维树，先插入 `THOUGHT_TREE` 步骤。
3) **多智能体**：`strategy=multi_agent` 或 `multi-agent` 时插入 `MULTI_AGENT`。
4) **辩论**：`strategy=debate` 时插入 `DEBATE`。
5) **研究**：`mode=deep_research` 或 `strategy=research` 时插入 `RESEARCH`。
6) **ReAct**：命中 `react` 条件时插入 `REACT`，并**立即返回**。
7) **默认工具**：未命中以上策略时，生成 `TOOL` 步骤。

这意味着：
- COT 一旦命中，会直接返回，不再叠加其它策略。
- `REACT` 命中后也会提前返回。
- `THOUGHT_TREE` 可以与 `MULTI_AGENT`/`DEBATE`/`RESEARCH` 叠加（作为前置步骤）。

## 3. 关键变量与来源
`buildHeuristicPlan(...)` 主要依赖以下变量：
- `query`：由 `TaskRequest.query` 提供，用于复杂度估计与步骤输入。
- `context`：由 `TaskRequest.context` 合并得到，内部读取的控制字段有：
  - `mode`
  - `strategy`
  - `cognitive_strategy`
  - `executionStrategy`
  - `react` / `reactEnabled`
  - `tool` / `fallbackTool`
  - `planSteps` / `planDependencies`（写回）
  - `executionStrategy` / `cognitiveStrategy`（写回）

## 4. 各策略触发条件与支持变量
### 4.1 COT（链式推理）
触发条件：`mode`、`strategy` 或 `cognitive_strategy` 为以下任意值：
- `cot`
- `chain_of_thought`
- `chain-of-thought`

触发后生成：
- `StepRequest(type=CHAIN_OF_THOUGHT, input={question, context, stepKey})`

### 4.2 TOT（思维树）
触发条件：
- `cognitive_strategy` 为 `tree_of_thoughts` 或 `tot`；或
- 查询复杂度 `complexityScore >= 0.8`（自动触发）。

触发后生成：
- `StepRequest(type=THOUGHT_TREE, input={prompt, stepKey, critical, strategy})`

### 4.3 ReAct
触发条件：满足任一项：
- `mode=react` 或 `strategy=react`
- `context.react=true` 或 `context.reactEnabled=true`（字符串或布尔）

触发后生成：
- `StepRequest(type=REACT, input={query, context, stepKey})`

### 4.4 研究（RESEARCH）
触发条件：
- `mode=deep_research` 或 `strategy=research`

### 4.5 多智能体（MULTI_AGENT）
触发条件：
- `strategy=multi_agent` 或 `strategy=multi-agent`

### 4.6 辩论（DEBATE）
触发条件：
- `strategy=debate`

## 5. 默认策略与自动选择
### 5.1 复杂度评估
复杂度来自 `estimateComplexity(query)`：
- 主要依据 **文本长度** 与 **分句数量**，综合得到 0~1 的分数。
- `query` 为空时默认为 0.1。

### 5.2 认知策略自动选择
当未显式指定 `strategy` 或 `cognitive_strategy` 时：
- `complexityScore >= 0.7` -> `tree_of_thoughts`
- `complexityScore >= 0.4` -> `reflection`
- 否则 `simple`

这会影响：
- 是否进入思维树（`complexityScore >= 0.8` 或显式 `tree_of_thoughts/tot`）
- 默认工具步骤的 `strategy` 字段

### 5.3 执行策略自动选择
当前实现中，无论复杂度如何，`resolveExecutionStrategy(...)` 都返回 `sequential`。

## 6. 是否可通过“查询入参”控制
### 6.1 可控入口
- **显式控制**：通过 `TaskRequest.context` 传入 `mode` / `strategy` / `cognitive_strategy` / `react` 等字段。
- **隐式控制**：仅通过 `query` 内容影响复杂度，从而影响默认的 `cognitive_strategy` 和是否触发 `THOUGHT_TREE`。

### 6.2 不能通过 query 明确指定模式
仅靠 `query` 文本不能显式指定 `cot`/`react`/`deep_research` 等模式；这些必须通过 `context` 传入。

## 7. 示例
### 7.1 强制 COT
```json
{
  "query": "请分步骤推理并给结论",
  "context": {
    "mode": "cot"
  }
}
```

### 7.2 高复杂度自动触发 TOT
```json
{
  "query": "请对一个跨系统数据同步方案做可行性分析，包含失败恢复、性能与边界条件"
}
```
> 该请求会因复杂度提高而倾向选择 `tree_of_thoughts`，并可能触发 `THOUGHT_TREE`。

### 7.3 显式 ReAct
```json
{
  "query": "根据库存、价格和促销条件给出采购建议",
  "context": {
    "mode": "react",
    "reactEnabled": true
  }
}
```

### 7.4 深度研究 + 多智能体
```json
{
  "query": "对比三家厂商的产品差异并给出推荐",
  "context": {
    "mode": "deep_research",
    "strategy": "multi_agent"
  }
}
```

## 8. 注意事项
- 以上策略仅在**规则规划**路径生效；若 LLM 规划成功，则不会进入该方法。
- `mode`/`strategy` 的控制权来自调用方上下文，建议统一在请求入口封装，避免在运行期随意改写。
- `executionStrategy` 目前固定为 `sequential`，如需并行/分支执行需扩展实现。

## 9. 关键代码参考位置
- `PlannerService.plan(...)`：`src/main/java/com/example/agent/planning/PlannerService.java`
- `PlannerService.buildHeuristicPlan(...)`：`src/main/java/com/example/agent/planning/PlannerService.java`
- `PlannerService.resolveCognitiveStrategy(...)` / `needsThoughtTree(...)` / `shouldUseReact(...)`

