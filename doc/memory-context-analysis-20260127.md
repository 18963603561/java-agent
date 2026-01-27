# 记忆提取与上下文推理现状分析（20260127）

## 结论
- 目前任务自然语言输入不会自动触发记忆提取，也不会自动注入上下文推理。
- 记忆检索仅在调用记忆接口时发生，任务流程未调用该接口。
- 默认配置下向量检索关闭，语义检索不可用，仅有基于文本匹配的检索通道。

## 现状与证据

### 任务入口链路未接入记忆检索
证据路径：
`src/main/java/com/example/agent/gateway/controller/TaskController.java`
`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
`src/main/java/com/example/agent/orchestrator/WorkflowRouter.java`
`src/main/java/com/example/agent/runtime/AgentRuntime.java`
`src/main/java/com/example/agent/planning/PlannerService.java`

说明：
任务请求只在上下文中透传，并进入规划与运行流程，未出现记忆检索或记忆注入的调用。

### 记忆接口存在但为显式调用
证据路径：
`src/main/java/com/example/agent/gateway/controller/MemoryController.java`
`src/main/java/com/example/agent/memory/MemoryStore.java`

说明：
记忆保存、检索、压缩通过独立接口触发，未在任务提交或规划阶段自动调用。

接口路径：
`/api/v1/memory/save`
`/api/v1/memory/search`
`/api/v1/memory/compress`

### 记忆检索触发条件
证据路径：
`src/main/java/com/example/agent/memory/MemoryStore.java`
`src/main/java/com/example/agent/memory/MemoryQuery.java`

条件要点：
- 必须调用记忆检索接口，并提供非空查询文本。
- 检索使用会话维度过滤，缺少会话标识时结果通常为空。
- 返回结果聚合三类：语义记忆、近期记忆、压缩记忆。

### 语义检索可用条件
证据路径：
`src/main/java/com/example/agent/memory/SemanticMemoryStore.java`
`src/main/java/com/example/agent/memory/QdrantVectorStore.java`
`src/main/java/com/example/agent/memory/MemoryVectorProperties.java`
`src/main/resources/application.yml`

条件要点：
- 向量存储组件仅在配置开启时创建。
- 当前默认配置关闭向量检索。

配置键与默认值：
`agent.memory.vector.enabled=false`

### 自动压缩触发条件（非检索）
证据路径：
`src/main/java/com/example/agent/memory/MemoryStore.java`
`src/main/java/com/example/agent/memory/MemoryPolicy.java`
`src/main/java/com/example/agent/memory/MemoryPolicyProperties.java`

条件要点：
- 仅在记忆保存或检索后判断是否需要压缩。
- 触发条件包含数量阈值、估算令牌阈值、最大存活时间等。

配置键：
`agent.memory.policy.enabled`
`agent.memory.policy.size-threshold`
`agent.memory.policy.token-threshold`
`agent.memory.policy.max-age-seconds`
`agent.memory.policy.min-compress-interval-seconds`

### 存储模式与持久化
证据路径：
`src/main/resources/application.yml`
`src/main/java/com/example/agent/memory/InMemoryMemoryRepository.java`
`src/main/java/com/example/agent/memory/JdbcMemoryRepository.java`

说明：
默认存储模式为内存，未启用持久化时重启即丢失。

## 功能确认
当前工程具备记忆存取模块与接口，但未实现“自然语言输入后自动提取记忆并参与上下文推理”的功能。

## 实现方案（增量接入）

### 目标
在不破坏现有流程的前提下，为任务输入引入自动记忆检索与上下文注入，并提供可控的开关与阈值。

### 方案要点
1. 接入点
- 在规划阶段前增加记忆检索并写入运行上下文。
- 入口建议在任务运行前或规划服务中完成，保证后续步骤可使用记忆。

2. 新增服务
- 新增记忆召回服务，统一封装检索、过滤、裁剪与摘要。
- 新增记忆写入服务，在任务结束或关键步骤完成时落盘记忆。

3. 触发条件
- 必须存在会话标识并开启记忆召回开关。
- 查询文本达到最小长度，或显式指定强制召回。
- 允许通过请求上下文字段覆盖默认策略。

4. 上下文注入方式
- 将检索结果以结构化字段写入上下文。
- 规划提示中追加记忆摘要，保证模型可见。

5. 记忆保存策略
- 保存用户输入、关键工具输出、最终结论。
- 对长文本进行摘要或分块，避免噪声与膨胀。

6. 日志与指标
- 记录召回开始、命中数量、耗时与失败原因。
- 增加记忆召回与写入的计数与延迟指标。

7. 兼容与降级
- 向量检索不可用时自动降级到文本检索。
- 召回失败不影响主流程，只记录告警。

### 可能的落地调整点
- 在任务运行入口增加记忆召回并合并上下文。
- 在规划提示构建处注入记忆摘要。
- 在任务结束处写入记忆记录并触发压缩。