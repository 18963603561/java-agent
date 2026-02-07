# (summary disabled) 现象分析与方案

生成时间
2026-02-01 13:32

## 现象定义

"(summary disabled)" 是系统在无法生成摘要或摘要信息为空时的兜底占位文本。
该占位并不等于功能异常，而是提醒上游未提供可用摘要数据。

## 触发位置（代码证据）

规划上下文摘要
- `src/main/java/com/example/agent/planning/PlannerService.java`
  - `buildContextSummary`：当摘要为空时写入 "(summary disabled)"

多智能体约束摘要
- `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java`
  - 约束摘要为空时写入 "(summary disabled)"

步骤摘要兜底
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - `normalizeStepSummary`：summary 为空时写入 "(summary disabled)"

反思摘要兜底
- `src/main/java/com/example/agent/reflection/ReflectionService.java`
  - `outputSummary` 与 `outputDigest` 均为空时写入 "(summary disabled)"

ReAct 循环输出摘要兜底
- `src/main/java/com/example/agent/runtime/ReactLoopService.java`
  - `outputSummary`/`stepSummary`/`outputDigest` 均为空时写入 "(summary disabled)"

最终输出摘要兜底
- `src/main/java/com/example/agent/runtime/FinalOutputService.java`
  - `stepSummary`/`outputSummary`/`outputDigest` 均为空时写入 "(summary disabled)"

## 触发场景归纳

场景一：摘要生成未启用或未生效
- 摘要生成开关由 `agent.summary.enable` 控制，默认值为 false。
- 若运行环境未加载包含该配置的 profile（例如仅加载 `application-docker.yml`），摘要构建器不会生成摘要。
- 直接表现为步骤输出缺少 `outputSummary`/`stepSummary`/`outputDigest`。

场景二：步骤失败路径未生成摘要
- `StepRuntimeService.failStep` 不会构建摘要。
- 失败步骤 output 为空或缺少摘要字段时，后续统一兜底为 "(summary disabled)"。

场景三：输出结构未携带摘要字段
- 自定义步骤或特殊执行路径未通过 `StepOutputSummaryBuilder` 生成摘要。
- `AgentRuntime` 仍会要求 `stepSummary.summary`，缺失则兜底为 "(summary disabled)"。

场景四：规划/约束上下文为空
- 规划阶段 `buildContextSummary` 未获得 snapshotId、tokenBudget、memoryItems、tools、evidenceCount。
- 多智能体约束为空时直接标记 "(summary disabled)"。

## 关键流程（摘要生成链路）

流程一：步骤完成路径
1. `StepRuntimeService.completeStep` 在 `agent.summary.enable=true` 时调用 `StepOutputSummaryBuilder`。
2. 生成并写入 `outputSummary`/`stepSummary`/`outputDigest` 到 step output。
3. `AgentRuntime.buildStepOutputSummary` 复制摘要字段并调用 `normalizeStepSummary`。
4. `FinalOutputService`/`ReactLoopService` 优先读取 `stepSummary.summary`，其次 `outputSummary`，再次 `outputDigest`。
5. 若全部缺失，则输出 "(summary disabled)"。

流程二：步骤失败路径
1. `StepRuntimeService.failStep` 不生成摘要。
2. 下游读取不到 `stepSummary`/`outputSummary`/`outputDigest`。
3. 触发多处兜底 "(summary disabled)"。

流程三：规划上下文
1. `PlannerService.buildContextSummary` 从 context 提取信息。
2. 提取结果为空时写入 "(summary disabled)"。
3. 该摘要进入 `PLAN_CONTEXT_JSON`，影响规划提示词。

## 当出现 "(summary disabled)" 且无法获取原始值的解决方案

方案一（优先）：保证摘要构建链路完整
- 确认运行 profile 中包含 `agent.summary.enable=true`。
- 检查是否存在覆盖配置导致 `agent.summary.enable=false`。
- 确保所有步骤通过 `StepRuntimeService.completeStep` 完成并生成摘要。

方案二：为失败路径补充摘要与错误输出
- 在失败路径生成最小摘要与 digest：
  - 在 `failStep` 或异常处理处补充 `outputSummary`/`outputDigest`。
- 让 `FinalOutputService` 能从错误摘要中获得可读信息。

方案三：保留原始输出以便回溯
- 使用 `GET /api/v1/timeline/steps` 获取 `StepRecord.output` 原始数据。
- 对外输出需要原始值时，提供调试开关返回 `output` 或 `stepOutputs`。

方案四：约束与规划摘要补齐
- 在构建 context 时写入 snapshotId、tokenBudget、tools、evidenceCount 等字段。
- 若多智能体场景需要摘要，确保约束与工具清单不为空。

## 建议落地步骤

1. 配置确认
   - 统一各环境的 `agent.summary.enable=true`，避免 profile 缺省导致关闭。

2. 失败路径补摘要
   - 为 `failStep` 增加摘要与 digest，避免兜底占位。

3. 输出可回溯
   - 在需要诊断的接口或日志中保留 `output` 原始值引用或摘要片段。

4. 验证用例
   - 成功步骤：摘要不应为 "(summary disabled)"。
   - 失败步骤：摘要可读且包含错误原因。
   - 规划上下文为空：应能识别并提示缺失信息，而非长期占位。