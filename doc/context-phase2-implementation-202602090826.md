# context 包第二阶段实施记录

## 1. 实施目标
- 统一上下文构建失败语义，消除吞异常与空结果歧义。
- 将 `DefaultContextBuilder` 收敛为流程编排器。
- 抽取策略解析与预算请求构建为可复用组件。
- 补齐单元测试与失败链路回归测试。

## 2. 本次改造范围
- 核心实现：
  - `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`
  - `src/main/java/com/example/agent/capabilities/context/ContextBuildResult.java`
  - `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java`
- 新增组件：
  - `src/main/java/com/example/agent/capabilities/context/builder/policy/ContextPolicyResolver.java`
  - `src/main/java/com/example/agent/capabilities/context/builder/budget/ContextBudgetRequestFactory.java`
  - `src/main/java/com/example/agent/capabilities/context/builder/exception/ContextBuildException.java`
- 测试与文档：
  - `src/test/java/com/example/agent/context/ContextPolicyResolverTest.java`
  - `src/test/java/com/example/agent/context/ContextBudgetRequestFactoryTest.java`
  - `src/test/java/com/example/agent/runtime/RuntimePreparationServiceTest.java`
  - `doc/context-phase2-task-list-202602090754.md`

## 3. 关键实现说明

### 3.1 失败语义统一
- `DefaultContextBuilder.build(...)` 对请求为空与构建失败场景统一抛出 `ContextBuildException`。
- 彻底删除“捕获异常后返回空 `ContextBuildResult`”路径。
- `RuntimePreparationService` 在构建边界显式捕获 `ContextBuildException`，记录 `stage/errorCode` 后继续上抛。

### 3.2 成功结果契约收敛
- `ContextBuildResult` 已调整为仅承载成功语义。
- 通过构造器约束 `snapshot` 和 `metrics` 非空，防止业务层继续使用“判空代表失败”的隐式语义。

### 3.3 Builder 组件拆分
- 新增 `ContextPolicyResolver`：
  - 支持从请求显式策略、运行时上下文、任务上下文解析。
  - 支持 `ContextPolicy` 与 `Map` 两类输入。
  - 解析过程不回写 `ContextBuildRequest`，消除入参污染。
- 新增 `ContextBudgetRequestFactory`：
  - 统一预算优先级链：请求显式值 > 运行时上下文 > 配置 > 默认值。
  - 对非法预算值进行显式校验并抛异常。
  - 统一补齐 `tenantId/workflowId/taskId` 与默认预算策略。
- `DefaultContextBuilder` 主流程改为仅编排：
  - 快照初始化
  - 策略解析
  - 预算请求创建与分配
  - 剪枝/裁剪/压缩
  - 指标与日志发布

### 3.4 可观测性补齐
- 新增成功指标：`context_build_success_total`，标签包含 `hasPolicy/trimmed/compressed`。
- 新增失败指标：`context_build_failed_total`，标签包含 `stage/errorCode`。
- 统一失败日志模板，补齐 `tenantId/workflowId/taskId/stage/errorCode`。

### 3.5 测试补齐
- 新增 `ContextPolicyResolverTest`：覆盖对象策略、Map 策略、空策略与任务上下文回退。
- 新增 `ContextBudgetRequestFactoryTest`：覆盖优先级链、默认回退、预算关闭与非法值处理。
- 新增 `RuntimePreparationServiceTest`：覆盖构建异常传播链路。

## 4. 全量测试结果
- 执行命令：`mvn -q -DskipTests=false test`
- 执行结果：通过。

## 5. 第二阶段结论
- 第二阶段任务（S2-T1 ~ S2-T9）已按清单完成落地。
- 当前上下文构建链路已具备单一失败语义、可观测闭环与更清晰的职责边界。
- 后续可进入第三阶段类型化上下文与跨模块装配策略统一治理。

