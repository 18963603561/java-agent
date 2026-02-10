# `src/main/java/com/example/agent/reflection` 包设计评审

## 1. 评审范围与方法

- 评审范围：
  - `src/main/java/com/example/agent/reflection/ReflectionService.java`
  - `src/main/java/com/example/agent/reflection/ReflectionProperties.java`
  - `src/main/java/com/example/agent/reflection/ReflectionResult.java`
  - `src/main/java/com/example/agent/reflection/ReflectionReport.java`
  - `src/main/java/com/example/agent/reflection/ReflectionRequest.java`
- 关联调用链：
  - `src/main/java/com/example/agent/runtime/engine/StepExecutionCoordinator.java`
  - `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryView.java`
- 评审维度：职责边界、耦合度、健壮性、可观测性、可维护性、可测试性。

## 2. 总体结论与等级

### 2.1 总等级

- **等级：三级（中）**
- **综合评分：71/100**

### 2.2 结论摘要

- 包本身具备明确业务目标，围绕“反思评估 + 是否重试”组织，主流程可运行。
- 但 `ReflectionService` 承担职责过多，出现明显“服务过胖”倾向，导致边界不清与扩展成本上升。
- 失败路径大量使用 `null` 表达，叠加异常吞没与日志上下文不足，运行时诊断难度偏高。
- 启发式与模型两套策略并存是优点，但策略抽象未形成稳定接口，后续新增策略时改动面会放大。

## 3. 包设计合理性分析

### 3.1 设计合理点

- **目标聚焦**：`reflection` 包聚焦在“质量评估与重试建议”，语义上是一个独立能力域。
- **具备降级路径**：`reflect` 先尝试模型反思，失败后回落规则反思，具备弹性（`ReflectionService.java:113`、`ReflectionService.java:123`）。
- **有安全意识**：反思上下文只喂摘要，不直接注入原始输出，降低提示注入风险（`ReflectionService.java:332`、`StepOutputSummaryView.java:56`）。
- **有基础可观测性**：具备关键节点日志、重试指标、提示词追踪（`ReflectionService.java:194`、`ReflectionService.java:224`、`ReflectionService.java:339`）。
- **存在测试覆盖**：`ReflectionServiceTest` 覆盖了低分重试、修复解析、摘要注入等主路径。

### 3.2 设计不足

- **核心服务职责过载**：`ReflectionService` 同时负责提示词构建、模型调用、解析修复、启发式评分、追踪与指标，职责边界过宽。
- **跨层耦合偏重**：服务直接依赖运行时对象与模型基础设施对象，导致 `reflection` 包更像“流程胶水层”而非稳定领域层。
- **策略抽象缺失**：模型策略与规则策略没有统一抽象，当前通过 `if/else + null` 拼接，扩展新策略会侵入主流程。

## 4. 代码坏味道清单（分级）

## 说明

- 严重级：高 / 中 / 低。
- 影响范围按“正确性、可维护性、排障成本”综合评估。

### 4.1 高级别问题

1) **空值语义承载失败状态，控制流可读性差**（严重级：高）

- 证据：`tryLlmReflection` 多处 `return null` 表达不同失败原因（`ReflectionService.java:152`、`ReflectionService.java:188`、`ReflectionService.java:208`）。
- 证据：上层以 `llmResult != null` 判断是否继续（`ReflectionService.java:114`），调用方又以 `reflection != null && reflection.isRetryRequested()` 判断重试（`StepExecutionCoordinator.java:188`）。
- 风险：失败原因丢失，容易出现“静默降级”，后续排障只能依赖零散日志。

2) **异常日志未输出堆栈，排障信息不足**（严重级：高）

- 证据：多处 `log.warn(..., ex.getMessage())`，未传 `ex`（`ReflectionService.java:163`、`ReflectionService.java:204`、`ReflectionService.java:326`）。
- 风险：线上问题缺乏堆栈，不利于快速定位根因。

