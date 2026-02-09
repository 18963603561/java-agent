# context 包设计合理性与坏味道评审

## 1. 评审范围
- 目标目录：`src/main/java/com/example/agent/capabilities/context`
- 子目录范围：`src/main/java/com/example/agent/capabilities/context/research`
- 评审对象：接口、实现类、领域模型、研究流水线、证据包服务
- 文件总数：`32` 个 Java 文件

## 2. 总体结论与等级
- 包设计合理性等级：**B-（6.6/10）**
- 代码坏味道等级：**C+（中高风险）**
- 结论摘要：当前设计具备可运行的分层骨架与较好的可观测性基础，但存在核心类过载、上下文类型不安全、错误处理语义不一致、部分接口行为不完整等结构性问题，建议在 `1~2` 个迭代完成治理。

## 3. 主要优点
- 有清晰的主流程入口与扩展点：`ContextBuilder`、`ContextAssembler` 两个抽象将“构建快照”和“装配提示词”做了概念分离。
- 上下文领域对象较完整：`ContextSnapshot` 汇聚运行元信息、任务意图、记忆、工具、预算、审计等核心语义，模型覆盖面完整。
- 可观测性基础较好：关键路径存在开始/结束/失败日志，如 `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:148`、`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:232`、`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:239`。
- 研究与证据链路具备可追踪意图：`ResearchPipeline` 具备 `PromptTrace` 记录，`EvidencePackService` 具备追加与汇总统计入口。
- 存在配套测试基础：`DefaultContextBuilderTest`、`EvidencePackTest`、`ResearchPipelineTest` 等测试用例为演进提供保护网。

## 4. 关键坏味道清单（按严重度）

### 4.1 高等级（建议优先处理）

#### S1-01 `DefaultContextBuilder` 单类职责过载（典型“上帝类”）
- 证据
  - 文件规模：`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`（`1008` 行）
  - 依赖过多：同文件引入 `39` 个 import
  - 同时承担：策略解析、快照构建、预算补齐、剪枝/裁剪/压缩编排、事件发布、指标计算、字符串与类型转换工具
- 影响
  - 变更耦合高，任一子能力变更都可能影响该类
  - 单元测试隔离成本高，回归风险增大
- 建议
  - 拆分为最少 `4` 个协作组件：`ContextSnapshotFactory`、`ContextPolicyResolver`、`ContextBudgetOrchestrator`、`ContextMetricsBuilder`
  - `DefaultContextBuilder` 仅保留流程编排职责

#### S1-02 异常语义不一致，存在“吞异常”风险
- 证据
  - `DefaultContextBuilder` 在构建异常时直接返回默认结果对象，不抛出异常：`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:237`
  - 调用方 `RuntimePreparationService` 对运行时异常是按“记录并抛出”处理：`src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:246`、`src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:252`
- 影响
  - 调用方难以区分“正常空结果”与“构建失败”
  - 失败可能以静默方式传播到后续链路
- 建议
  - 两种方式二选一：
    - 统一抛出业务异常；
    - 或在 `ContextBuildResult` 增加显式错误字段（如 `success/errorCode/errorMessage`），并由调用方强校验

#### S1-03 `ResearchPipeline` 对外方法行为不完整
- 证据
  - `run(String query)` 仅日志后直接返回空列表：`src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:65`、`src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:67`
- 影响
  - API 语义与使用者直觉不一致，误用时结果静默失败
- 建议
  - 使 `run(String query)` 委托到完整流程（可传 `null` 的上下文参数）
  - 或显式标记为已弃用并限制外部调用

#### S1-04 `EvidencePackService` 进程内存储无回收策略
- 证据
  - 进程级缓存：`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:30`
  - 仅有 `get/compute`，无移除/过期：`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:89`、`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:112`
  - 缺失租户或工作流时统一降级为 `unknown` 键：`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java:313`
- 影响
  - 长生命周期服务存在内存增长风险
  - `unknown:unknown` 场景下可能产生不期望的数据聚合
