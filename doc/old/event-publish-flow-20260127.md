# 发布事件流程分析报告

## 依据文件
```
src/main/java/com/example/agent/domain/event/StreamEvent.java
src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java
src/main/java/com/example/agent/runtime/AgentRuntime.java
src/main/java/com/example/agent/runtime/StepRuntimeService.java
src/main/java/com/example/agent/model/ModelInvocationService.java
src/main/java/com/example/agent/agentcore/EnforcementGateway.java
src/main/java/com/example/agent/tools/hook/HookManager.java
src/main/java/com/example/agent/gateway/controller/McpController.java
src/main/java/com/example/agent/gateway/controller/ApprovalController.java
src/main/java/com/example/agent/budget/TokenBudgetManager.java
src/main/java/com/example/agent/research/ResearchPipeline.java
src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java
src/main/java/com/example/agent/reasoning/DebateCoordinator.java
src/main/java/com/example/agent/scheduler/ScheduleEngine.java
src/main/java/com/example/agent/governance/ReplayService.java
src/main/java/com/example/agent/streaming/EventStreamService.java
src/main/java/com/example/agent/streaming/SseStreamController.java
src/main/java/com/example/agent/history/EventLogService.java
src/main/java/com/example/agent/history/EventLogRepository.java
src/main/java/com/example/agent/history/InMemoryEventLogRepository.java
src/main/java/com/example/agent/history/JdbcEventLogRepository.java
```
用于确认事件发布、分发、订阅与持久化链路。

## 流程概览
- 业务组件构建事件并发布到内部总线
- 总线同步分发给事件流监听与事件日志监听
- 事件流监听写入流式存储并推送在线订阅
- 事件日志监听进行幂等持久化
- 回放服务可基于历史记录重新生成事件流

## 发布者
### 直接发布者
```
TaskOrchestrator
AgentRuntime
StepRuntimeService
ModelInvocationService
EnforcementGateway
HookManager
McpController
ApprovalController
TokenBudgetManager
ResearchPipeline
MultiAgentCoordinator
DebateCoordinator
ScheduleEngine
ReplayService
```
这些组件直接调用事件发布器并构建事件对象。

### 间接触发
```
PlannerService
ReflectionService
```
这些组件通过模型调用入口间接触发提示与输出事件。

## 进入总线
```
ApplicationEventPublisher
StreamEvent
```
事件通过应用事件发布器进入总线。
事件载体统一为事件对象类型。
事件标识由工作流标识与序列号拼接生成。

```
EventStreamService
```
事件流服务维护序列号并用于生成事件标识。

## 接收者
```
EventStreamService
```
负责索引维护、流式存储写入与实时推送。

```
EventLogService
```
负责幂等持久化与查询。

```
EventType.LLM_PARTIAL
```
该类型被日志持久化过滤。

## 订阅链路
```
SseStreamController
EventStreamService.stream
/api/v1/stream/sse
```
订阅接口构造流式响应，支持类型过滤与断线续传。
首事件超时会生成超时事件用于游标推进。

```
last_event_id
Last-Event-ID
recordSyntheticEvent
```
断线续传游标从查询参数或请求头读取。
超时事件写入流式存储与索引，但不进入实时推送。

## 线程模型
- 事件发布与监听同步执行，运行在调用发布方法的线程
- 流式存储写入采用后台调度器执行
- 游标校验采用后台调度器执行
- 首事件超时采用并行调度器执行
- 订阅响应运行在响应式链路线程

```
Schedulers.boundedElastic
Schedulers.parallel
```

## 不丢保障
- 事件标识与序列号用于排序与去重
- 事件日志持久化使用幂等保存接口
- 流式存储保留一定窗口，断线续传按游标回放
- 游标校验发现缺口会报错，避免静默丢失
- 实时推送使用内存缓冲应对订阅端背压
- 回放服务可重建历史事件流

```
saveIfAbsent
MAX_STREAM_SIZE = 256
STREAM_TTL = 24h
STREAM_GAP
Sinks.many().multicast().onBackpressureBuffer()
ReplayService
```

## 不丢边界
- 流式存储窗口有限，超出窗口会产生缺口错误
- 流式写入为异步执行，写入失败仅记录日志
- 实时推送发布结果未检查，极端情况下可能丢失推送
- 部分事件类型不进入日志持久化
- 存储不可用时断线续传不可用

```
tryEmitNext
```