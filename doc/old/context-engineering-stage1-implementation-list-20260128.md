# 阶段一实现落地清单（结合现有代码点位）

## 输入依据
```
doc/context-engineering-stage1-tasks-20260128.md
doc/context-engineering-interface-draft-20260128.md
```

## 说明
本清单用于阶段一实现落地，强调在不破坏既有主流程的前提下，通过扩展方式接入上下文装配闭环能力。

## 落地清单

### 一、上下文分层模型与快照结构
任务编号
```
T1
```

代码点位
```
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
src/main/java/com/example/agent/common/TaskRequest.java
src/main/java/com/example/agent/auth/TenantContext.java
src/main/java/com/example/agent/memory/MemoryRecallService.java
src/main/java/com/example/agent/runtime/ObservationWindowBuffer.java
```

关键方法
```
AgentRuntime#run
AgentRuntime#buildRequestWithContext
ReactLoopService#run
ReactLoopService#think
ReactLoopService#observe
MemoryRecallService#recall
```

落地要点
- 在运行入口生成上下文快照并写入运行时上下文
- 将租户与请求元信息映射为运行时元信息层
- 将任务输入、成功标准与失败策略映射为任务意图层
- 将记忆召回结果映射为工作记忆与领域知识引用
- 将观察窗口内容映射为工作记忆摘要

新增类
```
src/main/java/com/example/agent/context/ContextSnapshot.java
src/main/java/com/example/agent/context/RuntimeMeta.java
src/main/java/com/example/agent/context/RoleBoundary.java
src/main/java/com/example/agent/context/TaskIntent.java
src/main/java/com/example/agent/context/WorkingMemory.java
src/main/java/com/example/agent/context/DomainKnowledge.java
src/main/java/com/example/agent/context/LongTermMemory.java
```

### 二、上下文构建器与装配模板
任务编号
```
T2
```

代码点位
```
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/planning/PlannerService.java
src/main/java/com/example/agent/reflection/ReflectionService.java
src/main/java/com/example/agent/runtime/FinalOutputService.java
src/main/java/com/example/agent/research/ResearchPipeline.java
src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java
src/main/java/com/example/agent/reasoning/DebateCoordinator.java
src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
```

关键方法
```
PlannerService#plan
ReflectionService#reflect
FinalOutputService#finalizeOutput
ResearchPipeline#run
ChainOfThoughtService#run
DebateCoordinator#run
MultiAgentCoordinator#coordinate
ReactLoopService#think
```

落地要点
- 统一通过上下文构建器生成快照与装配结果
- 将装配结果注入模型请求，避免分散拼装
- 对装配过程记录结构化指标用于追踪

新增类
```
src/main/java/com/example/agent/context/ContextBuilder.java
src/main/java/com/example/agent/context/ContextAssembler.java
src/main/java/com/example/agent/context/ContextBuildRequest.java
src/main/java/com/example/agent/context/ContextBuildResult.java
```

### 三、三段式提示词模板与多角色消息结构
任务编号
```
T3
```

代码点位
```
src/main/java/com/example/agent/model/ModelRequest.java
src/main/java/com/example/agent/model/ModelInvocationService.java
src/main/java/com/example/agent/model/DefaultModelProvider.java
src/main/java/com/example/agent/model/LlmClient.java
src/main/java/com/example/agent/model/DefaultLlmClient.java
```

关键方法
```
DefaultModelProvider#buildOpenAiRequestBody
ModelInvocationService#invoke
```

落地要点
- 模型请求支持多角色消息结构
- 提示词模板输出三段式结构并可裁剪
- 事件载荷记录提示词摘要与裁剪结果

新增类
```
src/main/java/com/example/agent/model/PromptTemplate.java
src/main/java/com/example/agent/model/PromptAssembler.java
src/main/java/com/example/agent/model/PromptBundle.java
src/main/java/com/example/agent/model/PromptMessage.java
src/main/java/com/example/agent/model/PromptRole.java
```

### 四、工具目录摘要化与按需加载
任务编号
```
T4
```

