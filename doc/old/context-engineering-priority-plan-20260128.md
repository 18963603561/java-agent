# 上下文工程改动点优先级与落地范围方案

## 输入依据
输入文档
```
doc/context-engineering-evidence-review-20260128.md
doc/ai-agent-book-part1-coverage-20260127.md
```
说明
以上文档为本次优先级与组件范围确认的唯一依据。

## 目标
- 确认改动点优先级
- 明确先落地组件范围与边界
- 给出分阶段落地方案与验收要点

## 约束
- 不破坏既有主流程
- 新能力以扩展方式引入
- 保持多租户隔离
- 保持日志与可观测性一致性

涉及主流程模块
```
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
```
说明
以上模块仅允许扩展，不做破坏性改造。

## 改动点优先级

### 一级优先
改动点
- 统一分层上下文模型与上下文构建器
- 三段式提示词模板与多角色消息结构
- 工具目录摘要化与按需加载定义
- 预算驱动的裁剪与压缩策略
- 上下文快照与事件载荷规范

原因
- 直接决定上下文装配的稳定性与成本控制
- 为记忆策略与结构化压缩提供统一承载结构
- 可在不破坏主流程的前提下完成最小闭环

### 二级优先
改动点
- 记忆过期清理与敏感信息脱敏
- 记忆来源与置信度字段完善
- 写入门槛与低价值过滤规则
- 结构化会话摘要、工作记忆与证据包
- 压缩触发与预算联动细化

原因
- 依赖一级优先的上下文结构与装配模板
- 需要在已有运行时采样与记忆流程上扩展

### 三级优先
改动点
- 工具定义缓存与结果引用复用
- 检索优先级与裁剪规则细化
- 事件载荷的跨链路对齐与审计增强

原因
- 属于优化项，对第一阶段闭环不是硬性前置

## 先落地组件范围

### 组件一：分层上下文模型与构建器
核心职责
- 定义六层上下文结构与字段规范
- 负责上下文装配、裁剪与输出
- 提供可复用的装配模板

范围边界
- 不改变既有业务流程控制
- 不包含记忆过期与脱敏

涉及模块
```
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/runtime/ReactLoopService.java
src/main/java/com/example/agent/common/TaskRequest.java
```

建议新增模块
```
src/main/java/com/example/agent/context/ContextSnapshot.java
src/main/java/com/example/agent/context/ContextBuilder.java
src/main/java/com/example/agent/context/ContextAssembler.java
```

### 组件二：三段式提示词模板与多角色消息
核心职责
- 固定系统策略与开发者策略
- 将用户输入与动态上下文分离
- 支持多角色消息结构

提示词结构
```
System
Developer
User + Dynamic Context
```
说明
以上结构用于稳定策略与动态事实分离。

范围边界
- 不改变模型路由策略
- 不改变既有模型供应商切换逻辑

涉及模块
```
src/main/java/com/example/agent/model/DefaultModelProvider.java
```

建议新增模块
```
src/main/java/com/example/agent/model/PromptTemplate.java
src/main/java/com/example/agent/model/PromptAssembler.java
```

### 组件三：工具目录摘要化与按需加载
核心职责
- 工具列表返回摘要信息
- 按工具名称获取定义
- 工具定义按需加载并缓存

范围边界
- 不调整工具执行权限判定

涉及模块
```
src/main/java/com/example/agent/agentcore/ToolRegistry.java
src/main/java/com/example/agent/model/ModelToolResolver.java
src/main/java/com/example/agent/tools/McpToolClient.java
```

建议新增模块
```
src/main/java/com/example/agent/tools/ToolCatalog.java
src/main/java/com/example/agent/tools/ToolSummary.java
```

### 组件四：预算驱动裁剪与压缩
核心职责
- 进行上下文预算分配
- 触发裁剪与压缩
- 输出裁剪结果摘要

范围边界
- 不调整预算统计口径

涉及模块
```
src/main/java/com/example/agent/budget/TokenBudgetManager.java
```

建议新增模块
```
src/main/java/com/example/agent/budget/ContextBudgetAllocator.java
src/main/java/com/example/agent/budget/ContextPruner.java
```

### 组件五：上下文快照与事件载荷规范
核心职责
- 统一上下文快照结构
- 记录关键步骤的上下文变化
- 输出可审计的事件载荷

范围边界
- 不改变事件流通道与传输方式

涉及模块
```
src/main/java/com/example/agent/streaming/EventStreamService.java
src/main/java/com/example/agent/domain/event/EventType.java
```

建议新增模块
```
src/main/java/com/example/agent/streaming/ContextEventPayload.java
```

## 落地方案

### 阶段一：基础上下文装配闭环
工作内容
- 建立分层上下文模型与构建器
- 引入三段式提示词模板
- 工具目录摘要化与按需加载
- 预算驱动裁剪与压缩策略
- 上下文快照与事件载荷规范

验收要点
- 上下文结构包含六层字段并可复用
- 提示词分段输出稳定，策略与事实分离
- 工具列表不再完整注入上下文
- 裁剪触发可控且记录裁剪结果摘要
- 事件流可回放上下文快照

### 阶段二：记忆策略与结构化压缩
工作内容
- 记忆过期清理与敏感信息脱敏
- 记忆来源与置信度字段补齐
- 写入门槛与低价值过滤规则
- 结构化会话摘要、工作记忆与证据包

验收要点
- 记忆数据可追溯且具备过期清理
- 压缩产物结构化并可复用

### 阶段三：优化与治理增强
工作内容
- 工具定义缓存与结果引用复用
- 检索优先级与裁剪规则细化
- 事件载荷跨链路对齐与审计增强

验收要点
- 上下文体积可预测且波动可控
- 关键事件可回放与审计

## 建议的下一步
- 确认优先级划分与第一阶段组件范围
- 确认是否进入阶段一实现与任务拆分
- 如需同步需求与计划文档，请明确调整范围