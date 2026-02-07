# 阶段三任务拆分

## P1
工具上下文减负

### 任务一
工具目录服务能力对齐

#### 范围
- 新增工具目录服务接口
- `ToolCatalogService`
- `listToolSummaries`
- `getToolSchema`
- 工具摘要输出仅包含名称、描述、标签、成本等级、时延等级与授权范围
- 工具输入结构读取支持缓存过期控制
- 缓存过期时间支持配置化
- 增加关键路径日志与指标

#### 涉及文件
- `src/main/java/com/example/agent/tools/ToolCatalogService.java`
- `src/main/java/com/example/agent/tools/ToolCatalog.java`
- `src/main/java/com/example/agent/tools/DefaultToolCatalog.java`
- `src/main/java/com/example/agent/tools/McpToolDefinition.java`
- `src/main/java/com/example/agent/tools/ToolSummary.java`
- `src/main/java/com/example/agent/tools/ToolQuery.java`
- `src/main/java/com/example/agent/agentcore/ToolCache.java`
- `src/main/java/com/example/agent/observability/MetricsPublisher.java`
- `src/main/resources/application.yml`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 工具摘要查询返回稳定且无输入结构字段
- 工具输入结构查询对未知工具返回空
- 缓存命中时不重复访问工具注册表
- 关键路径日志包含命中与加载信息
- 自动化测试覆盖摘要与输入结构查询
- `DefaultToolCatalogTest`

#### 测试
- `mvn test -Dtest=DefaultToolCatalogTest`

#### 指标
- `schema_cache_hit`
- `schema_loaded`
- 缓存命中率与加载次数可监控

#### 回滚点
- 保持旧的工具目录读取路径可用
- 通过配置开关回退为旧模式

### 任务二
模型工具解析摘要注入与按需加载

#### 范围
- 默认仅注入工具摘要
- 指定工具或必要场景才加载输入结构
- 引入缓存过期控制
- 记录工具注入体量与加载指标
- 关键路径日志补充
- `ModelToolResolver`

#### 涉及文件
- `src/main/java/com/example/agent/model/ModelToolResolver.java`
- `src/main/java/com/example/agent/model/ModelToolDefinition.java`
- `src/main/java/com/example/agent/model/ModelRequest.java`
- `src/main/java/com/example/agent/tools/ToolCatalogService.java`
- `src/main/java/com/example/agent/tools/ToolCatalog.java`
- `src/main/java/com/example/agent/agentcore/ToolCache.java`
- `src/main/java/com/example/agent/observability/MetricsPublisher.java`
- `src/test/java/com/example/agent/model/ModelToolResolverTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 未指定工具时注入的工具不包含输入结构
- 指定工具时仅注入对应工具且包含输入结构
- 缓存命中时减少输入结构加载次数
- 工具注入体量指标可记录
- 自动化测试覆盖摘要注入与按需加载

#### 测试
- `mvn test -Dtest=ModelToolResolverTest`

#### 指标
- `schema_cache_hit`
- `schema_loaded`
- `tools_injected_size`
- 工具注入体量可量化

#### 回滚点
- 保持全量工具输入结构注入的回退开关
- 异常时可回退为旧的工具注入逻辑

## P2
证据包可追溯性

### 任务一
证据包模型扩展

#### 范围
- 扩展证据包结构，覆盖引用、工具调用与记忆使用
- 保持旧字段兼容
- 裁剪逻辑支持新字段
- 关键路径日志补充
- `citations`
- `toolCalls`
- `memoriesUsed`

#### 涉及文件
- `src/main/java/com/example/agent/context/EvidencePack.java`
- `src/main/java/com/example/agent/context/EvidenceItem.java`
- `src/main/java/com/example/agent/context/Citation.java`
- `src/main/java/com/example/agent/context/MemoryRef.java`
- `src/main/java/com/example/agent/context/ToolCallState.java`
- `src/main/java/com/example/agent/context/WorkingMemory.java`
- `src/main/java/com/example/agent/context/ContextPolicy.java`
- `src/main/java/com/example/agent/budget/DefaultContextPruner.java`
- `src/test/java/com/example/agent/budget/DefaultContextPrunerTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 证据包可包含引用、工具调用与记忆使用
- 旧字段与已有序列化保持兼容
- 裁剪策略可限制新增字段数量
- 自动化测试覆盖裁剪逻辑
- `DefaultContextPrunerTest`

#### 测试
- `mvn test -Dtest=DefaultContextPrunerTest`

#### 指标
- `evidence_pack_items`
- `evidence_pack_pruned`
- 证据包条目数量与裁剪数量可监控

#### 回滚点
- 保留旧字段并允许空结构回退
- 通过配置开关关闭新增统计

### 任务二
证据包写入链路补齐