代码点位
```
src/main/java/com/example/agent/agentcore/ToolRegistry.java
src/main/java/com/example/agent/agentcore/ToolCache.java
src/main/java/com/example/agent/model/ModelToolResolver.java
src/main/java/com/example/agent/tools/McpToolClient.java
src/main/java/com/example/agent/tools/McpToolListResponse.java
src/main/java/com/example/agent/gateway/controller/McpController.java
```

关键方法
```
ToolRegistry#listDefinitions
ModelToolResolver#applyTooling
McpToolClient#listTools
```

落地要点
- 工具列表改为摘要字段输出，避免完整定义注入上下文
- 新增按工具名称获取完整定义的能力
- 结合工具缓存复用定义结果

新增类
```
src/main/java/com/example/agent/tools/ToolCatalog.java
src/main/java/com/example/agent/tools/ToolSummary.java
```

### 五、预算分配与裁剪策略
任务编号
```
T5
```

代码点位
```
src/main/java/com/example/agent/budget/TokenBudgetManager.java
src/main/java/com/example/agent/model/ModelInvocationService.java
src/main/java/com/example/agent/runtime/AgentRuntime.java
```

关键方法
```
TokenBudgetManager#recordUsage
ModelInvocationService#invoke
```

落地要点
- 在装配阶段进行预算分段分配
- 裁剪结果写入上下文快照与事件载荷
- 裁剪与压缩触发与预算剩余额度联动

新增类
```
src/main/java/com/example/agent/budget/ContextBudgetAllocator.java
src/main/java/com/example/agent/budget/ContextPruner.java
src/main/java/com/example/agent/budget/ContextBudgetRequest.java
src/main/java/com/example/agent/budget/ContextBudgetAllocation.java
src/main/java/com/example/agent/budget/ContextPruneRequest.java
src/main/java/com/example/agent/budget/ContextPruneResult.java
```

### 六、上下文快照与事件载荷规范
任务编号
```
T6
```

代码点位
```
src/main/java/com/example/agent/domain/event/EventType.java
src/main/java/com/example/agent/domain/event/StreamEvent.java
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
src/main/java/com/example/agent/agentcore/EnforcementGateway.java
src/main/java/com/example/agent/streaming/EventStreamService.java
```

关键方法
```
AgentRuntime#run
ReactLoopService#run
EnforcementGateway#execute
```

落地要点
- 增加上下文快照与裁剪相关事件类型
- 在关键节点发布上下文快照摘要
- 事件载荷包含预算状态与工具状态

新增类
```
src/main/java/com/example/agent/streaming/ContextEventPayload.java
src/main/java/com/example/agent/streaming/ContextDelta.java
```

### 七、阶段一最小闭环验证
任务编号
```
T7
```

代码点位
```
src/test/java/com/example/agent/runtime
src/test/java/com/example/agent/model
src/test/java/com/example/agent/tools
```

落地要点
- 验证上下文快照生成与裁剪输出
- 验证三段式提示词输出与角色分离
- 验证工具摘要与按需加载流程
- 验证事件载荷包含上下文摘要

## 交叉落地点补充

### 模型调用统一接入点
代码点位
```
src/main/java/com/example/agent/model/ModelInvocationService.java
```

落地要点
- 所有模型调用统一从此处注入提示词与上下文摘要
- 统一在事件载荷中记录提示词摘要与裁剪结果

### 运行时上下文入口
代码点位
```
src/main/java/com/example/agent/runtime/AgentRuntime.java
```

落地要点
- 在运行入口构建上下文快照并注入运行时上下文
- 保留原有上下文字段，保证向后兼容

### 事件流与审计入口
代码点位
```
src/main/java/com/example/agent/streaming/EventStreamService.java
```

落地要点
- 事件流保持结构不变，仅扩展载荷字段
- 使用现有序列与回放机制承载快照信息

## 说明
本清单仅覆盖阶段一落地范围，不包含记忆过期与脱敏等阶段二能力。