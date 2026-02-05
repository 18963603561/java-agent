# 运行时步骤模型合并与对象化设计（去历史包袱）

## 1. 目标与范围

本文针对以下类的字段语义、类型边界和转换路径进行梳理，并给出可直接落地的“合并 + 简化”方案：

- `StepRequest`
- `StepResponse`
- `StepQuery`
- `RuntimeResult`
- `StepOutputSummaryBuilder`
- `runtime/io` 包下：`StepError` `StepInput` `StepMeta` `StepOutput` `StepOutputMapper` `StepRaw` `StepSummary` `StepTiming` `StepUpstream`

前提约束：

- 新项目，不保留历史兼容分支。
- 以可复用、可维护、边界清晰为第一目标。
- `Map<String,Object>` 仅允许出现在外部动态边界（模型返回、工具返回、JSON 持久化边界）。

---

## 2. 全量检索结论（src/main/java + src/test/java）

### 2.1 引用覆盖结论

- `StepResponse`：无任何业务引用，仅类自身定义。
- `StepQuery`：无任何业务引用，仅类自身定义。
- `StepInput`、`StepUpstream`：无任何业务引用，仅类自身定义。
- `StepOutputMapper`：仅 `StepRuntimeService` 使用一次（`StepOutput -> Map`）。
- `StepRequest`：被 `PlannerService`、`AgentRuntime`、`ReflectionService`、`MultiAgentCoordinator` 与多处测试广泛使用。
- `RuntimeResult`：被 `AgentRuntime` 生成，被 `TaskOrchestrator`、`TaskControllerTest` 等消费。

### 2.2 当前最突出的混乱点

- 同一语义在对象和 `Map` 中重复承载。
- 同一链路内对象与 `Map` 来回转换。
- 运行态步骤汇总与持久化步骤输出是两套并行模型。
- 摘要模型缺乏强类型契约，下游大量按 key 猜字段。

---

## 3. 现状类职责与问题拆解

### 3.1 计划输入侧

- `StepRequest`
  - 现状：`stepType + input(Map) + requiresApproval + approvalSource`
  - 问题：`input(Map)` 混入工具参数、上下文、审批信息、依赖关系，运行期需要到处做 key 级判断。
- `StepInput`
  - 现状：试图提供强类型输入（`dependsOn/contextRefs/upstream/...`）。
  - 问题：未被主流程使用，形成“名义模型”。

结论：`StepRequest` 与 `StepInput` 语义重叠，应合并为单一强类型步骤规格对象。

### 3.2 步骤输出侧

- `StepOutput`
  - 现状：`meta/rawRef/structured/raw/summary/refs/errors`
  - 问题：字段边界重叠，`refs` 分散。
- `StepRaw`
  - 现状：`rawRef + data + truncated + refs`
  - 问题：与 `StepOutput.rawRef`、`StepOutput.refs` 重复表达。
- `StepSummary`
  - 现状：`text + json(Map) + truncation`
  - 问题：`json` 为全量动态结构，消费方需要猜 key。
- `StepMeta`
  - 现状：`status(String)`、`timings(StepTiming)`
  - 问题：状态丢失枚举约束；时间字段用 `String`，而 `StepRecord` 用 `Instant`。

### 3.3 结果汇总侧

- `RuntimeResult`
  - 现状：`steps` 为 `List<Map<String,Object>>`
  - 问题：与 `StepOutput` 强类型模型割裂，调用链继续传播动态结构。
- `AgentRuntime.recordStepOutput(...)`
  - 现状：手工组装 `entry(Map)`，并复制 `summary/raw/rawRef`。
  - 问题：与 `StepRuntimeService.buildStepOutput(...)` 形成重复实现。

### 3.4 映射器与构建器侧

- `StepOutputMapper`
  - 现状：只有 `toMap`，无反向解码。
  - 问题：对象模型没有成为主干，只是落库前临时包装。
- `StepOutputSummaryBuilder` 与 `RawOutputEnvelopeBuilder`
  - 现状：都在做递归清洗、截断、循环引用保护。
  - 问题：逻辑相似度高，配置和规则分散。

---

## 4. 是否可以合并：结论

可以，而且建议一次性合并。  
在新项目前提下，建议直接收敛为“单一主模型 + 明确边界转换”，删除未使用类与重复层。

---

## 5. 目标设计（建议方案）

### 5.1 设计原则

- 一个语义只保留一个字段归属。
- 领域层优先强类型；`Map` 只留在外部动态边界。
- 运行态与存储态共享同一 `StepResult` 模型，避免双轨。
- 摘要层、原始层、结构化层分别有清晰对象，不互相重复字段。

### 5.2 包结构建议

```text
com.example.agent.runtime.model.plan
  StepSpec
  StepKind
  StepPolicy
  StepDependency

com.example.agent.runtime.model.result
  StepResult
  StepResultMeta
  StepResultRaw
  StepResultSummary
  StepResultDigest
  StepResultStructured
  StepResultError
  StepResultRefSet

com.example.agent.runtime.codec
  StepResultJsonCodec
  StepSpecJsonCodec

com.example.agent.runtime.summary
  StepSummaryBuilder
  RawEnvelopeBuilder
  OutputSanitizer
```

说明：

- 不建议建 `util` 大杂烩包。
- 通用能力放在 `summary/codec` 等按能力分层的包。

### 5.3 合并后的核心 DTO 草案

