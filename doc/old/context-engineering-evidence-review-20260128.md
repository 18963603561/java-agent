# 智能体上下文工程证据补齐复核报告

## 复核范围
复核输入文档
```
vendor/ai-agent-book/zh/Part3-上下文与记忆/第07章：上下文窗口管理.md
vendor/ai-agent-book/zh/Part3-上下文与记忆/第08章：记忆架构.md
vendor/ai-agent-book/zh/Part3-上下文与记忆/第09章：多轮对话设计.md
```

对照源码
```
vendor/Shannon/go/orchestrator/internal/activities/context_compress.go
vendor/Shannon/go/orchestrator/internal/activities/semantic_memory.go
vendor/Shannon/go/orchestrator/internal/activities/semantic_memory_chunked.go
vendor/Shannon/go/orchestrator/internal/budget/manager.go
vendor/Shannon/go/orchestrator/internal/session/manager.go
vendor/Shannon/protos/llm/llm.proto
vendor/Shannon/protos/agent/agent.proto
vendor/Shannon/python/llm-service/llm_service/api/tools.py
vendor/Shannon/go/orchestrator/internal/activities/agent.go
```

项目代码
```
src/main/java/com/example/agent/auth/TenantContext.java
src/main/java/com/example/agent/common/TaskRequest.java
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
src/main/java/com/example/agent/runtime/ObservationWindowBuffer.java
src/main/java/com/example/agent/memory/MemoryStore.java
src/main/java/com/example/agent/memory/MemoryRecallService.java
src/main/java/com/example/agent/memory/MemoryWriteService.java
src/main/java/com/example/agent/memory/MemoryPolicy.java
src/main/java/com/example/agent/memory/CompressedMemoryStore.java
src/main/java/com/example/agent/budget/TokenBudgetManager.java
src/main/java/com/example/agent/model/DefaultModelProvider.java
src/main/java/com/example/agent/model/ModelToolResolver.java
src/main/java/com/example/agent/streaming/EventStreamService.java
src/main/java/com/example/agent/domain/event/EventType.java
```

## 复核标准
1. 上下文分层：运行时元信息、角色与边界、任务意图与成功标准、工作记忆、领域知识、长期记忆
2. 上下文装配：按需拼装、检索优先级、预算分配、裁剪与压缩
3. 三段式提示词：系统策略、开发者策略、用户问题与动态上下文
4. 记忆策略：写入门槛、过期机制、来源可追溯
5. 工具与资源上下文减负：工具列表摘要化、按需加载定义
6. 压缩策略：会话摘要、工作记忆、证据包
7. 编排模板与事件流：上下文快照、步骤事件、工具状态与流式追踪
8. 最小落地清单：上下文构建器、记忆策略、工具目录、压缩器、预算管理、可观测性

## 总体结论
- 结论：部分满足
- 已具备：记忆召回与压缩、预算监控、事件流、观察窗口
- 主要缺口：统一分层模型、上下文构建器、三段式提示词、工具摘要与按需加载、证据包、记忆过期与可追溯策略

## 逐条复核

### 一、上下文分层
结论
部分满足

依据（文档）
```
vendor/ai-agent-book/zh/Part3-上下文与记忆/第08章：记忆架构.md
vendor/ai-agent-book/zh/Part3-上下文与记忆/第07章：上下文窗口管理.md
```

说明
文档强调工作记忆、会话记忆、长期记忆、语义记忆等分层，并突出上下文窗口管理。

对照源码
```
vendor/Shannon/go/orchestrator/internal/activities/semantic_memory.go
vendor/Shannon/go/orchestrator/internal/session/manager.go
```

说明
源码包含最近、语义与摘要分层召回，并在会话结构中保留历史与租户信息。

项目证据
```
src/main/java/com/example/agent/auth/TenantContext.java
src/main/java/com/example/agent/common/TaskRequest.java
src/main/java/com/example/agent/memory/MemoryRecord.java
src/main/java/com/example/agent/memory/MemoryStore.java
src/main/java/com/example/agent/memory/MemoryRecallService.java
```

说明
当前实现提供运行时元信息与记忆分层字段，并能将记忆摘要注入上下文。

