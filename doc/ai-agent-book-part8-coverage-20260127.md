# 企业级特性覆盖性分析报告
日期
2026-01-27

## 范围与依据
分析对象
`F:\ai-code\java-agent`

参考文档
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part8-企业级特性\README.md`
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part8-企业级特性\第23章：Token预算控制.md`
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part8-企业级特性\第24章：策略治理.md`
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part8-企业级特性\第25章：安全执行.md`
`F:\ai-code\java-agent\vendor\ai-agent-book\zh\Part8-企业级特性\第26章：多租户设计.md`

检索范围
`src/main/java`
`src/test/java`

检索关键词
```text
budget
token
cost
policy
opa
sandbox
wasi
tenant
multitenant
auth
quota
apikey
```

约束
只读分析
未修改代码
未执行测试

## 总体结论
令牌预算控制
部分满足

策略治理
部分满足

安全执行
部分满足

多租户设计
部分满足

## 第 23 章 令牌预算控制
结论
部分满足

满足点
- 预算计量记录与汇总能力存在
证据路径
`src/main/java/com/example/agent/budget/TokenBudgetManager.java`
`src/main/java/com/example/agent/budget/TokenUsageRepository.java`
`src/main/java/com/example/agent/gateway/controller/BudgetController.java`

- 预算记录支持幂等写入
证据路径
`src/main/java/com/example/agent/budget/JdbcTokenUsageRepository.java`
`src/main/java/com/example/agent/budget/InMemoryTokenUsageRepository.java`
证据说明
基于 `usage_id` 与租户键去重

- 成本计算按输入与输出拆分计价
证据路径
`src/main/java/com/example/agent/budget/CostCalculator.java`
`src/main/java/com/example/agent/model/ModelDefinition.java`

- 预算阈值触发事件与模型降级
证据路径
`src/main/java/com/example/agent/budget/TokenBudgetManager.java`
`src/main/java/com/example/agent/model/ModelFallbackPolicy.java`
`src/main/java/com/example/agent/domain/event/EventType.java`

不足
- 未实现任务、会话、智能体三级预算
证据路径
`src/main/java/com/example/agent/budget/TokenUsageRepository.java`
`src/main/java/com/example/agent/budget/TokenBudgetManager.java`
`src/main/java/com/example/agent/multiagent/AgentProfile.java`
证据说明
仅按任务汇总，未见会话级与智能体级预算逻辑，配置字段未被使用

- 缺少硬限制、软限制与审批模式
证据路径
`src/main/java/com/example/agent/budget/TokenBudgetManager.java`
证据说明
超阈值仅触发事件与降级，不阻断执行

- 缺少预算背压与预算级熔断
证据路径
`src/main/java/com/example/agent/budget/TokenBudgetManager.java`
`src/main/java/com/example/agent/governance/RateLimitService.java`
`src/main/java/com/example/agent/governance/CircuitBreakerManager.java`
证据说明
限流与熔断未与预算控制联动

- 预算估算使用近似值
证据路径
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
证据说明
以字符串长度估算输入与输出，缺少真实令牌统计

## 第 24 章 策略治理
结论
部分满足

满足点
- 提供策略评估入口与审计日志
证据路径
`src/main/java/com/example/agent/gateway/controller/PolicyController.java`
`src/main/java/com/example/agent/policy/PolicyEngine.java`

- 策略评估具备基础拒绝逻辑与指标记录
证据路径
`src/main/java/com/example/agent/policy/PolicyEngine.java`
`src/main/java/com/example/agent/observability/MetricsPublisher.java`

- 工具级拦截与放行机制存在
证据路径
`src/main/java/com/example/agent/tools/hook/HookManager.java`
`src/main/java/com/example/agent/tools/hook/BlockedToolHookHandler.java`

不足
- 未集成开放策略代理或等价策略引擎
证据路径
`src/main/java/com/example/agent/policy/PolicyEngine.java`
证据说明
策略逻辑硬编码，未见外部策略文件与编译执行流程

- 缺少策略热更新、灰度模式与决策缓存
证据路径
`src/main/java/com/example/agent/policy/PolicyEngine.java`
证据说明
未见模式切换、缓存与策略版本标识

- 缺少策略版本审计与拒绝原因聚合指标
证据路径
`src/main/java/com/example/agent/policy/PolicyDecision.java`
`src/main/java/com/example/agent/observability/MetricsPublisher.java`

## 第 25 章 安全执行
结论
部分满足

满足点
- 工具执行前置校验与阻断机制存在
证据路径
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
`src/main/java/com/example/agent/agentcore/SandboxExecutor.java`
`src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java`

- 工具参数校验与主机白名单存在
证据路径
`src/main/java/com/example/agent/agentcore/ToolArgumentValidator.java`
`src/main/java/com/example/agent/tools/McpToolClient.java`

不足
- 未实现真正的沙箱隔离能力
证据路径
`src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java`
证据说明
仅基于阻断列表校验，不具备文件、网络、资源隔离

- 未见资源限制与执行超时配置
证据路径
`src/main/java/com/example/agent/sandbox/SandboxProperties.java`
证据说明
配置仅包含启用开关与阻断列表

- 未见沙箱执行审计与安全测试用例
证据路径
`src/test/java`
证据说明
未发现针对沙箱隔离的测试

## 第 26 章 多租户设计
结论
部分满足

满足点
- 租户上下文与过滤器存在
证据路径
`src/main/java/com/example/agent/auth/TenantResolver.java`
`src/main/java/com/example/agent/auth/TenantContextFilter.java`
`src/main/java/com/example/agent/auth/TenantContext.java`

- 鉴权支持租户范围校验
证据路径
`src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java`

- 数据访问层按租户隔离
证据路径
`src/main/java/com/example/agent/orchestrator/JdbcTaskRepository.java`
`src/main/java/com/example/agent/memory/JdbcMemoryRepository.java`
`src/main/java/com/example/agent/runtime/JdbcStepRecordRepository.java`
`src/main/java/com/example/agent/history/JdbcEventLogRepository.java`
`src/main/java/com/example/agent/budget/JdbcTokenUsageRepository.java`

- 向量存储查询带租户过滤
证据路径
`src/main/java/com/example/agent/memory/QdrantVectorStore.java`

- 事件流按租户过滤输出
证据路径
`src/main/java/com/example/agent/streaming/EventStreamService.java`

不足
- 租户标识来源于请求头
证据路径
`src/main/java/com/example/agent/auth/TenantResolver.java`
证据说明
租户标识未从鉴权结果绑定，仅做范围校验

- 缺少租户计划与配额管理能力
证据路径
`src/main/java/com/example/agent/auth`
`src/main/java/com/example/agent/budget/TokenBudgetManager.java`
证据说明
未见租户实体与计划配置

- 工具缓存未按租户隔离
证据路径
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
`src/main/java/com/example/agent/agentcore/ToolCache.java`
证据说明
缓存键不包含租户信息，可能产生跨租户复用