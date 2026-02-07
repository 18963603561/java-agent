# Shannon 对齐整改任务清单 - 20260127

## 依据
- 复核报告：`doc/shannon-alignment-verification-20260127-v2.md`
- 规格：`specs/001-agent-core-spec/spec.md`
- 合同：`specs/001-agent-core-spec/contracts/openapi.yaml`
- 快速验证：`specs/001-agent-core-spec/quickstart.md`
- 验收清单：`specs/001-agent-core-spec/checklists/checklist.md`

## 任务总览
| 任务编号 | 优先级 | 模块 | 缺陷对应 | 摘要 |
| --- | --- | --- | --- | --- |
| V2-P1-001 | `P1` | `streaming api` | V2-P1-001 | 补齐 `SSE` 断线续传事件回放能力 |
| V2-P1-002 | `P1` | `enterprise` | V2-P1-002 | 接入 `OPA` 策略评估引擎 |
| V2-P2-001 | `P2` | `memory system` | V2-P2-001 | 补齐层次化标识与自动压缩触发 |
| V2-P2-002 | `P2` | `multi-agent` | V2-P2-002 | 将 `DAG/Supervisor/Handoff` 接入运行时 |
| V2-P2-003 | `P2` | `governance` | V2-P2-003 | 落地背压事件与治理链路 |
| V2-P2-004 | `P2` | `runtime` | V2-P2-004 | 发射 `AGENT_STARTED/AGENT_COMPLETED` 事件 |

## 任务详情

### V2-P1-001 补齐 `SSE` 断线续传事件回放能力
- 现状证据：`src/main/java/com/example/agent/streaming/EventStreamService.java#stream` 仅订阅实时 `sink`；`src/main/java/com/example/agent/streaming/EventStreamService.java#validateCursor` 仅校验游标
- Shannon 证据：`vendor/Shannon/go/orchestrator/internal/streaming/manager.go#ReplaySince`、`vendor/Shannon/docs/streaming-api.md`
- 目标：支持按 `last_event_id`/`Last-Event-ID` 进行事件回放，并与实时流拼接，保证断线续传无缺口
- 关键改动：
  - 在 `EventStreamService` 增加回放读取逻辑，优先 `Redis` 事件流，必要时回退到事件持久化仓储
  - 将回放结果按 `seq` 排序并与实时 `sink` 合并，避免重复与乱序
  - 保持 `STREAM_GAP` 规则与关闭连接行为一致
- 影响接口：`/api/v1/stream/sse`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-002/FR-003）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK007、CHK021、CHK024）
- 验收要点：
  - 断线续传返回游标之后的事件序列，事件顺序严格递增
  - `last_event_id` 超出窗口触发 `STREAM_GAP`
  - 跨租户事件不可混入

### V2-P1-002 接入 `OPA` 策略评估引擎
- 现状证据：`src/main/java/com/example/agent/policy/PolicyEngine.java#evaluate` 为本地规则判断；检索 `OPA` 无命中
- Shannon 证据：`vendor/Shannon/config/opa/`、`vendor/Shannon/docs/agent-core-architecture.md`
- 目标：在策略评估链路引入 `OPA` 调用，补齐企业安全能力
- 关键改动：
  - `PolicyEngine` 增加 `OPA` 客户端与配置项（对齐 `agent.policy.opaUrl`）
  - 将评估结果映射为 `POLICY_DENIED`/通过，并记录审计日志
  - 保留本地规则作为降级兜底
- 影响接口：`/api/v1/policy/evaluate`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-024）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK044、CHK049）
- 验收要点：
  - `OPA` 拒绝时返回 `POLICY_DENIED` 且包含 `traceId/requestId`
  - `OPA` 不可用时触发可解释降级策略

### V2-P2-001 补齐层次化标识与自动压缩触发
- 现状证据：`src/main/java/com/example/agent/memory/MemoryStore.java#compress` 仅显式触发；检索 `recent/semantic` 无命中
- Shannon 证据：`vendor/Shannon/docs/memory-system-architecture.md`
- 目标：引入 `recent/semantic/compressed` 分层标识，并提供自动压缩触发策略
- 关键改动：
  - 新增层次字段规范，保存时默认 `recent`，向量检索标识 `semantic`
  - 在 `MemoryStore` 增加自动压缩条件（阈值/数量/时间窗口）
  - 压缩后写入 `compressed` 并可被检索
- 影响接口：`/api/v1/memory/search`、`/api/v1/memory/compress`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-006）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK009、CHK026）
- 验收要点：
  - 记忆记录具备明确层次标识
  - 达到阈值触发压缩，`compressed` 可检索

### V2-P2-002 将 `DAG/Supervisor/Handoff` 接入运行时
- 现状证据：`src/main/java/com/example/agent/runtime/AgentRuntime.java#executeStep` 仅调用 `MultiAgentCoordinator`
- Shannon 证据：`vendor/Shannon/docs/multi-agent-workflow-architecture.md`
- 目标：在运行时引入 `DAG` 执行与交接机制，补齐多智能体编排能力
- 关键改动：
  - 在 `AgentRuntime` 中引入 `AgentGraphExecutor` 与 `HandoffService`
  - 明确 `HANDOFF_REQUESTED`、`HANDOFF_COMPLETED` 事件发射位置
  - 对齐 `Supervisor` 编排入口与失败传播规则
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-021）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK006、CHK017）
- 验收要点：
  - 多智能体步骤可驱动 `DAG` 执行与交接事件
  - 失败传播路径可追溯

### V2-P2-003 落地背压事件与治理链路
- 现状证据：`BACKPRESSURE_APPLIED` 仅在 `EventType` 枚举中出现
- Shannon 证据：`vendor/Shannon/docs/agent-core-architecture.md`
- 目标：在限流/熔断/订阅压力场景中发射背压事件并记录治理指标
- 关键改动：
  - 在 `RateLimitService` 或 `EventStreamService` 增加背压触发点
  - 统一背压事件 `payload` 字段（原因、租户、触发点）
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-023）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（无对应条目）
- 验收要点：
  - 背压触发时输出 `BACKPRESSURE_APPLIED` 事件
  - 与 `rate.limit.count` 指标保持一致

### V2-P2-004 发射 `AGENT_STARTED/AGENT_COMPLETED` 事件
- 现状证据：仅在 `src/main/java/com/example/agent/domain/event/EventType.java` 定义，未发射
- Shannon 证据：`vendor/Shannon/docs/event-types.md`
- 目标：补齐运行时生命周期事件，提升可观测性与历史追溯
- 关键改动：
  - 在 `AgentRuntime` 运行入口与退出路径发射事件
  - 保证事件序列号与工作流一致
- 关联规范：`specs/001-agent-core-spec/spec.md`（事件类型定义）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK006、CHK017）
- 验收要点：
  - `WORKFLOW_STARTED` 之后出现 `AGENT_STARTED`
  - `WORKFLOW_COMPLETED` 之前出现 `AGENT_COMPLETED`