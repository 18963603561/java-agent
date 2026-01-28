# 生产架构覆盖性分析报告
日期
2026-01-27

## 范围与依据
分析对象
`F:\ai-code\java-agent`

参考文档
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part7-生产架构\README.md`
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part7-生产架构\第20章：三层架构设计.md`
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part7-生产架构\第21章：Temporal工作流.md`
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part7-生产架构\第22章：可观测性.md`

检索范围
`src/main/java`
`src/test/java`

检索关键词
```text
temporal
workflow
activity
worker
getversion
replay
signal
heartbeat
orchestrator
gateway
agentcore
metrics
micrometer
otel
opentelemetry
trace
tracing
prometheus
health
actuator
liveness
readiness
alert
grafana
```

约束
只读分析
未修改代码
未执行测试

## 总体结论
三层架构
部分满足

工作流引擎与确定性重放
不满足

可观测性体系
部分满足

## 第 20 章 三层架构设计
结论
部分满足

满足点
- 逻辑编排层与执行入口分离
证据路径
`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
`src/main/java/com/example/agent/orchestrator/WorkflowRouter.java`
`src/main/java/com/example/agent/runtime/AgentRuntime.java`
证据方法
`submitTask`
`route`
`run`

- 工具执行与安全校验链路存在
证据路径
`src/main/java/com/example/agent/agentcore/EnforcementGateway.java`
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
`src/main/java/com/example/agent/agentcore/SandboxExecutor.java`
`src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java`

- 模型调用与路由能力存在
证据路径
`src/main/java/com/example/agent/model/DefaultLlmClient.java`
`src/main/java/com/example/agent/model/DefaultModelProvider.java`
`src/main/java/com/example/agent/model/ModelInvocationService.java`
`src/main/java/com/example/agent/model/ModelRouter.java`

- 对外接口层存在
证据路径
`src/main/java/com/example/agent/gateway/controller/TaskController.java`
`src/main/java/com/example/agent/gateway/controller/ReplayController.java`

不足
- 物理分层与多服务拆分未体现
证据说明
调用链为进程内直接调用
调用对象
`com.example.agent.runtime.AgentRuntime`

- 沙箱仅为策略校验
证据路径
`src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java`
证据说明
仅基于黑名单拦截并返回模拟输出

- 层间通信未体现跨服务协议
证据路径
`src/main/java/com/example/agent/orchestrator/WorkflowRouter.java`
证据说明
未发现跨服务接口定义

## 第 21 章 工作流引擎与持久化执行
结论
不满足

已有能力
- 事件日志与步骤记录可用于回放
证据路径
`src/main/java/com/example/agent/history/EventLogRepository.java`
`src/main/java/com/example/agent/runtime/StepRuntimeService.java`
`src/main/java/com/example/agent/governance/ReplayService.java`

- 运行时暂停与审批控制存在
证据路径
`src/main/java/com/example/agent/runtime/ExecutionControlService.java`
`src/main/java/com/example/agent/gateway/controller/ApprovalController.java`

主要缺口
- 未发现工作流引擎依赖与实现
证据路径
`pom.xml`
检索结果
```text
temporal
workflow
activity
worker
```

- 回放为事件重放而非确定性重放
证据路径
`src/main/java/com/example/agent/governance/ReplayService.java`
证据说明
仅重新发布历史事件与步骤记录

- 执行控制状态为内存存储
证据路径
`src/main/java/com/example/agent/runtime/ExecutionControlService.java`
证据说明
注释中说明为内存缓存

## 第 22 章 可观测性
结论
部分满足

指标
- 统一指标发布器存在
证据路径
`src/main/java/com/example/agent/observability/MetricsPublisher.java`
证据字段
`MeterRegistry`

- 关键路径指标记录存在
证据路径
`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
`src/main/java/com/example/agent/runtime/StepRuntimeService.java`
`src/main/java/com/example/agent/streaming/EventStreamService.java`

- 指标端点配置存在
证据路径
`src/main/resources/application.yml`
证据片段
```text
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

追踪
- 链路标识读取与传播存在
证据路径
`src/main/java/com/example/agent/observability/TracingPublisher.java`
`src/main/java/com/example/agent/auth/TenantResolver.java`
`src/main/java/com/example/agent/auth/TenantContextFilter.java`
证据字段
`X-Trace-Id`
`traceId`

日志
- 关键路径日志覆盖存在
证据路径
`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
`src/main/java/com/example/agent/model/ModelInvocationService.java`
`src/main/java/com/example/agent/streaming/SseStreamController.java`

健康检查
- 健康端点配置存在
证据路径
`src/main/resources/application.yml`
`src/main/java/com/example/agent/auth/TenantProperties.java`

缺口与风险
Prometheus
未发现注册表依赖与导出端点

Grafana
未发现仪表盘配置

Alertmanager
未发现告警规则配置

- 追踪采样策略为全量
证据路径
`src/main/resources/application.yml`
证据片段
```text
management:
  tracing:
    sampling:
      probability: 1.0
```

- 指标标签可能产生高基数风险
证据路径
`src/main/java/com/example/agent/observability/MetricsPublisher.java`
证据字段
`traceId`

## 需要运行验证
- 指标端点暴露与采集链路是否可用
证据路径
`src/main/resources/application.yml`

- 追踪导出是否配置为实际后端
证据路径
`pom.xml`
`src/main/resources/application.yml`