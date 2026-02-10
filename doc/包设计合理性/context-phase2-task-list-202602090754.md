# context 包第二阶段改造任务清单

## 1. 阶段目标
- 目标周期：`1` 个迭代
- 目标导向：完成 `DefaultContextBuilder` 结构拆分，统一失败语义，消除“吞异常 + 空结果”歧义
- 完成标准：
  - `DefaultContextBuilder` 职责收敛为流程编排
  - 策略解析与预算请求生成完成独立组件化
  - 上下文构建失败语义唯一且可观测

## 2. 改造范围
- 核心实现：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/capabilities/context/ContextBuildResult.java`
  - `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java`
- 新增组件（建议目录）：
  - `src/main/java/com/example/agent/capabilities/context/builder/policy/`
  - `src/main/java/com/example/agent/capabilities/context/builder/budget/`
  - `src/main/java/com/example/agent/capabilities/context/builder/exception/`
- 测试范围：
  - `src/test/java/com/example/agent/context/`
  - `src/test/java/com/example/agent/runtime/`

## 3. 方案约束（按新项目标准）
- 不保留历史兼容分支，不做“老行为兜底 if/else”并存。
- 采用单一新语义，旧实现直接替换或删除。
- 新增组件必须可复用，不绑定单一调用路径。

## 4. 第二阶段任务清单（可直接实施）

### S2-T1 统一构建失败语义（最高优先级）
- 状态：**已完成**
- 设计决策：采用“**构建失败即抛异常**”单一语义。
- 改造动作：
  - 新增 `ContextBuildException`（包含 `errorCode`、`stage`、`tenantId`、`workflowId`）。
  - 删除 `DefaultContextBuilder.build(...)` 内部吞异常返回空结果的逻辑。
  - 在 `RuntimePreparationService` 边界统一捕获并上抛，不再接受模糊空结果。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java`
  - `src/main/java/com/example/agent/capabilities/context/builder/exception/ContextBuildException.java`
- 验收标准：
  - 构建失败不再“静默成功”。
  - 日志与异常可直接定位失败阶段。

### S2-T2 重定义 `ContextBuildResult` 成功契约（最高优先级）
- 状态：**已完成**
- 问题：当前结果对象缺少明确成功语义，容易与失败空结果混淆。
- 改造动作：
  - 将 `ContextBuildResult` 明确为“仅承载成功结果”。
  - 对关键字段建立非空契约：`snapshot`、`metrics`（必要时通过构造器或工厂方法保证）。
  - 删除与失败表达相关的暧昧字段或使用方式。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/ContextBuildResult.java`
  - 使用方文件（主要是 `RuntimePreparationService` 与测试）
- 验收标准：
  - 业务代码中不再出现“通过判空推断失败”的模式。

### S2-T3 抽取 `ContextPolicyResolver`（高优先级）
- 状态：**已完成**
- 改造动作：
  - 从 `DefaultContextBuilder` 抽离：
    - `resolvePolicy(...)`
    - `resolvePolicyFromContext(...)`
    - `buildPolicyFromMap(...)`
    - `hasPolicyContent(...)`
  - 删除对入参 `ContextBuildRequest` 的副作用写入（移除 `request.setPolicy(...)` 这类行为）。
  - 暴露纯函数式入口：输入请求与上下文，输出解析后的策略对象。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/capabilities/context/builder/policy/ContextPolicyResolver.java`
- 验收标准：
  - 解析策略逻辑不依赖 `DefaultContextBuilder` 私有状态。
  - 同一输入得到稳定输出，无请求对象污染。

