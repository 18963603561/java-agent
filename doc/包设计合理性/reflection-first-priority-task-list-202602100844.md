# `reflection` 包第一优先级改造任务清单

## 1. 目标

- 目标一：用显式结果对象替代 `null` 控制流，消除隐式语义。
- 目标二：统一异常日志规范，提升故障定位速度。
- 目标三：拆分 `ReflectionService`，收敛职责边界，降低回归风险。

## 2. 范围

- 核心范围：`src/main/java/com/example/agent/reflection`
- 联动范围：`src/main/java/com/example/agent/runtime/engine/StepExecutionCoordinator.java`
- 测试范围：`src/test/java/com/example/agent/reflection` 与 `src/test/java/com/example/agent/runtime`

## 3. 任务清单（第一优先级）

### A 组：显式结果对象替代 `null` 控制流

#### 任务 A1：定义统一决策模型

- 任务编号：`REFL-P0-A1`
- 主要产出：新增 `ReflectionDecision` 与状态枚举、原因码枚举。
- 建议文件：
  - `src/main/java/com/example/agent/reflection/ReflectionDecision.java`
  - `src/main/java/com/example/agent/reflection/ReflectionDecisionStatus.java`
  - `src/main/java/com/example/agent/reflection/ReflectionFailureReason.java`
- 字段建议：`status`、`reason`、`retryRequested`、`report`、`fromLlm`、`repairAttempted`、`repairSuccess`。
- 验收标准：可以覆盖当前 `tryLlmReflection` 的全部返回语义，不再依赖 `null` 表达失败。

#### 任务 A2：改造 `tryLlmReflection` 返回契约

- 任务编号：`REFL-P0-A2`
- 主要产出：`tryLlmReflection` 由返回 `ReflectionResult` 改为返回 `ReflectionDecision`。
- 改造重点：
  - 将“模型返回空、解析失败、修复失败、调用异常”映射为不同 `status/reason`。
  - 保留原有 `report` 与 `retry` 计算能力。
- 建议文件：`src/main/java/com/example/agent/reflection/ReflectionService.java`
- 验收标准：该方法内部不再出现 `return null`。

#### 任务 A3：改造 `reflect` 主流程编排

- 任务编号：`REFL-P0-A3`
- 主要产出：`reflect` 主流程基于 `ReflectionDecision` 显式分支，去掉空值分支。
- 改造重点：
  - LLM 成功时直接落 `ReflectionResult`。
  - LLM 失败且允许回退时进入规则策略。
  - 禁止回退时抛出带原因码的异常。
- 建议文件：`src/main/java/com/example/agent/reflection/ReflectionService.java`
- 验收标准：流程分支可读，状态机清晰，行为与现网逻辑等价。

#### 任务 A4：联动调用方消除空判断歧义

- 任务编号：`REFL-P0-A4`
- 主要产出：`StepExecutionCoordinator` 对反思结果处理由“空值判定”过渡为“显式状态判定”。
- 建议文件：`src/main/java/com/example/agent/runtime/engine/StepExecutionCoordinator.java`
- 验收标准：调用方不再通过 `result != null` 推断语义，仅根据显式字段处理。

#### 任务 A5：补齐契约测试

- 任务编号：`REFL-P0-A5`
- 主要产出：新增单元测试覆盖 `ReflectionDecision` 关键状态。
- 建议场景：
  - 模型输出为空。
  - 解析失败且修复成功。
  - 解析失败且修复失败并触发回退。
  - 禁止回退时抛出预期异常。
- 建议文件：
  - `src/test/java/com/example/agent/reflection/ReflectionServiceTest.java`
  - `src/test/java/com/example/agent/runtime/StepExecutionCoordinatorTest.java`
- 验收标准：新增场景断言“状态 + 原因码 + 是否回退”三元组。

### B 组：统一异常日志规范

#### 任务 B1：建立反思日志上下文字段规范

