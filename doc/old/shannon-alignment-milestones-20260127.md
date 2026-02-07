# Shannon 对齐整改里程碑计划 - 20260127

## 目标
- 按优先级完成 `P1`、`P2` 缺陷整改，确保与规格与清单一致
- 输出可验收的实现、测试与文档变更清单

## 里程碑总览
| 里程碑 | 目标 | 任务范围 | 输出物 | 验收口径 | 依赖 |
| --- | --- | --- | --- | --- | --- |
| M1 | 补齐 `SSE` 断线续传回放 | `EventStreamService`、`SseStreamController` | 实现+测试+文档 | CHK007/CHK021/CHK024 | 无 |
| M2 | 接入 `OPA` 策略评估 | `PolicyEngine`、配置项 | 实现+测试+文档 | CHK044/CHK049 | M1 可并行 |
| M3 | 运行时事件与背压落地 | `AgentRuntime`、`RateLimitService` | 实现+测试 | CHK006/CHK017 | M1/M2 可并行 |
| M4 | 记忆层次化与自动压缩 | `MemoryStore`、`MemoryRepository` | 实现+测试 | CHK009/CHK026 | M3 可并行 |
| M5 | 多智能体编排接入运行时 | `AgentRuntime`、`AgentGraphExecutor`、`HandoffService` | 实现+测试 | CHK006/CHK017 | M3/M4 可并行 |

## M1 补齐 `SSE` 断线续传回放
- 对应缺陷：V2-P1-001
- 目标：断线续传可回放历史事件并与实时流拼接
- 主要任务：
  - 新增回放读取逻辑，优先 `Redis` 流，必要时回退到事件日志仓储
  - 回放与实时流合并，按 `seq` 去重并保证递增
  - 保持 `STREAM_GAP` 规则与关闭连接逻辑一致
  - 补齐单元/集成测试，覆盖游标超窗与跨租户隔离
- 影响接口：`/api/v1/stream/sse`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-002/FR-003）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK007、CHK021、CHK024）
- 验收输出：
  - 回放事件序列可验证，顺序与 `eventId` 一致
  - 断线续传命中 `STREAM_GAP` 时返回冲突错误

## M2 接入 `OPA` 策略评估
- 对应缺陷：V2-P1-002
- 目标：策略评估与企业安全对齐
- 主要任务：
  - 增加 `OPA` 客户端与配置项（对齐 `agent.policy.opaUrl`）
  - `PolicyEngine` 优先调用 `OPA`，失败降级本地规则
  - 补充日志与错误码映射，保持 `POLICY_DENIED` 语义
  - 补齐策略拒绝与 `OPA` 不可用的测试用例
- 影响接口：`/api/v1/policy/evaluate`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-024）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK044、CHK049）
- 验收输出：
  - `OPA` 拒绝返回 `POLICY_DENIED` 并带 `traceId/requestId`
  - `OPA` 不可用时触发可解释降级

## M3 运行时事件与背压落地
- 对应缺陷：V2-P2-003、V2-P2-004
- 目标：补齐运行时生命周期事件与背压治理
- 主要任务：
  - 在 `AgentRuntime` 入口与终止路径发射 `AGENT_STARTED/AGENT_COMPLETED`
  - 在限流或订阅压力触发处发射 `BACKPRESSURE_APPLIED`
  - 补齐测试，验证事件顺序与序列号
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-023 与事件类型定义）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK006、CHK017）
- 验收输出：
  - `WORKFLOW_STARTED` 之后出现 `AGENT_STARTED`
  - `WORKFLOW_COMPLETED` 之前出现 `AGENT_COMPLETED`
  - 背压触发时可观察到事件与指标

## M4 记忆层次化与自动压缩
- 对应缺陷：V2-P2-001
- 目标：补齐 `recent/semantic/compressed` 层次与自动压缩策略
- 主要任务：
  - 写入默认层级 `recent`，语义检索标识 `semantic`
  - 增加自动压缩触发条件并写入 `compressed`
  - 补齐压缩触发与检索行为测试
- 影响接口：`/api/v1/memory/search`、`/api/v1/memory/compress`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-006）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK009、CHK026）
- 验收输出：
  - 层次字段稳定可追溯
  - 达到阈值后自动压缩可验证

## M5 多智能体编排接入运行时
- 对应缺陷：V2-P2-002
- 目标：将 `DAG/Supervisor/Handoff` 接入运行时主链路
- 主要任务：
  - 在 `AgentRuntime` 引入 `AgentGraphExecutor` 与 `HandoffService`
  - 发射 `HANDOFF_REQUESTED/HANDOFF_COMPLETED` 事件
  - 明确失败传播与回退策略
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-021）
- 关联清单：`specs/001-agent-core-spec/checklists/checklist.md`（CHK006、CHK017）
- 验收输出：
  - 多智能体步骤能触发 `DAG` 执行与交接事件
  - 失败传播路径可追溯