# 禁用摘要后使用原始结果的可行性分析

## 结论摘要
- 结论：可以实现，但需要严格控制体量与风险，不能无条件“原样透传”。
- 推荐策略：摘要禁用时，反思与多步骤上下文走“受控原始结果”路径（限长、字段白名单、脱敏），避免内存/成本/泄露风险。

## 背景与现状
1. 当前 `agent.summary.enable=false` 时，`StepOutputSummaryBuilder` 不生成摘要。
2. `StepRuntimeService.completeStep` 不会合并 `outputSummary/toolResultSummary/stepSummary/outputDigest` 到输出。
3. `AgentRuntime.updateRuntimeContext` 只写入 `lastStepSummary`（由 `buildStepOutputSummary` 构造），该方法仅复制摘要字段并兜底 `(summary disabled)`。
4. `ReflectionService.buildOutputSummaryContext` 仅使用 `outputSummary/outputDigest`，摘要缺失时直接使用 `(summary disabled)`。
5. 结论：禁用摘要后，反思与多步骤上下文无法依赖上一任务真实输出内容。

## 需求解读
用户希望在 `agent.summary.enable=false` 时“按照原始结果来”。
含义可能包括：
- 反思阶段能看到原始输出；
- 多步骤上下文可引用上一任务输出；
- 生成最终结果时可使用原始内容。

## 可行性与风险评估
### 可行性
- 逻辑上可实现：在摘要禁用时，将原始输出写入上下文与反思输入。
- 修改点集中：`AgentRuntime`、`ReflectionService`、`FinalOutputService/ReactLoopService` 等。

### 主要风险
1. **上下文膨胀**：原始输出可能很大，导致 prompt 超长、费用激增或超限失败。
2. **敏感信息泄露**：原始输出可能包含隐私或机密数据，反思或下游使用会扩大传播面。
3. **稳定性风险**：大对象序列化、循环引用、不可序列化对象可能导致异常。
4. **一致性问题**：不同阶段对原始输出的取舍不一致，导致行为不可预测。

## 方案评估
### 方案 A（推荐）：禁用摘要时走“受控原始结果”
- 核心思路：不生成摘要，但提供“可控原始输出”的上下文。
- 控制手段：
  1. 全局限长（类似 `maxChars`）；
  2. 字段白名单（仅允许 `answer/result/data/error` 等）；
  3. 脱敏（命中隐私字段自动掩码）；
  4. 仅保留最近步骤的原始输出，避免上下文增长。
- 优点：尽量满足“原始结果可用”，同时可控风险。
- 缺点：需要新增配置与处理逻辑。

### 方案 B（备选）：禁用摘要时仍允许“反思前临时摘要”
- 仅在反思阶段临时生成摘要，不落库。
- 优点：改动小、风险低。
- 缺点：不能满足“完全原始结果”的诉求。

### 方案 C（不推荐）：完全原样透传
- 直接把原始输出塞入上下文与反思。
- 风险大：上下文爆炸、成本不可控、隐私暴露。

## 影响范围与修改点
### 需要修改的文件
1. `src/main/java/com/example/agent/runtime/AgentRuntime.java`
   - `updateRuntimeContext`：当摘要禁用时，允许写入受控原始输出（例如 `lastStepOutput`）。
   - `buildStepOutputSummary`：摘要缺失时可按新策略返回“原始输出摘要/裁剪版”。
2. `src/main/java/com/example/agent/reflection/ReflectionService.java`
   - `buildOutputSummaryContext`：摘要缺失时改为使用“受控原始输出”。
3. `src/main/java/com/example/agent/runtime/FinalOutputService.java`
   - 摘要缺失时从“受控原始输出”提取信息作为摘要或补充提示。
4. `src/main/java/com/example/agent/runtime/ReactLoopService.java`
   - 摘要缺失时优先使用“受控原始输出”。
5. `src/main/java/com/example/agent/runtime/StepSummaryProperties.java` 或新增配置类
   - 新增原始输出控制参数：最大长度、字段白名单、脱敏开关等。
6. `src/main/resources/application.yml`
   - 增加或补充上述配置。

## 建议方案（落地方向）
1. 新增配置：
   - `agent.summary.raw-output.enable`：禁用摘要时是否写入原始输出上下文。
   - `agent.summary.raw-output.max-chars`：原始输出最大长度。
   - `agent.summary.raw-output.allow-keys`：允许字段白名单。
   - `agent.summary.raw-output.mask-keys`：脱敏字段列表。
2. 在 `AgentRuntime.updateRuntimeContext` 写入 `lastStepOutput`：
   - 只写入裁剪后的 JSON 文本或结构化映射，避免大对象/循环引用。
3. `ReflectionService.buildOutputSummaryContext`：
   - 摘要缺失时优先读取 `lastStepOutput` 的裁剪文本作为输入。
4. `FinalOutputService/ReactLoopService`：
   - 摘要为空时尝试从裁剪后的原始输出提取核心信息。

## 是否合适的结论
- **合适但需受控**：完全原样透传不安全，建议采用“受控原始结果”的策略。
- 如果业务场景强依赖原始输出，可在生产环境通过配置逐步放开与监控。

## 验收要点
1. 摘要禁用时，反思与多步骤上下文仍能看到关键输出。
2. 大输出被裁剪，系统仍可稳定运行。
3. 日志与事件流中不出现敏感数据泄露。
4. 配置可开关、可灰度。

