# 工具结果保真输出与汇总方案

## 一、背景与问题
- 现有流程中，工具执行后会进入大模型汇总路径。具体位置包括：
  - `AgentRuntime.tryExecuteDirectToolStep` 调用 `llmStepService.summarizeDirectToolResult`。
  - `LlmStepService.summarizeToolResult` 对工具结果进行二次汇总。
  - `FinalOutputService.finalizeOutput` 在最终输出阶段再次汇总。
- 现象：工具原始结果准确，但汇总输出出现含义偏差或事实变化。
- 结论：工具结果属于事实来源，若无保真约束，汇总易引入理解偏差。

## 二、目标
1) 工具结果保持为事实基线，不能被汇总改写。
2) 允许在可控场景下做摘要，但必须可追溯、可校验、可回退。
3) 支持按工具、按步骤或按请求配置策略。

## 三、方案总览（四种模式）
- `RAW`：不走大模型汇总，直接返回工具结果。
- `TEMPLATE`：使用确定性模板生成摘要，避免模型理解偏差。
- `LLM_STRICT`：大模型输出结构化摘要，但必须附带证据路径并经过一致性校验。
- `LLM_SUMMARY`：保留现状，用于低风险场景。

## 四、策略决策与配置
### 4.1 新增配置与上下文字段
- 请求级别：`context.toolSummaryMode`、`context.finalSummaryMode`。
- 步骤级别：`step.input.summaryMode` 或 `step.input.rawOnly=true`。
- 工具级别：`toolSummaryMode` 白名单或黑名单（例如高风险工具默认 `RAW`）。

### 4.2 策略优先级
1) `step.input.summaryMode` 或 `rawOnly`。
2) `context.toolSummaryMode`。
3) 工具级别配置。
4) 默认值（建议为 `LLM_STRICT` 或 `RAW`）。

## 五、执行流程改造要点
### 5.1 直达工具路径
- 在 `tryExecuteDirectToolStep` 中根据模式选择：
  - `RAW`：直接返回 `toolResult`，并标记 `source=direct_tool_raw`。
  - `TEMPLATE`：模板拼接摘要，同时保留 `rawResult`。
  - `LLM_STRICT`：调用汇总后进入一致性校验，失败则回退 `RAW`。

### 5.2 大模型工具路径
- 在 `summarizeToolResult` 中：
  - 将工具原始结果写入 `rawResult`。
  - 强制输出证据字段 `evidence`（例如 `$.result.items[0].id`）。
  - 进入一致性校验，失败回退 `RAW`。

### 5.3 最终汇总
- 若最终可用结果来自 `RAW` 且标记 `finalRawOnly=true`：
  - 直接输出工具结果或模板输出。
- 若仍需最终摘要：
  - 使用 `LLM_STRICT` 的证据机制与一致性校验。

## 六、保真校验与回退
### 6.1 一致性校验规则
- 数值、日期、标识符必须存在于 `rawResult` 中。
- 关键字段数量、列表长度、计数类信息必须与 `rawResult` 一致。
- 若摘要包含外推结论，必须能由 `evidence` 路径定位。

### 6.2 回退策略
- 校验失败：记录告警并直接返回 `RAW` 结果。
- 模型解析失败：返回 `RAW` 或 `TEMPLATE`。

## 七、输出结构建议
- `answer`：面向用户的摘要。
- `highlights`：关键信息摘要。
- `confidence`：置信度。
- `evidence`：证据路径数组。
- `rawResult`：工具原始结果。

## 八、日志与可观测性
- 记录摘要模式、工具名、是否校验通过。
- 记录校验失败原因与回退次数。
- 输出对比指标：`summary_mismatch_count`、`summary_fallback_count`。

## 九、测试与验收
- 单元测试：校验器、模板输出、回退逻辑。
- 集成测试：直达工具与模型工具两条路径。
- 回归用例：关键工具在 `RAW` 模式下输出一致。

## 十、落地建议与迁移节奏
1) 先对高风险工具启用 `RAW` 或 `LLM_STRICT`。
2) 观察告警与回退比例，逐步扩大范围。
3) 保留开关，支持快速回滚。