差距
- 缺少统一的六层上下文结构与字段规范
- 角色边界、任务成功标准、领域知识层未结构化
- 工作记忆与长期记忆的边界与写入规则不清晰

改动点
- 补充分层上下文模型与转换规则
- 明确任务成功标准与角色边界字段并纳入上下文
- 领域知识层改为资源检索并引入引用

### 二、上下文装配
结论
部分满足

依据（文档）
```
vendor/ai-agent-book/zh/Part3-上下文与记忆/第07章：上下文窗口管理.md
vendor/ai-agent-book/zh/Part3-上下文与记忆/第08章：记忆架构.md
```

说明
文档强调预算约束下的压缩与分层检索，要求在上下文窗口内进行取舍。

对照源码
```
vendor/Shannon/go/orchestrator/internal/activities/context_compress.go
vendor/Shannon/go/orchestrator/internal/activities/semantic_memory.go
vendor/Shannon/go/orchestrator/internal/budget/manager.go
```

说明
源码提供压缩、分层检索与预算检查能力。

项目证据
```
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/memory/MemoryRecallService.java
src/main/java/com/example/agent/memory/MemoryPolicy.java
src/main/java/com/example/agent/budget/TokenBudgetManager.java
```

说明
运行时将记忆摘要注入上下文，具备压缩触发与预算统计。

差距
- 缺少统一装配流程与可配置拼装模板
- 缺少预算分配与裁剪策略
- 检索优先级与裁剪规则未固化

改动点
- 新增统一上下文构建器与装配模板
- 引入预算分配与裁剪策略配置
- 固化检索优先级与裁剪顺序

### 三、三段式提示词
结论
不满足

依据（文档）
```
vendor/ai-agent-book/zh/Part3-上下文与记忆/第07章：上下文窗口管理.md
```

说明
文档提到系统提示词占用上下文窗口，提示需要将固定策略与动态内容分离。

对照源码
```
vendor/Shannon/protos/llm/llm.proto
```

说明
源码定义多角色消息结构，支持系统与用户分离。

项目证据
```
src/main/java/com/example/agent/model/DefaultModelProvider.java
```

说明
当前请求构建仅使用单一用户消息，缺少系统与开发者提示词的分离。

差距
- 提示词结构单一
- 固定策略与动态事实混杂
- 缺少统一模板与裁剪规则

改动点
- 引入多角色消息结构
- 拆分系统策略、开发者策略与用户输入
- 统一模板并增加裁剪策略

### 四、记忆策略
结论
部分满足

依据（文档）
```
vendor/ai-agent-book/zh/Part3-上下文与记忆/第08章：记忆架构.md
vendor/ai-agent-book/zh/Part3-上下文与记忆/第09章：多轮对话设计.md
```

说明
文档强调记忆类型、保留期限与敏感信息处理。

对照源码
```
vendor/Shannon/go/orchestrator/internal/activities/context_compress.go
vendor/Shannon/go/orchestrator/internal/session/manager.go
```

说明
源码在压缩与会话层实现敏感信息处理与过期控制。

项目证据
```
src/main/java/com/example/agent/memory/MemoryWriteService.java
src/main/java/com/example/agent/memory/MemoryPolicy.java
src/main/java/com/example/agent/memory/MemoryRecord.java
src/main/java/com/example/agent/memory/JdbcMemoryRepository.java
```

说明
当前实现具备写入开关与自动压缩，记录包含租户与任务标识。

差距
- 缺少记忆过期与清理机制
- 缺少敏感信息脱敏
- 缺少来源与置信度等可追溯字段
- 写入门槛较粗，缺少低价值过滤规则

改动点
- 增加记忆过期策略与清理任务
- 写入与压缩前增加敏感信息脱敏
- 扩展记忆记录字段支持来源与置信度
- 增加写入门槛与低价值过滤规则

### 五、工具与资源上下文减负
结论
不满足

依据（文档）
文档未直接覆盖该项。

对照源码
```
vendor/Shannon/python/llm-service/llm_service/api/tools.py
vendor/Shannon/go/orchestrator/internal/activities/agent.go
vendor/Shannon/protos/llm/llm.proto
```