- 任务编号：`REFL-P0-B1`
- 主要产出：统一日志字段字典。
- 字段建议：`tenantId`、`workflowId`、`stepType`、`attempt`、`scene`、`reasonCode`。
- 建议文件：`src/main/java/com/example/agent/reflection/ReflectionService.java`
- 验收标准：反思异常日志字段顺序与命名统一。

#### 任务 B2：异常捕获处统一输出堆栈

- 任务编号：`REFL-P0-B2`
- 主要产出：将 `log.warn(..., ex.getMessage())` 改为 `log.warn(..., ex)` 模式。
- 建议文件：`src/main/java/com/example/agent/reflection/ReflectionService.java`
- 验收标准：每个 `catch` 块日志均可看到完整堆栈。

#### 任务 B3：补关键路径开始与结束日志

- 任务编号：`REFL-P0-B3`
- 主要产出：补充反思关键路径 `start/end` 与外部调用前后日志。
- 改造重点：
  - 外部调用：模型调用与修复调用。
  - 关键路径：选择策略、落回退、最终重试判定。
- 建议文件：`src/main/java/com/example/agent/reflection/ReflectionService.java`
- 验收标准：从日志可还原一次完整反思生命周期。

### C 组：拆分 `ReflectionService`

#### 任务 C1：定义策略接口与选择器

- 任务编号：`REFL-P0-C1`
- 主要产出：新增策略接口与选择器，主服务仅做编排。
- 建议文件：
  - `src/main/java/com/example/agent/reflection/strategy/ReflectionStrategy.java`
  - `src/main/java/com/example/agent/reflection/strategy/ReflectionStrategySelector.java`
- 验收标准：主流程不再直接写 `if (llmEnabled)` 的策略细节实现。

#### 任务 C2：抽离模型反思执行器

- 任务编号：`REFL-P0-C2`
- 主要产出：将提示词构建、模型调用、解析修复、提示追踪抽离到独立实现。
- 建议文件：`src/main/java/com/example/agent/reflection/strategy/LlmReflectionStrategy.java`
- 验收标准：该类可独立测试，`ReflectionService` 不再直接处理解析修复细节。

#### 任务 C3：抽离规则反思执行器

- 任务编号：`REFL-P0-C3`
- 主要产出：将启发式评分逻辑迁移到规则策略实现。
- 建议文件：`src/main/java/com/example/agent/reflection/strategy/HeuristicReflectionStrategy.java`
- 验收标准：规则评分逻辑从主服务中完全移出。

#### 任务 C4：抽离解析器组件

- 任务编号：`REFL-P0-C4`
- 主要产出：将 `parseReflection` 与修复后再解析逻辑抽为独立组件。
- 建议文件：`src/main/java/com/example/agent/reflection/parser/ReflectionResponseParser.java`
- 验收标准：解析规则具备单测，且策略层仅调用解析组件。

#### 任务 C5：回归与兼容验证

- 任务编号：`REFL-P0-C5`
- 主要产出：执行回归测试，确保重构前后外部行为一致。
- 验收范围：
  - `reflection.retry.count` 指标行为不变。
  - `REFLECTION_STARTED/REFLECTION_COMPLETED` 事件行为不变。
  - `StepExecutionCoordinator` 对重试与完成判定行为不变。
- 建议文件：
  - `src/test/java/com/example/agent/reflection/ReflectionServiceTest.java`
  - `src/test/java/com/example/agent/runtime/StepExecutionCoordinatorTest.java`
- 验收标准：现有测试通过，新增测试通过。

## 4. 推荐实施顺序

- 第一步：`A1 -> A2 -> A3 -> A4 -> A5`
- 第二步：`B1 -> B2 -> B3`
- 第三步：`C1 -> C2 -> C3 -> C4 -> C5`

## 5. 完成定义

- 无 `null` 语义分支承载反思失败状态。
- 所有异常捕获日志包含统一上下文字段与完整堆栈。
- `ReflectionService` 降为编排层，核心策略与解析逻辑已拆分。
- 相关单测通过，关键链路行为与改造前一致。