- 建议
  - 增加基于时间或容量的回收策略
  - 增加显式 `remove/evict` 接口
  - `tenantId/workflowId` 为空时改为拒绝写入或创建一次性临时包

### 4.2 中等级（建议近期处理）

#### S2-01 运行时上下文依赖 `Map<String, Object>` 与大量字符串键
- 证据
  - `ContextBuildRequest` 持有原始 `runtimeContext`：`src/main/java/com/example/agent/capabilities/context/ContextBuildRequest.java:50`
  - `DefaultContextBuilder` 内部存在大量硬编码键读取：`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:329`、`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:350`、`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:381`、`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:884`
- 影响
  - 类型安全弱，运行时错误概率高
  - 键名分散，重构成本高
- 建议
  - 引入上下文键常量或类型化访问器
  - 逐步过渡到专用上下文对象，减少裸 `Map` 直接读取

#### S2-02 参数透传未落地，接口表达与实现不一致
- 证据
  - `DefaultContextAssembler.assemble(...)` 接收 `trimReport/pruneResult/compressionResult`，但当前仅标注“透传”，未形成实质装配规则：`src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java:62`、`src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java:87`
- 影响
  - 接口语义提前膨胀，真实行为不清晰
- 建议
  - 若暂不使用，先收敛签名
  - 若需要使用，补齐显式映射规则与测试

#### S2-03 装配逻辑重复，存在双实现漂移风险
- 证据
  - `DefaultContextAssembler` 与 `PromptAssemblyContextResolver` 均实现 `system/developer` 填充逻辑：`src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java:93`、`src/main/java/com/example/agent/capabilities/llm/prompt/PromptAssemblyContextResolver.java:98`
- 影响
  - 规则更新时容易一处修改、一处遗漏
- 建议
  - 抽取共享策略组件，或明确单一入口

#### S2-04 局部异常处理缺少错误日志
- 证据
  - `ResearchPipeline` 多处 `catch (Exception)` 后直接返回空结果：`src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:208`、`src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:222`
- 影响
  - 故障定位困难，问题表现为“结果为空”而非可诊断错误
- 建议
  - 至少补充 `debug/warn` 级别日志（包含 `queryLength/workflowId` 等上下文）

### 4.3 低等级（可在重构中顺带处理）

#### S3-01 输入对象被方法内部修改，增加隐式副作用
- 证据
  - `resolvePolicy(...)` 内部将解析结果回写请求对象：`src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:740`
- 影响
  - 调用方若复用请求对象，可能出现跨阶段状态污染
- 建议
  - 使用局部变量承载解析结果，避免修改入参

#### S3-02 存在未接入主流程的配置类
- 证据
  - `DeepResearchWorkflowProperties` 仅在自身定义中出现：`src/main/java/com/example/agent/capabilities/context/research/DeepResearchWorkflowProperties.java:13`
- 影响
  - 增加认知负担，易形成“看起来可配置，实际未生效”
- 建议
  - 尽快接入使用链路或清理

## 5. 包设计合理性详评

### 5.1 分层与边界
- 优点：`builder` 与 `assembler` 的概念边界存在；`research` 子包对研究逻辑做了初步隔离。
- 问题：证据链路（`EvidencePack*`）与核心上下文对象仍混在同一层级包中，演进方向上建议进一步拆分子包（如 `model`、`builder`、`evidence`、`research`）。

### 5.2 内聚与耦合
- 优点：大部分模型类职责单一，便于序列化和跨模块传递。
- 问题：`DefaultContextBuilder` 耦合预算、记忆、工具、流式事件、研究对象，导致类内聚性下降。

### 5.3 可测试性
- 优点：已有较多针对上下文链路的测试样例，说明工程具备回归保护意识。
- 问题：由于核心类职责过重，测试多依赖模拟对象，行为组合验证成本偏高。

### 5.4 可观测性
- 优点：构建主链路日志较完整；证据包有指标上报。
- 问题：研究解析失败路径日志不足，导致问题排查粒度不一致。

## 6. 治理建议与迭代顺序