说明
源码支持工具列表与按需获取定义，并可向模型传递工具定义结构。

项目证据
```
src/main/java/com/example/agent/agentcore/ToolRegistry.java
src/main/java/com/example/agent/model/ModelToolResolver.java
src/main/java/com/example/agent/tools/McpToolClient.java
```

说明
当前工具定义直接进入模型请求，虽然支持按技能过滤，但缺少摘要化列表与按需加载定义。

差距
- 缺少工具摘要化列表
- 缺少按工具名称获取定义的接口
- 工具定义未按需加载，导致上下文膨胀

改动点
- 工具列表返回摘要化信息
- 新增按工具名称获取定义的接口
- 模型侧按需加载定义并建立缓存

### 六、压缩策略
结论
部分满足

依据（文档）
```
vendor/ai-agent-book/zh/Part3-上下文与记忆/第07章：上下文窗口管理.md
```

说明
文档提出滑动窗口与摘要压缩，并强调压缩的取舍。

对照源码
```
vendor/Shannon/go/orchestrator/internal/activities/context_compress.go
vendor/Shannon/go/orchestrator/internal/activities/semantic_memory.go
vendor/Shannon/go/orchestrator/internal/activities/semantic_memory_chunked.go
```

说明
源码提供摘要压缩、摘要检索与多样性去重。

项目证据
```
src/main/java/com/example/agent/memory/MemoryPolicy.java
src/main/java/com/example/agent/memory/CompressedMemoryStore.java
src/main/java/com/example/agent/memory/MemoryRecallService.java
src/main/java/com/example/agent/runtime/ObservationWindowBuffer.java
```

说明
当前实现具备自动压缩与观察窗口，但压缩摘要为拼接，缺少结构化产物。

差距
- 摘要生成缺少质量控制与脱敏
- 工作记忆与证据包未形成结构化产物
- 压缩触发未与预算联动

改动点
- 引入结构化会话摘要与工作记忆
- 生成证据包并保留来源
- 将压缩触发与预算联动

### 七、编排模板与事件流
结论
部分满足

依据（文档）
```
vendor/ai-agent-book/zh/Part3-上下文与记忆/第09章：多轮对话设计.md
```

说明
文档强调会话连续性、隐私与状态管理。

对照源码
```
vendor/Shannon/protos/agent/agent.proto
```

说明
源码提供会话上下文字段，包含历史、工具与成本信息。

项目证据
```
src/main/java/com/example/agent/streaming/EventStreamService.java
src/main/java/com/example/agent/domain/event/EventType.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
src/main/java/com/example/agent/runtime/AgentRuntime.java
```

说明
当前具备事件流与思考、行动、观察事件，但缺少统一上下文快照与工作记忆更新。

差距
- 上下文快照结构缺失
- 事件载荷缺少统一字段规范
- 工具状态与检索引用未统一跟踪

改动点
- 新增上下文快照结构并在关键节点更新
- 统一事件载荷字段与序列化规范
- 将工具状态与检索引用写入事件流

### 八、最小落地清单
结论
部分满足

项目证据
```
src/main/java/com/example/agent/memory/MemoryPolicy.java
src/main/java/com/example/agent/memory/CompressedMemoryStore.java
src/main/java/com/example/agent/budget/TokenBudgetManager.java
src/main/java/com/example/agent/streaming/EventStreamService.java
```

说明
已具备记忆策略、压缩器、预算管理与可观测性，但缺少上下文构建器与工具目录分层。

差距
- 缺少上下文构建器
- 缺少工具目录分层与按需加载
- 缺少证据包与工作记忆产物

改动点
- 补齐上下文构建器与装配模板
- 增加工具目录分层与按需加载
- 增加证据包与工作记忆产物

## 改动点清单
- 新增分层上下文模型与上下文构建器
- 建立三段式提示词模板并支持多角色消息
- 增加记忆过期清理与敏感信息脱敏
- 记录记忆来源与置信度并设定写入门槛
- 工具列表摘要化与按需加载定义接口
- 生成结构化会话摘要、工作记忆与证据包
- 建立上下文快照并统一事件载荷规范
- 预算驱动的裁剪与压缩策略