3) **`ReflectionService` 过胖，违背单一职责**（严重级：高）

- 证据：同一类长度约 410 行，集成 7 个构造注入依赖与 26 个导入。
- 证据：同类内同时处理“策略决策、提示词模板、调用、解析、修复、追踪、打分”。
- 风险：修改任一子能力都可能影响主流程，回归风险高。

### 4.2 中级别问题

4) **提示词语义与输入上下文存在错位**（严重级：中）

- 证据：提示词要求依据 `steps` 与 `query` 审核（`ReflectionService.java:245`~`ReflectionService.java:272`）。
- 证据：实际上下文仅注入 `stepType`、`attempt`、`outputSummary/outputDigest`（`ReflectionService.java:234`~`ReflectionService.java:237`、`StepOutputSummaryView.java:56`~`StepOutputSummaryView.java:73`）。
- 风险：模型判断依据与指令描述不一致，评分稳定性受影响。

5) **反思结果解析过于宽松**（严重级：中）

- 证据：`parseReflection` 仅要求 `score` 为数字，`retry` 缺失即默认 `false`，`notes` 缺失默认固定文案（`ReflectionService.java:290`~`ReflectionService.java:298`）。
- 风险：模型输出不合约时仍被接受，可能掩盖质量问题。

6) **配置缺少边界校验**（严重级：中）

- 证据：`ReflectionProperties` 未使用校验注解，`maxRetries`、`confidenceThreshold`、`minOutputChars` 无运行期校验（`ReflectionProperties.java:33`、`ReflectionProperties.java:38`、`ReflectionProperties.java:43`）。
- 风险：异常配置值会导致行为偏差甚至不可预期结果。

7) **硬编码长提示词降低可维护性**（严重级：中）

- 证据：提示词模板直接内嵌在业务类（`ReflectionService.java:244`）。
- 风险：版本管理、灰度、对比评估困难；类内噪音高。

### 4.3 低级别问题

8) **疑似遗留未使用类**（严重级：低）

- 证据：`ReflectionRequest` 在主代码中无引用，仅定义未消费。
- 风险：增加认知负担，误导后续开发者理解“反思输入契约”。

9) **启发式评分过于依赖 `Map.toString()`**（严重级：低）

- 证据：通过 `output.toString().length()` 与文本包含判断质量（`ReflectionService.java:390`、`ReflectionService.java:404`）。
- 风险：结构化数据语义丢失，评分容易受序列化形式影响。

## 5. 分维度评分

- 职责边界：2.5/5
- 健壮性：3.0/5
- 可观测性：3.0/5
- 可维护性：2.5/5
- 可测试性：3.5/5
- **综合：2.9/5（三级，中）**

## 6. 优先级改进建议

### 6.1 第一优先级（建议先做）

- 以显式结果对象替代 `null` 控制流：引入 `ReflectionDecision`（包含状态枚举、原因码、报告）。
- 统一异常日志规范：异常捕获处统一输出上下文 + 堆栈。
- 拆分 `ReflectionService`：至少拆成策略选择器、模型策略执行器、规则策略执行器、结果解析器。

### 6.2 第二优先级（中期）

- 将提示词模板外置（模板文件或配置中心），并增加版本号。
- 收敛反思输入契约：由专用 `ReflectionContext` 对象取代散落 `Map<String, Object>`。
- 为 `ReflectionProperties` 增加参数校验与启动期失败机制。

### 6.3 第三优先级（可并行）

- 清理未使用的 `ReflectionRequest`，或让其成为真正的统一输入模型。
- 增加契约测试：覆盖“缺字段、越界分数、非法类型、空上下文”等失败分支。

## 7. 结论

- 该包当前可用，但处于“功能先行、结构滞后”的状态。
- 如果近期计划继续增强反思能力（多策略、多模型、分场景阈值），建议先做一次轻量重构再扩展功能。
- 以当前质量基线评估：**适合小步迭代，不适合继续叠加复杂逻辑而不做结构治理**。