```java
public class StepSpec {
    private String stepId;
    private String stepType;
    private StepPolicy policy;
    private Map<String, Object> arguments;
    private Map<String, Object> context;
    private List<String> dependsOn;
}

public class StepResult {
    private StepResultMeta meta;
    private RawRef rawRef;
    private StepResultRaw raw;
    private StructuredResult structured;
    private StepResultSummary summary;
    private StepResultRefSet refs;
    private List<StepResultError> errors;
}
```

关键约束：

- `rawRef` 只在 `StepResult.rawRef` 保留一次，`raw` 内不再重复 `rawRef`。
- `refs` 只在 `StepResultRefSet` 集中维护，`raw.refs` 删除。
- `summary` 由对象字段表达固定结构，避免 `summary.json(Map)`。

---

## 6. 现有类处理建议（删除/合并/保留）

- `StepResponse`：删除（无引用）。
- `StepQuery`：删除（无引用）。
- `StepInput`：删除并并入 `StepSpec`（未使用且语义重叠）。
- `StepUpstream`：删除或并入 `StepDependency`。
- `StepRequest`：重构为 `StepSpec`，拆分 `input` 为 `arguments/context/policy/dependsOn`。
- `RuntimeResult`：重构，`steps` 改为 `List<StepResult>`。
- `StepOutput`：重命名为 `StepResult`，作为运行态与存储态统一模型。
- `StepRaw`：保留但瘦身，删除重复 `rawRef/refs`。
- `StepSummary`：重构，删除 `json(Map)`，改强类型字段。
- `StepMeta`：保留并增强，`status` 改 `StepState`，时间改为强类型。
- `StepTiming`：合并到 `StepMeta` 或保留强类型时间对象，禁止字符串时间。
- `StepError`：保留。
- `StepOutputMapper`：删除，替换为 `StepResultJsonCodec` 双向编解码。
- `StepOutputSummaryBuilder`：重构为返回强类型摘要对象。

---

## 7. 涉及改动文件清单（按优先级）

### P0（必须先做，清主干）

- `src/main/java/com/example/agent/runtime/StepRequest.java`
- `src/main/java/com/example/agent/runtime/RuntimeResult.java`
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
- `src/main/java/com/example/agent/runtime/StepRuntimeService.java`
- `src/main/java/com/example/agent/runtime/StepRecord.java`
- `src/main/java/com/example/agent/runtime/io/StepOutput.java`
- `src/main/java/com/example/agent/runtime/io/StepRaw.java`
- `src/main/java/com/example/agent/runtime/io/StepSummary.java`
- `src/main/java/com/example/agent/runtime/io/StepMeta.java`
- `src/main/java/com/example/agent/runtime/io/StepOutputMapper.java`

### P1（存储与消费链路）

- `src/main/java/com/example/agent/runtime/JdbcStepRecordRepository.java`
- `src/main/java/com/example/agent/runtime/InMemoryStepRecordRepository.java`
- `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
- `src/main/java/com/example/agent/runtime/FinalOutputService.java`
- `src/main/java/com/example/agent/gateway/controller/TimelineController.java`
- `src/main/java/com/example/agent/governance/ReplayService.java`

### P2（步骤输入与上下游收口）

- `src/main/java/com/example/agent/planning/PlannerService.java`
- `src/main/java/com/example/agent/planning/Plan.java`
- `src/main/java/com/example/agent/planning/PlanResult.java`
- `src/main/java/com/example/agent/reflection/ReflectionService.java`
- `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java`
- `src/main/java/com/example/agent/runtime/StepOutputSummaryBuilder.java`
- `src/main/java/com/example/agent/runtime/RawOutputEnvelopeBuilder.java`

### P3（删除无效类与清理）

- `src/main/java/com/example/agent/runtime/StepResponse.java`
- `src/main/java/com/example/agent/runtime/StepQuery.java`
- `src/main/java/com/example/agent/runtime/io/StepInput.java`
- `src/main/java/com/example/agent/runtime/io/StepUpstream.java`

### P4（测试重构）

- `src/test/java/com/example/agent/runtime/StepRuntimeServiceTest.java`
- `src/test/java/com/example/agent/runtime/AgentRuntimeApprovalIntegrationTest.java`
- `src/test/java/com/example/agent/runtime/FinalOutputServiceTest.java`
- `src/test/java/com/example/agent/planning/PlannerServiceTest.java`
- `src/test/java/com/example/agent/reflection/ReflectionServiceTest.java`
- `src/test/java/com/example/agent/multiagent/MultiAgentCoordinatorTest.java`
- `src/test/java/com/example/agent/gateway/controller/TaskControllerTest.java`

---

## 8. 建议落地顺序

1. 先定统一 DTO（`StepSpec`、`StepResult`）和字段归属。
2. 再改执行主干（`AgentRuntime` + `StepRuntimeService`），消灭手工 `Map` 汇总。
3. 然后改存储编解码与对外返回。
4. 最后删无用类并统一测试断言。

---

## 9. 预期收益

- 代码路径减少：不再“对象 -> Map -> 对象语义读取”。
- 字段语义稳定：`rawRef/refs/summary` 归属唯一。
- 可复用性提升：摘要与原始裁剪逻辑可共享 `OutputSanitizer`。
- 测试成本下降：断言从 key 字符串转为类型字段。

---

## 10. 风险点与规避

- 风险：一次性改造面较大。  
  规避：按 P0/P1/P2 分层提交，先打通主链路再清理边缘。

- 风险：历史测试大量依赖 `Map` key。  
  规避：提供临时 JSON 视图适配器，再逐步替换测试断言。

- 风险：摘要字段从动态变强类型后，少数字段丢失。  
  规避：在 `StepResultSummary` 预留 `ext(Map)` 扩展位，仅用于低频扩展。