#### 范围
- 工具调用写入证据包
- 记忆召回写入证据包
- 构建上下文时合并证据包
- 关键路径日志补充
- `ToolExecutor`
- `MemoryRecallService`
- `DefaultContextBuilder`

#### 涉及文件
- `src/main/java/com/example/agent/agentcore/ToolExecutor.java`
- `src/main/java/com/example/agent/memory/MemoryRecallService.java`
- `src/main/java/com/example/agent/memory/MemoryRecallResult.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/context/ContextBuildRequest.java`
- `src/main/java/com/example/agent/context/WorkingMemory.java`
- `src/main/java/com/example/agent/context/EvidencePack.java`
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
- `src/test/java/com/example/agent/agentcore/ToolExecutorTest.java`
- `src/test/java/com/example/agent/memory/MemoryRecallServiceTest.java`
- `src/test/java/com/example/agent/context/DefaultContextBuilderTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 工具调用后证据包包含工具调用记录
- 记忆召回后证据包包含记忆引用
- 构建上下文结果包含证据包
- 日志记录写入与合并次数
- 自动化测试覆盖写入链路
- `ToolExecutorTest`
- `MemoryRecallServiceTest`
- `DefaultContextBuilderTest`

#### 测试
- `mvn test -Dtest=ToolExecutorTest`
- `mvn test -Dtest=MemoryRecallServiceTest`
- `mvn test -Dtest=DefaultContextBuilderTest`

#### 指标
- `evidence_tool_calls`
- `evidence_memories_used`
- 写入链路可量化

#### 回滚点
- 保持证据包为空时的兼容路径
- 通过配置开关关闭写入链路

### 任务三
事件载荷证据包统计透传

#### 范围
- 上下文事件载荷增加证据包统计字段
- 统计字段缺失时保持兼容
- 关键路径日志补充
- `ContextEventPublisher`
- `ContextSnapshotSummary`
- `ContextEventPayload`
- `evidenceToolCallCount`
- `evidenceMemoryCount`
- `evidenceCitationCount`

#### 涉及文件
- `src/main/java/com/example/agent/streaming/ContextEventPublisher.java`
- `src/main/java/com/example/agent/streaming/ContextSnapshotSummary.java`
- `src/main/java/com/example/agent/streaming/ContextEventPayload.java`
- `src/test/java/com/example/agent/streaming/ContextEventPublisherTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 事件载荷包含证据包统计字段
- 统计字段为空时不影响现有消费者
- 自动化测试覆盖事件载荷
- `ContextEventPublisherTest`

#### 测试
- `mvn test -Dtest=ContextEventPublisherTest`

#### 指标
- `context_event_evidence_pack`
- 证据包事件统计可监控

#### 回滚点
- 保持旧字段输出不变
- 通过配置开关关闭新增统计

## P3
预算驱动裁剪与压缩联动

### 任务一
预算策略模型与分配联动

#### 范围
- 新增预算策略模型，支持比例分配与裁剪顺序配置
- 支持压缩触发条件配置
- 预算分配器读取策略并保持默认行为
- 关键路径日志补充
- `ContextBudgetPolicy`

#### 涉及文件
- `src/main/java/com/example/agent/budget/ContextBudgetPolicy.java`
- `src/main/java/com/example/agent/budget/ContextBudgetRequest.java`
- `src/main/java/com/example/agent/budget/DefaultContextBudgetAllocator.java`
- `src/main/java/com/example/agent/budget/ContextBudgetAllocation.java`
- `src/main/java/com/example/agent/budget/ContextSection.java`
- `src/main/java/com/example/agent/context/ContextPolicy.java`
- `src/main/resources/application.yml`
- `src/test/java/com/example/agent/budget/DefaultContextBudgetAllocatorTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 不提供策略时默认比例不变
- 提供策略时分配比例按配置生效
- 配置缺失时使用默认值
- 自动化测试覆盖分配逻辑
- `DefaultContextBudgetAllocatorTest`

#### 测试
- `mvn test -Dtest=DefaultContextBudgetAllocatorTest`

#### 指标
- `context_budget_policy_applied`
- `context_budget_total_tokens`
- 预算策略应用情况可监控

#### 回滚点
- 策略为空时保持默认分配
- 通过配置开关关闭策略读取

### 任务二
超预算裁剪顺序固化与指标

#### 范围
- 固化超预算裁剪顺序
- 裁剪结果记录原因与类型
- 记录裁剪日志与指标
- 与现有裁剪结果兼容

#### 涉及文件
- `src/main/java/com/example/agent/budget/DefaultContextPruner.java`
- `src/main/java/com/example/agent/budget/ContextPruneRequest.java`
- `src/main/java/com/example/agent/budget/ContextPruneResult.java`
- `src/main/java/com/example/agent/budget/PrunedItem.java`
- `src/main/java/com/example/agent/context/ContextPolicy.java`
- `src/main/java/com/example/agent/budget/ContextSection.java`
- `src/main/java/com/example/agent/observability/MetricsPublisher.java`
- `src/test/java/com/example/agent/budget/DefaultContextPrunerTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 超预算时按固定顺序裁剪
- 裁剪结果包含原因与数量
- 日志记录裁剪顺序与结果
- 自动化测试覆盖裁剪逻辑
- `DefaultContextPrunerTest`

