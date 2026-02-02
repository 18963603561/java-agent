# 方案 B：分层摘要（推荐）落地改动清单与示例

> 依据：`doc/reflection-llm-prompt-output-analysis-202601301749.md` 与 `doc/prompt-output-injection-slimming-plan-202601302325.md` 的方案 B。

## 1. 目标与原则
1) 在执行阶段生成轻量摘要字段，提示词只注入摘要层，避免全量输出注入。
2) 输出结构保持兼容：原始 `output` 仍保留，但提示词侧不再直接使用。
3) 摘要可配置、可截断、可观测，便于灰度与回归。

## 2. 分层摘要结构（统一规范）
- **层 0：原始输出（Raw）**
  - 现有 `output` 原样保留，仅用于存档与必要的回放。
- **层 1：摘要层（Summary）**
  - `outputSummary`：对输出的关键字段/关键结论进行结构化摘要。
  - `toolResultSummary`：工具类输出的摘要（工具名、状态、结果字段、错误码等）。
  - `stepSummary`：可直接用于最终输出汇总的单步摘要（步骤类型、状态、关键信息）。
- **层 2：指纹层（Digest）**
  - `outputDigest`：包含输出字段列表、字段数量、字符数/长度区间、截断标记等。

> 约定：`stepSummary` 与 `outputSummary` 为优先注入字段；`outputDigest` 仅用于必要的质量判断或排查。

## 3. 涉及文件修改清单（方案 B）

### 3.1 新增/调整摘要生成
- `src/main/java/com/example/agent/runtime/StepRuntimeService.java`
  - 在 `completeStep(...)` 中统一生成摘要字段并写入 `output`（或扩展 `StepRecord`）。
  - 记录摘要生成耗时与截断信息日志。

- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - `recordStepOutput(...)`：向 `stepOutputs` 写入 `stepSummary` 而非完整 `output`。
  - `updateRuntimeContext(...)`：追加 `lastStepSummary`，避免把 `lastStepOutput` 传入提示词。

- `src/main/java/com/example/agent/runtime/StepRecord.java`
  - **推荐方式**：不新增字段，直接将摘要放入 `output` 内部键（无 DB 结构变更）。
  - **可选方式**：新增 `stepSummary`/`outputSummary` 字段（需要同步 `JdbcStepRecordRepository`）。

- `src/main/java/com/example/agent/runtime/StepResponse.java`
  - 若对外接口需要摘要，增加 `summary` 字段或在 `output` 中透出 `stepSummary`。

- 新增 `src/main/java/com/example/agent/runtime/StepOutputSummaryBuilder.java`
  - 统一生成 `outputSummary`/`toolResultSummary`/`stepSummary`/`outputDigest`。
  - 支持最大字符数、字段白名单、列表截断等配置。

### 3.2 替换提示词注入（摘要层）
- `src/main/java/com/example/agent/reflection/ReflectionService.java`
  - `buildReflectionPrompt(...)` 与 `tryRepairReflection(...)`：仅注入摘要层（`outputSummary`、`outputDigest`）。

- `src/main/java/com/example/agent/runtime/FinalOutputService.java`
  - `buildFinalPrompt(...)` 与 `tryRepairFinalOutput(...)`：注入 `stepSummary` 列表，避免完整 `output`。

- `src/main/java/com/example/agent/runtime/ReactLoopService.java`
  - `buildThinkPrompt(...)` 与 `tryRepairDecision(...)`：观察列表改为摘要化 `observationSummary`。

- `src/main/java/com/example/agent/planning/PlannerService.java`
  - `buildPlanPrompt(...)`：上下文改为 `contextSummary`（token/字段/工具概览）。

- `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java`
  - `buildPrompt(...)`：`step.getInput()` 替换为白名单摘要（query/goal/constraints/tools）。

- `src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java`
  - `buildPrompt(...)`：`previousSteps` 改为 `stepSummaries`，保留最近 N 条摘要。

### 3.3 配置与可观测性
- `src/main/java/com/example/agent/reflection/ReflectionProperties.java`
- `src/main/java/com/example/agent/runtime/ReactRuntimeProperties.java`
- `src/main/java/com/example/agent/runtime/FinalOutputProperties.java`（如不存在则新增）
  - 新增摘要配置项：
    - `summary.maxChars`
    - `summary.maxListItems`
    - `summary.maxFieldChars`
    - `summary.enable`

- `src/main/resources/application.yml`
  - 增加默认配置与注释。

