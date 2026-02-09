# context 包第三阶段改造任务清单

## 1. 阶段目标
- 目标周期：`1~2` 个迭代
- 目标导向：完成“类型化上下文访问 + 装配策略统一 + 无效配置清理”，进一步提升包内聚性与可维护性。
- 完成标准：
  - 核心链路不再依赖字符串键直读 `Map`。
  - 上下文装配规则收敛为单一实现，不再存在重复逻辑漂移。
  - 未接入主流程的研究配置完成下线，消除死配置认知负担。

## 2. 改造范围
- 核心实现：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java`
  - `src/main/java/com/example/agent/capabilities/context/ContextAssembler.java`
  - `src/main/java/com/example/agent/capabilities/llm/prompt/PromptAssemblyContextResolver.java`
- 运行时上下文相关：
  - `src/main/java/com/example/agent/runtime/step/RuntimeContext.java`
  - `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java`
- 研究配置清理：
  - `src/main/java/com/example/agent/capabilities/context/research/DeepResearchWorkflowProperties.java`
  - `src/main/resources/application*.yml` 中对应配置项
- 建议新增目录：
  - `src/main/java/com/example/agent/capabilities/context/runtime/`
  - `src/main/java/com/example/agent/capabilities/context/assembly/`
- 测试范围：
  - `src/test/java/com/example/agent/context/`
  - `src/test/java/com/example/agent/runtime/`
  - `src/test/java/com/example/agent/capabilities/llm/prompt/`

## 3. 方案约束（按新项目标准）
- 不保留历史分支，不做“新旧逻辑并存”的兼容判断。
- 对核心链路采用单一语义：类型化读取失败即明确日志与异常，不允许静默降级。
- 涉及接口签名调整时同步修改调用方与测试，不保留过渡 API。

## 4. 第三阶段任务清单（可直接实施）

### S3-T1 建立类型化上下文访问层（最高优先级）
- 改造动作：
  - 新增运行时上下文键定义与访问门面，例如：
    - `ContextRuntimeKeys`
    - `ContextRuntimeView`
    - `MutableContextRuntimeView`
  - 将常用键（预算、策略、工具、任务约束、证据包等）统一为常量与类型化 getter/setter。
  - 明确非法类型读取策略：记录告警并返回空，禁止在业务层 scattered `instanceof`。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/runtime/*.java`
  - `src/main/java/com/example/agent/runtime/step/RuntimeContext.java`
- 验收标准：
  - 核心上下文键不再散落硬编码字符串。
  - 访问规则可复用、可单测。

### S3-T2 替换 `DefaultContextBuilder` 的字符串键直读（最高优先级）
- 改造动作：
  - 使用 `ContextRuntimeView` 替换 `DefaultContextBuilder` 中对 `Map<String, Object>` 的直接读取。
  - 移除 Builder 内与上下文解析相关的通用读写工具方法（如 `readString/readInteger/readBoolean/readStringList`）。
  - 统一由访问层负责上下文类型转换，Builder 仅做流程编排。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
- 验收标准：
  - Builder 核心流程不再出现魔法键。
  - 类体积继续下降，职责进一步收敛。