### 第一阶段（本周可落地）
- 修复 `ResearchPipeline.run(String query)` 空实现问题。
- 给 `ResearchPipeline` 的解析异常补充日志。
- 给 `EvidencePackService` 增加最小可用回收接口（按 `tenantId/workflowId` 清理）。

### 第二阶段（1 个迭代）
- 拆分 `DefaultContextBuilder`：先抽 `ContextPolicyResolver` 与 `ContextBudgetRequestFactory`。
- 调整 `ContextBuildResult` 的错误表达，消除“吞异常”语义歧义。

### 第三阶段（2 个迭代）
- 引入类型化上下文访问层，替换 `DefaultContextBuilder` 中字符串键直读。
- 统一 `DefaultContextAssembler` 与 `PromptAssemblyContextResolver` 的填充策略。
- 评估并处理 `DeepResearchWorkflowProperties` 的接入或下线。

## 7. 最终建议
- 当前包设计**可用但债务明显**，建议按“先修语义风险，再做结构拆分”的顺序推进。
- 若近期要引入新能力（如更多裁剪策略、研究模式扩展），应优先完成 `DefaultContextBuilder` 拆分，否则后续迭代边际成本会快速上升。

## 8. 阶段进展更新（2026-02-09）

### 8.1 第一阶段状态
- 已完成。
- 关键产物：
  - `ResearchPipeline` 统一解析/修复/兜底语义并补齐日志。
  - `EvidencePackService` 增加按键删除接口与删除指标。

### 8.2 第二阶段状态
- 已完成。
- 关键结果：
  - 上下文构建失败语义统一为抛出 `ContextBuildException`。
  - `ContextBuildResult` 收敛为仅承载成功结果。
  - 已从 `DefaultContextBuilder` 抽取 `ContextPolicyResolver` 与 `ContextBudgetRequestFactory`。
  - `DefaultContextBuilder` 收敛为流程编排器并补齐成功/失败指标。
  - `RuntimePreparationService` 已支持带 `stage/errorCode` 的异常传播日志。
  - 新增独立测试覆盖策略解析、预算构建与失败传播链路。

### 8.3 当前风险复评
- `S1-01`（Builder 过载）风险：由高降至中。
- `S1-02`（吞异常）风险：已关闭。
- `S3-01`（请求对象污染）风险：已关闭。
- 其余第三阶段问题保持原计划推进。

### 8.4 第三阶段状态（2026-02-09）
- 已完成。
- 关键结果：
  - 已引入类型化上下文访问层：`ContextRuntimeKeys`、`ContextRuntimeView`、`MutableContextRuntimeView`、`ContextRuntimeViews`。
  - `DefaultContextBuilder` 已完成核心上下文读取收口，不再直接散落关键魔法键访问。
  - `RuntimePreparationService`、`PlanTelemetry` 等运行链路写入已统一到类型化访问门面。
  - 已抽取并接入统一装配策略组件 `PromptContextPolicyApplier`，`DefaultContextAssembler` 与 `PromptAssemblyContextResolver` 使用同一策略实现。
  - `ContextAssembler` 参数语义已落地：`trimReport/pruneResult/compressionResult` 显式映射到 `truncatedSections` 与 `assemblyMetadata`。
  - 未接入主流程的 `DeepResearchWorkflowProperties` 与 `application*.yml` 中对应 `deep-research` 配置已下线。
  - 关键可观测性已补齐：`context_runtime_read_failed_total` 与 `context_assemble_policy_branch_total` 指标已接入。
  - 已新增并更新阶段三测试：`ContextRuntimeViewTest`、`PromptContextPolicyApplierTest` 与相关装配链路测试。

### 8.5 风险复评（第三阶段完成后）
- `S2-01`（Map + 魔法键）风险：由中高降至中低。
- `S2-03`（装配策略重复）风险：已关闭。
- `S3-02`（死配置）风险：已关闭。
- 当前剩余风险主要集中在个别非 context 核心模块的历史 Map 协议访问，可在后续专项治理。
