# steps 为空与 summary disabled 问题分析报告

## 结论概述
- 本次请求设置了 toolChoice.mode=NONE，规划层判定“禁用工具”，因此生成 LLM 类型步骤。
- LLM 步骤内部直接调用 FinalOutputService，并显式传入空的 steps 列表，所以构造的 FINAL_CONTEXT_JSON 中 steps=[] 属于设计行为。
- 输出结果中的 summary="(summary disabled)" 来自步骤摘要开关关闭（agent.summary.enable=false）以及归一化逻辑的兜底填充。

## 复现步骤
1) 启动项目（跳过测试编译）：
   - 命令：mvn -q -Dmaven.test.skip=true spring-boot:run
2) 请求体：见文件 logs/request-20260131.json
3) 发送请求：
   - curl.exe -H "X-API-Key: demo-key" -H "X-Tenant-Id: tenant-demo-1" -H "Content-Type: application/json" -X POST http://localhost:8080/api/v1/tasks --data-binary @logs/request-20260131.json

## 实际响应（关键片段）
- 本次请求返回的步骤摘要与最终输出（节选）：
  - steps[0].output.stepSummary.summary = "(summary disabled)"
  - finalOutput.stepSummary.summary = "(summary disabled)"
- 响应中 steps 列表仅包含 1 个 LLM 步骤。

## 现象 1：构造提示词中 steps=[] 的原因
### 触发链路
1) 请求体包含 toolChoice.mode=NONE。
2) PlannerService.isToolsDisabled 识别工具禁用，走“LLM 步骤”分支，生成单步 LLM 计划。
3) LLM 步骤执行时，AgentRuntime.executeLlmStep 调用 FinalOutputService.finalizeOutput 时传入 List.of() 作为 steps。
4) FinalOutputService.buildFinalPrompt 使用 buildStepSummaries(stepOutputs)，因传入为空，最终提示词中的 steps=[]。

### 关键代码位置
- 规划阶段禁用工具：src/main/java/com/example/agent/planning/PlannerService.java
  - 方法：isToolsDisabled(...)、buildHeuristicPlan(...)
- LLM 步骤直接调用最终输出并传空 steps：src/main/java/com/example/agent/runtime/AgentRuntime.java
  - 方法：executeLlmStep(...)
- 最终提示词组装：src/main/java/com/example/agent/runtime/FinalOutputService.java
  - 方法：buildFinalPrompt(...)

### 结论
steps=[] 并非数据丢失，而是 LLM 步骤执行路径里显式传入空 steps 导致，属于当前设计行为。

## 现象 2：输出 summary="(summary disabled)" 的原因
### 触发链路
1) application.yml 中 agent.summary.enable=false，StepOutputSummaryBuilder 未启用摘要构建。
2) StepRuntimeService.completeStep 未注入 outputSummary / stepSummary 等字段。
3) AgentRuntime.buildStepOutputSummary 进行归一化时检测到 summary 为空，填充为 "(summary disabled)"。

### 关键代码位置
- 摘要开关配置：src/main/resources/application.yml
  - 节点：agent.summary.enable: false
- 摘要构建器开关：src/main/java/com/example/agent/runtime/StepSummaryProperties.java
  - 字段：enable
- 兜底填充 summary：src/main/java/com/example/agent/runtime/AgentRuntime.java
  - 方法：normalizeStepSummary(...)

### 结论
summary="(summary disabled)" 为摘要开关关闭时的兜底填充值，属于配置与逻辑共同作用的结果。

## 关联观察（可选）
- 由于 LLM 步骤产出的内容在步骤列表中仅保留摘要层，最终 result.finalOutput 在本次请求中也仅包含摘要字段，未包含 answer/highlights/confidence 等最终回答字段。
- 如需对外返回完整 LLM 输出，需要调整步骤输出保存策略或 finalOutput 的生成逻辑。

## 建议（如需改变当前表现）
1) 若希望 final 提示词包含步骤输出：
   - 避免 toolChoice.mode=NONE；或改为可执行工具的模式（如 AUTO/REQUIRED）。
2) 若希望步骤 summary 生成：
   - 将 agent.summary.enable 改为 true。
3) 若希望 finalOutput 返回完整回答内容：
   - 保存 LLM 步骤原始输出，或在 resolveFinalOutputFromSteps 中改用原始输出而非摘要输出。