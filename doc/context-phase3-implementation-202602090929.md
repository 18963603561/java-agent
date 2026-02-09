# context 包第三阶段实施记录

## 1. 实施目标
- 完成类型化上下文访问层落地，消除核心链路关键魔法键散落访问。
- 统一提示词装配策略实现，保证不同调用链语义一致。
- 下线未接入主流程的死配置，减少配置认知负担。
- 补齐第三阶段新增链路的可观测性与测试门禁。

## 2. 本次改造范围
- 核心实现：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java`
  - `src/main/java/com/example/agent/capabilities/context/PromptAssemblyInput.java`
  - `src/main/java/com/example/agent/capabilities/llm/prompt/PromptAssemblyContextResolver.java`
  - `src/main/java/com/example/agent/capabilities/llm/prompt/DefaultPromptAssembler.java`
  - `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java`
- 新增组件：
  - `src/main/java/com/example/agent/capabilities/context/runtime/ContextRuntimeKeys.java`
  - `src/main/java/com/example/agent/capabilities/context/runtime/ContextRuntimeView.java`
  - `src/main/java/com/example/agent/capabilities/context/runtime/MutableContextRuntimeView.java`
  - `src/main/java/com/example/agent/capabilities/context/runtime/ContextRuntimeViews.java`
  - `src/main/java/com/example/agent/capabilities/context/assembly/PromptContextPolicyApplier.java`
- 配置清理：
  - 删除 `src/main/java/com/example/agent/capabilities/context/research/DeepResearchWorkflowProperties.java`
  - 清理 `src/main/resources/application*.yml` 中 `deep-research` 配置。
- 测试：
  - `src/test/java/com/example/agent/context/ContextRuntimeViewTest.java`
  - `src/test/java/com/example/agent/context/PromptContextPolicyApplierTest.java`
  - `src/test/java/com/example/agent/context/ContextAssemblerDoesNotPassSnapshotToTemplateTest.java`
  - `src/test/java/com/example/agent/runtime/MapKeyAccessGuardTest.java`

## 3. 关键实现说明

### 3.1 类型化上下文访问层落地
- 统一关键运行时键到 `ContextRuntimeKeys`，避免硬编码字符串散落。
- `ContextRuntimeView` 提供字符串、整型、布尔、列表、映射与证据包等统一读取能力。
- 类型读取失败时统一记录告警并上报 `context_runtime_read_failed_total`，避免业务层大量 `instanceof`。
- `MutableContextRuntimeView` 提供受控写入能力，运行时准备与构建链路统一通过门面写回。

### 3.2 Builder 读写收口
- `DefaultContextBuilder` 关键字段读取已切换到 `ContextRuntimeView`。
- 删除 Builder 内部重复的通用 Map 解析工具方法，职责收敛为流程编排。
- `RuntimePreparationService` 通过 `MutableContextRuntimeView` 写入 `contextSnapshot/contextBudget/contextPrune/promptAssemblyInput/evidencePack`。

### 3.3 装配策略单点化
- 抽取 `PromptContextPolicyApplier`，统一 system/developer/user 填充规则。
- `DefaultContextAssembler` 与 `PromptAssemblyContextResolver` 均复用同一策略组件，消除双实现漂移。
- 新增分支命中指标 `context_assemble_policy_branch_total`，可观测策略行为。

### 3.4 ContextAssembler 参数语义落地
- `trimReport/pruneResult/compressionResult` 不再仅透传。
- 在 `PromptAssemblyInput` 中显式写入：
  - `truncatedSections`：标记 `context_trimmed/context_pruned/context_compressed`
  - `assemblyMetadata`：记录裁剪、剪枝、压缩过程摘要信息。
- `DefaultPromptAssembler` 读取装配输入后按统一策略补齐并执行裁剪。

### 3.5 死配置下线
- 删除未接入主流程且无实际生效路径的 `DeepResearchWorkflowProperties`。
- 清理全部主配置与样例配置中的 `deep-research` 节点，避免“看似可配、实际无效”。

### 3.6 架构守卫与阶段修正
- `MapKeyAccessGuardTest` 作为关键键访问守门，继续保留。
- 在阶段三收口中，将触发守卫的关键访问点统一替换为 `ContextRuntimeKeys` 常量读取，避免新增散落硬编码。

## 4. 回归验证
- 执行命令：`mvn -q -DskipTests=false test`
- 执行结果：通过。
- 说明：在中途出现守卫失败与一次接口测试偶发失败后，均已复测并完成全量通过收口。

## 5. 第三阶段结论
- 第三阶段任务（S3-T1 ~ S3-T9）已按清单完成落地。
- context 包核心链路的上下文访问与装配策略已形成可复用、可观测、可测试的统一实现。
- 后续可将同类 Map 协议治理扩展到非 context 核心模块，进一步降低全仓协议漂移风险。

