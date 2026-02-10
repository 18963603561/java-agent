# `reflection` 包第三优先级改造任务清单

## 1. 目标

- 目标一：完成遗留模型清理闭环，确保反思域对象“有定义必有使用”。
- 目标二：补齐反思契约失败分支测试，提升边界输入下的行为可预测性。

## 2. 对应评审条目

- 来源文档：`doc/reflection-package-review-202602100834.md`
- 对应章节：`6.3 第三优先级（可并行）`
- 原始要求：
  - 清理未使用的 `ReflectionRequest`，或让其成为统一输入模型。
  - 增加契约测试，覆盖“缺字段、越界分数、非法类型、空上下文”等失败分支。

## 3. 现状说明

- `ReflectionRequest` 已在第一阶段重构中删除（当前状态是“清理路线”而非“复用路线”）。
- 当前测试覆盖了主流程、修复流程与回退流程，但契约异常输入覆盖仍可继续增强。

## 4. 任务清单（第三优先级）

### A 组：遗留对象治理（轻量）

#### 任务 A1：反思域对象“可达性”巡检

- 任务编号：`REFL-P2-A1`
- 主要产出：梳理 `reflection` 包全部类的“是否被主链路消费”清单。
- 建议动作：
  - 对 `reflection/model`、`reflection/prompt`、`reflection/parser`、`reflection/strategy` 做引用扫描。
  - 标识“仅测试使用”“运行时使用”“未使用”三类状态。
- 验收标准：不再出现孤立、无消费价值的运行时代码对象。

#### 任务 A2：删除新增遗留代码的防回归守门

- 任务编号：`REFL-P2-A2`
- 主要产出：新增轻量守门测试或静态扫描规则，防止无引用对象再次进入主包。
- 建议文件：`src/test/java/com/example/agent/reflection/ReflectionPackageHygieneTest.java`
- 验收标准：CI 可在新增“未消费运行时对象”时直接失败。

### B 组：契约失败分支测试补齐（核心）

#### 任务 B1：解析契约异常输入测试

- 任务编号：`REFL-P2-B1`
- 主要产出：面向 `ReflectionResponseParser` 新增失败分支测试。
- 建议文件：`src/test/java/com/example/agent/reflection/parser/ReflectionResponseParserTest.java`
- 必测场景：
  - 缺字段：缺少 `score`。
  - 非法类型：`score` 为字符串、`retry` 为字符串、`notes` 为对象。
  - 非 JSON 内容：纯文本、带前后缀污染文本。
  - 空输出：空串、空白串。
- 验收标准：每类输入都有明确断言，且失败语义稳定（返回空或进入修复分支）。

#### 任务 B2：越界分数策略测试

- 任务编号：`REFL-P2-B2`
- 主要产出：补齐 `score<0`、`score>1` 的行为断言。
- 建议方案（二选一并固定）：
  - 方案 1：解析阶段直接判为不合法并失败。
  - 方案 2：解析阶段归一化到 `[0,1]` 并记录告警。
- 验收标准：越界分数行为在代码和测试中保持一致，禁止“隐式漂移”。

#### 任务 B3：空上下文与最小上下文测试

- 任务编号：`REFL-P2-B3`
- 主要产出：对 `ReflectionContextMapper` 与 `LlmReflectionStrategy` 增加空上下文覆盖。
- 建议文件：
  - `src/test/java/com/example/agent/reflection/model/ReflectionContextMapperTest.java`
  - `src/test/java/com/example/agent/reflection/strategy/LlmReflectionStrategyContractTest.java`
- 必测场景：
  - `step=null`、`output=null`、`attempt=0`。
  - 仅有 `outputDigest`、无 `outputSummary`。
  - `outputSummary.summary` 为空，触发兜底摘要文本。
- 验收标准：空上下文不抛空指针，行为可预期且可断言。

#### 任务 B4：失败原因码映射一致性测试

- 任务编号：`REFL-P2-B4`
- 主要产出：验证 `ReflectionFailureReason` 与异常场景的一一映射。
- 建议文件：`src/test/java/com/example/agent/reflection/strategy/LlmReflectionStrategyReasonCodeTest.java`
- 必测场景：
  - 模型返回空 -> `LLM_EMPTY_RESPONSE`
  - 解析异常 -> `LLM_PARSE_ERROR`
  - 修复失败 -> `LLM_REPAIR_FAILED`
  - 调用异常 -> `LLM_INVOCATION_ERROR`
- 验收标准：原因码覆盖完整，且与日志字段一致。

#### 任务 B5：端到端失败分支回归测试

- 任务编号：`REFL-P2-B5`
- 主要产出：在协调器层验证失败分支是否正确驱动重试/失败。
- 建议文件：`src/test/java/com/example/agent/runtime/StepExecutionCoordinatorTest.java`
- 必测场景：
  - 反思建议重试时步骤标记 `REFLECTION_RETRY`。
  - 反思不可用且回退关闭时流程失败并保留错误码。
- 验收标准：运行态行为与反思决策语义一致。

## 5. 推荐实施顺序

- 第一阶段（治理）：`A1 -> A2`
- 第二阶段（解析契约）：`B1 -> B2`
- 第三阶段（上下文与原因码）：`B3 -> B4`
- 第四阶段（端到端回归）：`B5`

## 6. 完成定义

- 无新增未消费的反思运行时对象。
- 契约异常输入测试覆盖“缺字段、越界分数、非法类型、空上下文”四大类。
- 失败原因码、日志字段、策略行为保持一致。
- 全量测试通过：`mvn test -DskipTests=false`。