#### 测试
- `mvn test -Dtest=DefaultContextPrunerTest`

#### 指标
- `context_prune_over_budget`
- `context_pruned_items`
- `context_pruned_section`
- 裁剪结果可量化

#### 回滚点
- 策略为空时保持原裁剪顺序
- 通过配置开关关闭新增指标

### 任务三
预算触发压缩联动

#### 范围
- 预算超限触发压缩
- 与预算管理器联动读取预算信息
- 压缩结果写入日志与指标
- 配置化开关保持默认行为
- `TokenBudgetManager`

#### 涉及文件
- `src/main/java/com/example/agent/budget/TokenBudgetManager.java`
- `src/main/java/com/example/agent/memory/MemoryStore.java`
- `src/main/java/com/example/agent/memory/CompressionRequest.java`
- `src/main/java/com/example/agent/memory/MemoryPolicy.java`
- `src/main/java/com/example/agent/memory/MemoryRecallService.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/resources/application.yml`
- `src/test/java/com/example/agent/memory/MemoryStoreTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 超预算条件满足时触发压缩
- 未超预算时不触发压缩
- 日志记录触发原因与结果
- 自动化测试覆盖压缩触发
- `MemoryStoreTest`

#### 测试
- `mvn test -Dtest=MemoryStoreTest`

#### 指标
- `memory_compress_triggered`
- `budget_over_threshold`
- 压缩触发情况可监控

#### 回滚点
- 通过配置开关关闭预算触发压缩
- 默认保持现有自动压缩策略

## P4
可选脱敏与拒写最小化

### 任务一
脱敏与拒写最小化门禁

#### 范围
- 风险门禁开启时执行脱敏
- 写入最小化控制记忆落盘
- 默认关闭并可配置
- 关键路径日志与指标
- `ContextPolicy`
- `MemoryWriteService`

#### 涉及文件
- `src/main/java/com/example/agent/context/ContextPolicy.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/memory/MemoryWriteService.java`
- `src/main/java/com/example/agent/memory/MemoryWriteProperties.java`
- `src/main/java/com/example/agent/context/WorkingMemory.java`
- `src/main/resources/application.yml`
- `src/test/java/com/example/agent/memory/MemoryWriteServiceTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 风险门禁开启时敏感字段被遮蔽
- 门禁关闭时行为不变
- 写入最小化生效并记录日志
- 自动化测试覆盖门禁逻辑
- `MemoryWriteServiceTest`

#### 测试
- `mvn test -Dtest=MemoryWriteServiceTest`

#### 指标
- `sensitive_mask_applied`
- `memory_write_skipped`
- 脱敏与拒写情况可监控

#### 回滚点
- 通过配置开关关闭脱敏与拒写
- 默认保持现有写入策略

## P5
可选审批确认链路

### 任务一
高风险工具调用审批

#### 范围
- 高风险工具调用触发审批请求
- 与运行时审批链路对齐
- 默认关闭并可配置
- 关键路径日志与指标
- `CapabilityBoundaryEvaluator`
- `ExecutionControlService`
- `ApprovalController`

#### 涉及文件
- `src/main/java/com/example/agent/agentcore/ToolExecutor.java`
- `src/main/java/com/example/agent/agentcore/EnforcementGateway.java`
- `src/main/java/com/example/agent/evaluation/CapabilityBoundaryEvaluator.java`
- `src/main/java/com/example/agent/runtime/ExecutionControlService.java`
- `src/main/java/com/example/agent/gateway/controller/ApprovalController.java`
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
- `src/main/java/com/example/agent/runtime/ReactLoopService.java`
- `src/test/java/com/example/agent/runtime/AgentRuntimeApprovalIntegrationTest.java`

#### 不允许做什么
- 禁止大重构
- 禁止破坏现有兼容性
- 禁止修改阶段二已稳定接口，除非提供适配层

#### 验收点
- 高风险工具调用生成审批请求
- 审批拒绝时工具调用被阻断
- 低风险调用不受影响
- 自动化测试覆盖审批链路
- `AgentRuntimeApprovalIntegrationTest`

#### 测试
- `mvn test -Dtest=AgentRuntimeApprovalIntegrationTest`

#### 指标
- `tool_approval_requested`
- `tool_approval_granted`
- `tool_approval_denied`
- 审批链路可量化

#### 回滚点
- 通过配置开关关闭审批链路
- 默认保持现有审批行为