# `reflection` 包设计合理性与坏味道评审

评审时间：2026-02-10 10:14

## 1. 评审范围与方法

### 1.1 评审范围

- 评审目录：`src/main/java/com/example/agent/reflection`
- 评审对象：
  - 根包编排与契约对象
  - `model`、`parser`、`prompt`、`strategy` 子包
  - 与 `runtime`、`llm` 相关的边界交互

### 1.2 评审维度

- 包分层与职责边界
- 可扩展性与复用性
- 契约稳定性与错误处理一致性
- 可测试性与可观测性
- 代码坏味道与长期维护风险

---

## 2. 总体等级结论

### 2.1 包设计合理性等级

- **等级：A-（84/100）**
- 结论：整体设计已经具备清晰分层（编排、策略、解析、提示词、上下文模型），主链路可读性与可测试性较好，能够支撑当前阶段迭代。

### 2.2 代码坏味道等级

- **等级：B（中等）**
- 结论：存在若干“可运行但会放大后续改造成本”的结构性坏味道，主要集中在解析契约一致性、策略扩展方式、启发式策略输入模型与对象可变性上。

### 2.3 维护风险等级

- **中等偏低**
- 若继续扩展多策略、多模型供应商和更严格契约治理，当前坏味道会逐步变成维护负担，建议在后续 1~2 个迭代窗口内消化高优先级问题。

---

## 3. 设计亮点（正向结论）

### 3.1 编排层与策略层分离清晰

- `ReflectionService` 负责流程编排与回退语义，策略执行由 `ReflectionStrategy` 接口承载，符合“主流程稳定、策略可替换”的设计方向。
- 证据：`src/main/java/com/example/agent/reflection/ReflectionService.java:18`
- 证据：`src/main/java/com/example/agent/reflection/strategy/ReflectionStrategy.java:21`

### 3.2 强类型上下文替代动态 `Map`

- 通过 `ReflectionContextMapper -> ReflectionContext` 将运行时摘要映射为领域对象，降低上层直接访问动态键值的风险。
- 证据：`src/main/java/com/example/agent/reflection/model/ReflectionContextMapper.java:17`
- 证据：`src/main/java/com/example/agent/reflection/model/ReflectionContext.java:9`

### 3.3 提示词模板化与启动期配置校验

- 模板通过 `ReflectionPromptProvider` 按版本加载，并配合 `ReflectionPropertiesValidator` 做严格模式校验，避免运行时才暴露模板缺失问题。
- 证据：`src/main/java/com/example/agent/reflection/prompt/ReflectionPromptProvider.java:22`
- 证据：`src/main/java/com/example/agent/reflection/ReflectionPropertiesValidator.java:13`

### 3.4 失败原因码语义收敛

- 引入 `ReflectionFailureReason` 和 `ReflectionDecisionStatus`，回退路径具备可追踪语义，较原先“异常字符串驱动分支”明显改善。
- 证据：`src/main/java/com/example/agent/reflection/ReflectionFailureReason.java:9`
- 证据：`src/main/java/com/example/agent/reflection/ReflectionDecisionStatus.java:9`

### 3.5 测试覆盖方向正确

- 已有包卫生、配置校验、解析契约、原因码映射、上下文映射等测试，能够支撑包级重构回归。
- 证据：`src/test/java/com/example/agent/reflection/ReflectionPackageHygieneTest.java:23`
- 证据：`src/test/java/com/example/agent/reflection/parser/ReflectionResponseParserTest.java:31`

---

## 4. 坏味道清单与等级

### 4.1 `RFL-SMELL-01` 解析器返回语义混用（异常 + `null`）

- 等级：**中高（B+）**
- 现象：`parse` 既可能抛异常（JSON 非法），也可能返回 `null`（字段缺失/越界），调用方需要双通道处理，复杂度上升。
- 证据：`src/main/java/com/example/agent/reflection/parser/ReflectionResponseParser.java:53`
- 证据：`src/main/java/com/example/agent/reflection/parser/ReflectionResponseParser.java:60`
- 影响：失败语义不统一，后续扩展解析规则时容易漏掉分支，增加误判概率。
- 建议：统一为 `ParseOutcome`（`success/failed + reason + payload`），避免 `null` 语义。

### 4.2 `RFL-SMELL-02` 启发式策略依赖 `Map.toString()`