### 3.4 单元测试建议
- `src/test/java/com/example/agent/runtime/StepOutputSummaryBuilderTest.java`
- `src/test/java/com/example/agent/reflection/ReflectionServiceTest.java`
- `src/test/java/com/example/agent/runtime/FinalOutputServiceTest.java`
- `src/test/java/com/example/agent/runtime/ReactLoopServiceTest.java`

## 4. 字段示例（统一字段约定）
- `outputSummary`：摘要对象（可为 Map）
- `toolResultSummary`：工具输出摘要
- `stepSummary`：单步摘要
- `outputDigest`：字段/体量指纹
- `truncated`：是否截断（布尔）

示例结构：
```json
{
  "outputSummary": {
    "status": "COMPLETED",
    "answer": "...",
    "errorCode": null,
    "keyFields": ["answer", "confidence", "citationsCount"],
    "sample": "..."
  },
  "toolResultSummary": {
    "tool": "demo_tool",
    "success": true,
    "resultKeys": ["items", "total"],
    "sample": "items[0].title=..."
  },
  "stepSummary": {
    "stepId": "s-1",
    "type": "TOOL",
    "status": "COMPLETED",
    "summary": "tool=demo_tool, items=3, total=3"
  },
  "outputDigest": {
    "keyCount": 12,
    "keys": ["items", "total", "tokenUsage"],
    "charCount": 3280,
    "truncated": true
  }
}
```

## 5. 优化前/优化后场景示例

### 场景 A：反思（Reflection）
**优化前（REFLECTION_CONTEXT_JSON 注入）**
```json
{
  "stepType": "TOOL",
  "attempt": 1,
  "output": {
    "contextSnapshot": {"...": "超大对象"},
    "contextBudget": {"...": "超大对象"},
    "evidencePack": {"...": "超大对象"},
    "tokenUsage": {"...": "超大对象"},
    "result": {"items": ["..."], "total": 120}
  }
}
```

**优化后（仅摘要层）**
```json
{
  "stepType": "TOOL",
  "attempt": 1,
  "outputSummary": {
    "status": "COMPLETED",
    "resultKeys": ["items", "total"],
    "itemsCount": 120,
    "sample": "items[0].title=..."
  },
  "outputDigest": {
    "keyCount": 5,
    "charCount": 4800,
    "truncated": true
  }
}
```

### 场景 B：最终输出汇总（FinalOutput）
**优化前（FINAL_CONTEXT_JSON 注入）**
```json
{
  "query": "...",
  "planSummary": "...",
  "steps": [
    {"stepId": "s1", "type": "TOOL", "output": {"...": "超大对象"}},
    {"stepId": "s2", "type": "LLM", "output": {"answer": "...", "contextSnapshot": "..."}}
  ]
}
```

**优化后（仅 stepSummary 列表）**
```json
{
  "query": "...",
  "planSummary": "...",
  "steps": [
    {"stepId": "s1", "type": "TOOL", "status": "COMPLETED", "summary": "tool=demo_tool, items=120"},
    {"stepId": "s2", "type": "LLM", "status": "COMPLETED", "summary": "answer=..."}
  ]
}
```

### 场景 C：ReAct 决策
**优化前（REACT_CONTEXT_JSON 注入）**
```json
{
  "query": "...",
  "iteration": 3,
  "observations": [
    {"content": "{"large":"tool output ..."}", "tool": "search"}
  ]
}
```

**优化后（观察摘要）**
```json
{
  "query": "...",
  "iteration": 3,
  "observations": [
    {"tool": "search", "summary": "status=OK, items=5", "contentSize": 3820, "truncated": true}
  ]
}
```

### 场景 D：规划（Planner）
**优化前（PLAN_CONTEXT_JSON 注入）**
```json
{
  "query": "...",
  "context": {
    "contextSnapshot": {"...": "超大对象"},
    "contextBudget": {"...": "超大对象"},
    "evidencePack": {"...": "超大对象"}
  }
}
```

**优化后（contextSummary）**
```json
{
  "query": "...",
  "contextSummary": {
    "snapshotId": "snap-1",
    "tokenBudget": 2048,
    "memoryItems": 3,
    "tools": ["search", "fetch"],
    "evidenceCount": 2
  }
}
```

## 6. 兼容性与落地注意事项
1) 提示词输入缩减不改变模型输出结构，接口兼容风险低。
2) 若有逻辑依赖原始 `output` 字段，应保留原始输出存档，仅提示词侧不使用。
3) 建议提供配置开关 `summary.enable` 以便灰度回滚。

## 7. 回归验证建议
- 反思输出解析成功率与重试率对比。
- 最终输出 JSON 成功率与回答质量抽样对比。
- ReAct 决策稳定性与工具调用正确率。
- 规划输出结构稳定性与 token 成本统计。