### S3-T3 统一上下文装配策略实现（高优先级）
- 改造动作：
  - 抽取共享装配策略组件（建议：`PromptContextPolicyApplier`）。
  - `DefaultContextAssembler` 与 `PromptAssemblyContextResolver` 统一调用该组件。
  - 统一字段优先级规则，避免 system/developer 等字段出现双实现偏差。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java`
  - `src/main/java/com/example/agent/capabilities/llm/prompt/PromptAssemblyContextResolver.java`
  - `src/main/java/com/example/agent/capabilities/context/assembly/*.java`
- 验收标准：
  - 装配策略实现单点化。
  - 同一输入在两条调用链得到一致输出。

### S3-T4 落地 `ContextAssembler` 参数语义（高优先级）
- 改造动作：
  - 对 `trimReport/pruneResult/compressionResult` 建立显式装配规则，不再“仅透传未使用”。
  - 在最终提示词上下文中增加可控摘要块（如裁剪摘要、压缩摘要、剪枝摘要）。
  - 若字段最终确认无业务价值，直接调整接口签名并同步调用方，不保留冗余参数。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/ContextAssembler.java`
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextAssembler.java`
  - 调用方及对应测试
- 验收标准：
  - 接口表达与实现行为一致。
  - 不再存在“签名有参数、逻辑无落地”的歧义。

### S3-T5 下线未接入配置 `DeepResearchWorkflowProperties`（中高优先级）
- 改造动作：
  - 删除未接入主流程且无测试覆盖的配置类与关联配置项。
  - 清理相关文档与注释中的无效引用。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/research/DeepResearchWorkflowProperties.java`
  - `src/main/resources/application*.yml`
- 验收标准：
  - 代码中不再存在“看似可配置但实际无效”的研究配置入口。

### S3-T6 补齐类型化访问与装配链路可观测性（中优先级）
- 改造动作：
  - 新增上下文读取异常指标（建议：`context_runtime_read_failed_total`，标签 `key/type`）。
  - 新增装配策略分支命中指标（建议：`context_assemble_policy_branch_total`）。
  - 统一异常日志模板，必须包含 `tenantId/workflowId/taskId/key/stage`。
- 影响文件：
  - 上下文访问层与装配组件实现文件
  - `DefaultContextBuilder`、`DefaultContextAssembler`
- 验收标准：
  - 关键异常可通过日志与指标双向检索。

### S3-T7 单元测试与架构守卫（高优先级）
- 改造动作：
  - 新增测试：
    - `ContextRuntimeViewTest`：覆盖类型转换、非法值、默认回退。
    - `PromptContextPolicyApplierTest`：覆盖装配优先级与冲突处理。
  - 更新测试：
    - `DefaultContextBuilderTest`：移除对旧 `Map` 读写细节的间接依赖。
    - `ContextAssembler` 与 `PromptAssemblyContextResolver` 相关测试：验证统一策略输出一致。
  - 增加架构守卫测试：禁止在核心类新增魔法键直读。
- 建议文件：
  - `src/test/java/com/example/agent/context/ContextRuntimeViewTest.java`
  - `src/test/java/com/example/agent/context/PromptContextPolicyApplierTest.java`
  - `src/test/java/com/example/agent/context/DefaultContextBuilderTest.java`
- 验收标准：
  - 新组件核心分支有测试覆盖。
  - 关键类中新增魔法键行为可被守卫测试阻断。

### S3-T8 集成回归与门禁（高优先级）
- 改造动作：
  - 对运行时准备、上下文构建、提示词装配端到端链路执行回归。
  - 验证成功链路与失败链路行为不退化。
- 固定门禁：
  - 每子阶段完成后执行：`mvn -q -DskipTests=false test`
- 验收标准：
  - 无已知失败带入下一子阶段。

### S3-T9 文档同步与收口（中优先级）
- 改造动作：
  - 更新评审文档中的第三阶段状态与风险复评。
  - 输出第三阶段实施记录文档。
- 建议文件：
  - `doc/context-package-design-review-202602090716.md`
  - `doc/context-phase3-implementation-<YYYYMMDDHHmm>.md`

## 5. 执行顺序建议
- 第 1 天：`S3-T1`
- 第 2 天：`S3-T2 + S3-T3`
- 第 3 天：`S3-T4 + S3-T5`
- 第 4 天：`S3-T6 + S3-T7`
- 第 5 天：`S3-T8 + S3-T9`

## 6. 第三阶段 DoD
- 架构：核心链路完成类型化上下文访问改造，`DefaultContextBuilder` 不再魔法键直读。
- 语义：上下文装配策略实现单点化，接口语义与行为一致。
- 质量：新增组件具备独立测试并通过全量回归。
- 文档：第三阶段任务清单与实施记录完整落盘。

## 7. 完成状态（2026-02-09）
- `S3-T1`：已完成。
- `S3-T2`：已完成。
- `S3-T3`：已完成。
- `S3-T4`：已完成。
- `S3-T5`：已完成。
- `S3-T6`：已完成。
- `S3-T7`：已完成。
- `S3-T8`：已完成。
- `S3-T9`：已完成。

### 7.1 阶段收口说明
- 架构守卫测试已覆盖阶段三核心键访问治理，新增散落魔法键会被门禁阻断。
- 上下文访问层、装配策略层、配置清理与回归验证均已按清单落地。
- 全量测试命令 `mvn -q -DskipTests=false test` 已执行并通过。