- 等级：**中（B）**
- 现象：长度与失败关键词判定直接基于 `output.toString()`，与序列化顺序、格式化差异耦合。
- 证据：`src/main/java/com/example/agent/reflection/strategy/HeuristicReflectionStrategy.java:90`
- 证据：`src/main/java/com/example/agent/reflection/strategy/HeuristicReflectionStrategy.java:104`
- 影响：评分稳定性不足，同义输出可能产生不同分值；对复杂对象和大对象不友好。
- 建议：改为基于 `ReflectionContext` 中的摘要与指纹字段评分。

### 4.3 `RFL-SMELL-03` 策略选择器对具体实现类硬编码

- 等级：**中（B）**
- 现象：`ReflectionStrategySelector` 直接依赖 `LlmReflectionStrategy`、`HeuristicReflectionStrategy` 具体类。
- 证据：`src/main/java/com/example/agent/reflection/strategy/ReflectionStrategySelector.java:24`
- 证据：`src/main/java/com/example/agent/reflection/strategy/ReflectionStrategySelector.java:29`
- 影响：新增策略需修改选择器代码，不利于插件化与横向扩展。
- 建议：改为注入 `List<ReflectionStrategy>` + 显式优先级元数据。

### 4.4 `RFL-SMELL-04` 领域结果对象可变性与其它模型风格不一致

- 等级：**中低（B-）**
- 现象：`ReflectionResult`、`ReflectionReport` 是可变对象（含 setter），与大量不可变模型并存。
- 证据：`src/main/java/com/example/agent/reflection/ReflectionResult.java:11`
- 证据：`src/main/java/com/example/agent/reflection/ReflectionReport.java:11`
- 影响：多处传递后被二次修改时，容易引入隐式状态污染。
- 建议：优先收敛为不可变对象或仅在边界层保留可变 DTO。

### 4.5 `RFL-SMELL-05` `LlmReflectionStrategy` 责任偏多

- 等级：**中（B）**
- 现象：同一类同时处理提示词、调用、解析/修复、失败码映射、追踪上报，类体积较大。
- 证据：`src/main/java/com/example/agent/reflection/strategy/LlmReflectionStrategy.java:34`
- 影响：后续改动容易产生连锁回归，单元测试隔离成本增加。
- 建议：拆分为“调用执行器 + 结果判定器 + 追踪记录器”。

### 4.6 `RFL-SMELL-06` 内置兜底模板可读性细节问题

- 等级：**低（C+）**
- 现象：兜底模板文本与上下文片段直接拼接，缺少显式换行分隔。
- 证据：`src/main/java/com/example/agent/reflection/prompt/ReflectionPromptProvider.java:139`
- 影响：对模型可读性和调试可观测性不友好（非功能错误）。
- 建议：加换行和结构化分隔标记。

---

## 5. 分维度评分明细

| 维度 | 分数 | 等级 | 说明 |
|---|---:|---|---|
| 分层与职责边界 | 88 | A- | 服务编排、策略、解析、提示词基本分离 |
| 契约清晰度 | 80 | B+ | 失败码已收敛，但解析返回语义尚未完全统一 |
| 扩展性 | 78 | B+ | 策略扩展仍依赖选择器改码 |
| 可测试性 | 86 | A- | 测试覆盖面较全，且有契约类测试 |
| 可观测性 | 85 | A- | 关键链路日志齐全，失败上下文较完整 |
| 代码一致性 | 79 | B+ | 不可变模型与可变模型并存，风格略分裂 |

**综合：84 / 100（A-）**

---

## 6. 建议改造优先级（仅建议，不含本次实施）

### 第一优先级（建议尽快）

- 统一解析返回契约，消除 `null + 异常` 双通道语义。
- 启发式评分从 `Map.toString` 迁移到 `ReflectionContext` 结构化字段。

### 第二优先级（建议下一迭代）

- 策略选择器改为“策略列表 + 顺序元数据”的可扩展模式。
- 统一 `ReflectionResult/ReflectionReport` 的不可变建模风格。

### 第三优先级（可并行优化）

- 拆分 `LlmReflectionStrategy` 过多职责。
- 优化兜底模板的结构化可读性与调试信息。

---

## 7. 最终结论

- 该包已具备较好的工程化基础，属于“**可以稳定演进**”的状态。
- 当前坏味道并非阻塞上线问题，但会在“策略数量增加、契约更严格、多人并行开发”时迅速放大维护成本。
- 若目标是提升长期复用性与设计稳定性，建议先处理第一优先级两项，再推进扩展性和一致性治理。