### S2-T4 抽取 `ContextBudgetRequestFactory`（高优先级）
- 状态：**已完成**
- 改造动作：
  - 从 `DefaultContextBuilder` 抽离：
    - `resolveBudgetRequest(...)`
    - `fillBudgetRequest(...)`
    - 预算相关的读取与默认填充逻辑
  - 对输入输出做强约束：返回合法 `ContextBudgetRequest` 或抛异常，不返回语义不明的对象。
  - 统一预算优先级链：请求显式值 > 上下文 > 配置 > 默认值。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/capabilities/context/builder/budget/ContextBudgetRequestFactory.java`
- 验收标准：
  - 预算请求构建逻辑不再散落在 Builder 主流程中。
  - 默认值策略在单元测试中可验证。

### S2-T5 收敛 `DefaultContextBuilder` 为编排器（高优先级）
- 状态：**已完成**
- 改造动作：
  - Builder 只保留流程顺序控制：
    - 快照初始化
    - 策略解析调用
    - 预算请求生成与分配
    - 剪枝/裁剪/压缩串联
    - 指标与事件发布
  - 删除已抽离逻辑的私有工具方法，降低类体积与认知负担。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
- 验收标准：
  - 文件行数与方法数量显著下降。
  - 无跨职责工具方法残留。

### S2-T6 统一构建可观测性（中高优先级）
- 状态：**已完成**
- 改造动作：
  - 新增失败指标：`context_build_failed_total`（标签建议：`stage`、`errorCode`）。
  - 成功指标保留：`context_build_success_total`（标签建议：`hasPolicy`、`trimmed`、`compressed`）。
  - 异常日志统一模板，必须带 `tenantId/workflowId/taskId/stage/errorCode`。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java`
- 验收标准：
  - 任一失败都可在日志和指标中双向检索。

### S2-T7 单元测试重构（高优先级）
- 状态：**已完成**
- 改造动作：
  - 为 `ContextPolicyResolver` 增加独立测试：
    - 解析 `ContextPolicy` 对象
    - 解析 `Map` 结构
    - 空策略判定
  - 为 `ContextBudgetRequestFactory` 增加独立测试：
    - 预算优先级链
    - 默认值回退
    - 非法值处理
  - 更新 `DefaultContextBuilderTest`，删除对已抽离私有行为的间接覆盖。
- 建议文件：
  - `src/test/java/com/example/agent/context/ContextPolicyResolverTest.java`
  - `src/test/java/com/example/agent/context/ContextBudgetRequestFactoryTest.java`
  - `src/test/java/com/example/agent/context/DefaultContextBuilderTest.java`
- 验收标准：
  - 新组件测试覆盖核心分支。
  - 旧测试不再依赖已删除实现细节。

### S2-T8 集成测试与回归测试（高优先级）
- 状态：**已完成**
- 改造动作：
  - 增加“构建失败抛异常”链路测试：
    - `RuntimePreparationService` 能正确传播构建异常。
  - 保持成功链路回归：
    - 构建成功后快照、预算、事件行为不退化。
- 建议文件：
  - `src/test/java/com/example/agent/runtime/RuntimePreparationServiceTest.java`
  - 现有相关集成测试文件
- 验收标准：
  - 成功链路与失败链路均有自动化验证。

### S2-T9 文档同步（中优先级）
- 状态：**已完成**
- 改造动作：
  - 更新评审文档中的第二阶段状态与新语义说明。
  - 输出第二阶段实施记录文档（与第一阶段同格式）。
- 建议文件：
  - `doc/context-package-design-review-202602090716.md`
  - `doc/context-phase2-implementation-<YYYYMMDDHHmm>.md`

## 5. 执行顺序建议
- 第 1 天：`S2-T1 + S2-T2`（先统一失败语义）
- 第 2 天：`S2-T3 + S2-T4`（拆策略与预算）
- 第 3 天：`S2-T5 + S2-T6`（收敛 Builder 与可观测）
- 第 4 天：`S2-T7 + S2-T8`（测试补齐与回归）
- 第 5 天：`S2-T9`（文档同步与收口）

## 6. 每阶段测试门禁（必须执行）
- 子阶段完成后固定执行：`mvn -q -DskipTests=false test`
- 不允许带已知失败进入下一子阶段。
- 若失败，先修复再继续。

## 7. 第二阶段 DoD
- 架构：`DefaultContextBuilder` 从“实现+工具混合体”收敛为“流程编排器”。
- 语义：上下文构建失败语义唯一，不再存在吞异常路径。
- 质量：新增组件均有独立测试，且全量测试通过。
- 文档：任务清单与实施记录完整落盘。
