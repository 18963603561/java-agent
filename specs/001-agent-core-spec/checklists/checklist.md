# 验收与质量门禁清单: `Java Shannon Agent Orchestrator Core`

**Purpose**: 用于审查规格、计划与任务中的验收与质量门禁要求是否完整、清晰、可追溯，作为上线前门禁依据。
**Created**: 2026-01-25
**Feature**: `specs/001-agent-core-spec/spec.md`

**Note**: 本清单由 `/speckit.checklist` 生成，聚焦需求与文档质量，不用于实现验证。

## 需求完整性

- [ ] CHK001 是否在规格中提供覆盖全部核心模块的 `Shannon` 对齐映射表并包含差异说明？（通过: 映射表覆盖 `agent core`、`orchestrator`、`streaming api`、`event types`、`task history & timeline`、`memory system`、`scheduled tasks`、`authentication & multitenancy`、`token budget tracking` 且有对齐说明；不通过: 缺少任一模块或无差异说明）[Blocker][`Completeness`][Spec §Shannon 模块对齐映射表][Task T4-3]
- [ ] CHK002 是否在规格中明确各模块的职责边界与禁止承担职责？（通过: `orchestrator`、`agent core`、`streaming`、`history`、`memory`、`auth`、`budget` 的边界与禁止事项明确且无遗漏；不通过: 职责交叉或缺失）[Blocker][`Completeness`][Spec §模块边界与职责约束][Task T1-5]
- [ ] CHK003 是否在计划中对各阶段的 `Spring` 组件选型给出明确且统一的说明？（通过: Phase 0~3 的选型均以 `Spring Boot` 生态为主且无未说明的非 `Spring` 组件；不通过: 存在未说明或违背优先约束的选型）[Blocker][`Completeness`][Plan §Phase 0/1/2/3 Spring 组件选型说明][Task T0-1]
- [ ] CHK004 是否在规格中完整定义统一响应与错误结构并要求全局异常映射？（通过: `ApiResponse` 与 `ErrorResponse` 字段完整且要求全局异常统一映射；不通过: 字段缺失或缺少映射要求）[Blocker][`Completeness`][Spec §统一输出对象结构][Spec §错误码与异常策略][Task T0-2][Task T0-7]
- [ ] CHK005 是否在规格中完整列出核心接口并明确输入输出包含 `TenantContext`？（通过: 任务、事件、记忆、调度、预算等接口齐全且签名包含租户上下文；不通过: 任一核心接口缺失或上下文遗漏）[Blocker][`Completeness`][Spec §关键接口][Task T1-4]
- [ ] CHK006 是否在规格中完整定义事件类型与核心事件分类？（通过: 工作流、工具、LLM 等事件分类齐全；不通过: 关键事件缺失）[Blocker][`Completeness`][Spec §DTO 与事件类型定义][Task T1-1]
- [ ] CHK007 是否在规格中完整定义 `SSE` 接口参数、断线续传规则与错误返回？（通过: 路径、参数、`Last-Event-ID` 规则与 `STREAM_GAP` 等错误场景均定义；不通过: 任何关键规则缺失）[Blocker][`Completeness`][Spec §流式 SSE 接口定义][Task T1-7][Task T1-8]
- [ ] CHK008 是否在规格中完整定义事件持久化策略与写入顺序？（通过: `Redis` 与 `PostgreSQL` 的职责、顺序、排除事件明确；不通过: 缺少顺序或排除规则）[Blocker][`Completeness`][Spec §任务历史与时间线模型][Task T2-2][Task T2-3]
- [ ] CHK009 是否在规格中完整定义 `memory system` 的职责边界与落盘策略？（通过: 记忆层次、存储职责与落盘位置明确；不通过: 边界或落盘缺失）[Blocker][`Completeness`][Spec §记忆抽象与落盘策略][Task T2-7]
- [ ] CHK010 是否在规格中完整定义多租户与鉴权隔离规则覆盖所有入口模块？（通过: `streaming`、`history`、`budget` 等均规定租户隔离；不通过: 任一入口未覆盖）[Blocker][`Completeness`][Spec §多租户与鉴权策略][Task T0-5][Task T3-1]
- [ ] CHK011 是否在规格中完整定义 `token budget` 计量点、传播路径与存储要求？（通过: 计量点、`usageId` 幂等、存储位置均明确；不通过: 计量链路缺失或不完整）[Blocker][`Completeness`][Spec §预算计量与存储][Task T3-9]

## 需求清晰性

- [ ] CHK012 是否明确 `Redis Stream` 的保留窗口与容量阈值？（通过: `TTL` 与容量阈值明确；不通过: 仅给出“默认”或缺少数值）[Recommended][`Clarity`][Spec §任务历史与时间线模型][Task T1-7]
- [ ] CHK013 是否明确幂等键的作用范围与返回行为？（通过: `TaskRequest.idempotencyKey` 与 `ScheduleSpec.idempotencyKey` 的重复行为明确；不通过: 仅描述“幂等”但无具体规则）[Blocker][`Clarity`][Spec §并发、幂等、重试与失败恢复][Spec §定时任务模型][Task T1-9][Task T3-4]
- [ ] CHK014 是否明确事件序列号生成与事件顺序约束？（通过: `seq` 生成主体与顺序规则明确；不通过: 顺序约束不完整或歧义）[Blocker][`Clarity`][Spec §事件模型与顺序约束][Task T1-5]
- [ ] CHK015 是否明确错误码与 `HTTP` 状态的映射规则？（通过: 各错误码对应的状态码规则明确；不通过: 仅列错误码未给出映射）[Recommended][`Clarity`][Gap][Spec §错误码与异常策略][Task T0-7][Task T1-8][Task T3-1]

## 需求一致性

- [ ] CHK016 是否确保规格、计划与任务中的控制器命名一致？（通过: `SseStreamController`、`TaskController`、`ScheduleController`、`TimelineController` 在三者中一致；不通过: 存在命名漂移）[Blocker][`Consistency`][Spec §推荐包结构与关键类名][Plan §Project Structure][Task T1-8][Task T1-9][Task T3-5][Task T2-5]
- [ ] CHK017 是否确保事件类型清单在规格与计划中一致？（通过: Phase 1 事件类型与规格清单一致；不通过: 存在遗漏或新增未说明）[Blocker][`Consistency`][Spec §DTO 与事件类型定义][Plan §Phase 1 关键事件类型][Task T1-1]
- [ ] CHK018 是否确保 `streaming`、`history`、`memory` 的职责边界描述一致？（通过: 三者职责与数据来源在多个章节一致；不通过: 存在相互矛盾或覆盖）[Blocker][`Consistency`][Spec §模块边界与职责约束][Spec §任务历史与时间线模型][Spec §记忆抽象与落盘策略][Task T1-7][Task T2-3][Task T2-7]

## 验收标准质量

- [ ] CHK019 是否确保 Phase 0~3 的验收标准可度量且与成功标准对齐？（通过: 每个阶段验收标准可度量并与成功标准一致；不通过: 仅描述结果无度量指标）[Blocker][`Acceptance Criteria`][Plan §Phase 0/1/2/3 验收标准][Spec §Success Criteria][Task T0-1]
- [ ] CHK020 是否确保所有任务条目包含清晰的验收标准与验证方式？（通过: 任务条目均包含验收标准与验证方式且无歧义；不通过: 存在缺失或模糊描述）[Recommended][`Acceptance Criteria`][Task T0-1][Task T1-7][Task T2-3][Task T3-4]

## 场景覆盖

- [ ] CHK021 是否在规格中覆盖 `SSE` 的断线续传与类型过滤场景？（通过: 续传与过滤规则完整；不通过: 任一场景缺失）[Blocker][`Coverage`][Spec §流式 SSE 接口定义][Task T1-7][Task T1-8]
- [ ] CHK022 是否在规格中覆盖跨租户访问被拒绝的场景？（通过: `streaming`、`history`、`budget` 均规定跨租户拒绝或返回规则；不通过: 任一模块未覆盖）[Blocker][`Coverage`][Spec §多租户与鉴权策略][Task T1-7][Task T2-5][Task T3-5]
- [ ] CHK023 是否在规格中覆盖预算阈值事件的触发与传播场景？（通过: `BUDGET_THRESHOLD` 触发条件与传播路径明确；不通过: 未说明触发或传播）[Blocker][`Coverage`][Spec §预算计量与存储][Task T3-8]

## 边界与异常覆盖

- [ ] CHK024 是否在规格中覆盖 `last_event_id` 超出窗口的处理规则？（通过: `STREAM_GAP` 行为与连接处理明确；不通过: 超窗处理缺失）[Blocker][`Edge Case`][Spec §流式 SSE 接口定义][Task T1-7]
- [ ] CHK025 是否在规格中覆盖事件持久化失败的重试与告警规则？（通过: 重试策略与 `ERROR_OCCURRED`/`EVENT_PERSIST_FAILED` 记录规则明确；不通过: 失败处理缺失）[Blocker][`Edge Case`][Spec §任务历史与时间线模型][Task T2-3]
- [ ] CHK026 是否在规格中覆盖向量嵌入不可用的降级策略？（通过: 明确返回空结果且不阻断主流程；不通过: 降级策略缺失）[Blocker][`Edge Case`][Spec §记忆抽象与落盘策略][Task T2-7]
- [ ] CHK027 是否在规格中覆盖鉴权失败或租户缺失的处理规则？（通过: 明确返回结果与日志/事件要求；不通过: 处理规则缺失）[Blocker][`Edge Case`][Spec §多租户与鉴权策略][Task T0-5][Task T3-1]

## 非功能性要求

- [ ] CHK028 是否在规格中给出覆盖任务、事件、预算、存储、调度的指标清单？（通过: 指标名称覆盖全部模块且无遗漏；不通过: 指标缺失或未覆盖关键模块）[Blocker][`Non-Functional`][Spec §观测指标][Task T0-3][Task T1-7][Task T2-3][Task T3-8]
- [ ] CHK029 是否在规格中明确 `SSE` 的稳定性阈值与超时行为？（通过: 明确无事件超时阈值与处理动作；不通过: 阈值或行为缺失）[Recommended][`Non-Functional`][Spec §流式 SSE 接口定义][Task T1-7][Task T1-8]
- [ ] CHK030 是否在规格或计划中明确阻塞式访问的隔离要求？（通过: `WebFlux` 场景下 `boundedElastic` 隔离要求明确；不通过: 隔离要求缺失）[Blocker][`Non-Functional`][Spec §依赖与假设][Plan §Technical Context][Task T2-3][Task T2-7][Task T3-4]

## 依赖与假设

- [ ] CHK031 是否在规格中明确核心依赖与替代边界？（通过: `PostgreSQL`、`Redis`、`VectorStore` 的作用与替代边界明确；不通过: 依赖或边界缺失）[Blocker][`Dependencies`][Spec §依赖与假设][Task T2-7]
- [ ] CHK032 是否在规格中说明非 `Spring` 组件引入的原因与风险？（通过: 引入原因与风险评估明确；不通过: 未说明原因或风险）[Blocker][`Dependencies`][Spec §记忆抽象与落盘策略][Task T2-7]

## 歧义与冲突

- [ ] CHK033 是否明确 `persist=true` 的语义并与计划一致？（通过: `persist=true` 含义与写入路径明确且与计划一致；不通过: 含义不清或与计划矛盾）[Blocker][`Ambiguity`][Spec §任务历史与时间线模型][Plan §Phase 2 存储与状态管理策略][Task T2-4]
- [ ] CHK034 是否确保事件顺序与持久化顺序无冲突？（通过: `Redis` 先写、`PostgreSQL` 后补写的顺序与事件顺序约束一致；不通过: 存在冲突或未说明）[Blocker][`Conflict`][Spec §任务历史与时间线模型][Plan §Phase 2 存储与状态管理策略][Task T2-3]
- [ ] CHK035 是否明确错误码与 `HTTP` 状态的冲突处理方式？（通过: 明确冲突处理或记录为显式缺口；不通过: 冲突规则缺失且未记录）[Recommended][`Ambiguity`][Gap][Spec §错误码与异常策略][Task T0-7][Task T1-8][Task T3-1]

## 性能与可靠性验证

- [ ] CHK036 是否完成任务提交与鉴权开销基线验证并记录阈值？（通过: `p95` 提交耗时 <= 2 秒且鉴权开销基线已记录；不通过: 缺少基线或不满足阈值）[Blocker][`Non-Functional`][Spec §Success Criteria][Task T4-4]
- [ ] CHK037 是否完成 `SSE` 断线恢复与 `Redis` 回退耗时基线验证？（通过: 断线恢复 `p95` <= 5 秒且回退耗时基线已记录；不通过: 缺少基线或不满足阈值）[Blocker][`Non-Functional`][Spec §Success Criteria][Task T4-5]
- [ ] CHK038 是否完成事件吞吐与持久化成功率基线验证？（通过: 成功率 >= 99.9% 且吞吐基线已记录；不通过: 缺少基线或成功率不足）[Blocker][`Non-Functional`][Spec §Success Criteria][Task T4-6]
- [ ] CHK039 是否完成任务历史查询延迟基线验证？（通过: `p95` 查询延迟 <= 3 秒且基线已记录；不通过: 缺少基线或不满足阈值）[Blocker][`Non-Functional`][Spec §Success Criteria][Task T4-7]
- [ ] CHK040 是否完成越权访问与存在性泄漏验证？（通过: 跨租户访问返回 `NOT_FOUND` 且无泄漏；不通过: 返回码不一致或存在性泄漏）[Blocker][`Non-Functional`][Spec §Success Criteria][Task T4-8]
- [ ] CHK041 是否完成预算覆盖率与内存增长/清理基线验证？（通过: 覆盖率 100% 且清理基线已记录；不通过: 覆盖率不足或缺少基线）[Blocker][`Non-Functional`][Spec §Success Criteria][Task T4-9]
- [ ] CHK042 是否完成多租户越权验证覆盖新增接口？（通过: `timeline/steps`、`mcp/tools`、`replay` 等入口跨租户访问返回 `NOT_FOUND`；不通过: 任一入口可越权读取）[Blocker][`Security`][Spec §多租户与鉴权策略][Task T1.5-10][Task T3-10]
- [ ] CHK043 是否完成预算触发模型降级验证？（通过: 预算超限触发 `MODEL_FALLBACK_APPLIED` 且记录可追溯；不通过: 未触发或记录缺失）[Blocker][`Governance`][Spec §预算计量与存储][Task T3-14]
- [ ] CHK044 是否完成 `OPA` 拒绝验证？（通过: 触发策略拒绝返回 `POLICY_DENIED` 并写入审计；不通过: 返回码不一致或审计缺失）[Blocker][`Security`][Spec §Enterprise 安全][Task T3-12]
- [ ] CHK045 是否完成 `WASI` 沙箱资源限制验证？（通过: 文件/网络/资源限制触发 `SANDBOX_DENIED` 或 `SANDBOX_VIOLATION`；不通过: 限制未生效）[Blocker][`Security`][Spec §Enterprise 安全][Task T3-13]

## 错误码与安全验证

- [ ] CHK046 是否完成 `TENANT_MISSING` 错误验证？（通过: 缺失 `X-Tenant-Id` 调用接口返回 `HTTP 400` 且 `ErrorResponse.code=TENANT_MISSING`，日志包含 `tenantId`、`userId`、`traceId`、`requestId`；不通过: 状态码或错误码不一致，或日志字段缺失）[Blocker][`Security`][Spec §错误码与异常策略][Spec §多租户与鉴权策略]
- [ ] CHK047 是否完成 `AUTH_FAILED` 错误验证？（通过: 使用无效 `X-API-Key` 返回 `HTTP 401` 且 `ErrorResponse.code=UNAUTHORIZED`，日志包含 `tenantId`、`userId`、`traceId`、`requestId` 并记录 `AUTH_FAILED`；不通过: 状态码或错误码不一致，或日志字段缺失）[Blocker][`Security`][Spec §错误码与异常策略][Spec §多租户与鉴权策略]
- [ ] CHK048 是否完成 `MCP_UNAVAILABLE` 错误验证？（通过: 模拟 `MCP` 不可用返回 `HTTP 503` 且 `ErrorResponse.code=MCP_UNAVAILABLE`，日志包含 `tenantId`、`userId`、`traceId`、`requestId`；不通过: 状态码或错误码不一致，或日志字段缺失）[Blocker][`Reliability`][Spec §错误码与异常策略][Spec §MCP/Skills/Hooks]
- [ ] CHK049 是否完成 `POLICY_DENY` 错误验证？（通过: 策略拒绝返回 `HTTP 403` 且 `ErrorResponse.code=POLICY_DENIED`（别名 `POLICY_DENY`），日志包含 `tenantId`、`userId`、`traceId`、`requestId`；不通过: 状态码或错误码不一致，或日志字段缺失）[Blocker][`Security`][Spec §错误码与异常策略][Spec §Enterprise 安全]
- [ ] CHK050 是否完成 `SANDBOX_DENY` 错误验证？（通过: 沙箱拒绝返回 `HTTP 403` 且 `ErrorResponse.code=SANDBOX_DENIED`（别名 `SANDBOX_DENY`），日志包含 `tenantId`、`userId`、`traceId`、`requestId`；不通过: 状态码或错误码不一致，或日志字段缺失）[Blocker][`Security`][Spec §错误码与异常策略][Spec §Enterprise 安全]
- [ ] CHK051 是否完成 `REPLAY_NOT_FOUND` 错误验证？（通过: 回放不存在返回 `HTTP 404` 且 `ErrorResponse.code=REPLAY_NOT_FOUND`，日志包含 `tenantId`、`userId`、`traceId`、`requestId`；不通过: 状态码或错误码不一致，或日志字段缺失）[Blocker][`Reliability`][Spec §错误码与异常策略][Spec §Production 治理]
- [ ] CHK052 是否完成 `HOOK_BLOCKED` 错误验证？（通过: 启用阻断型 `Hook` 返回 `HTTP 409` 且 `ErrorResponse.code=HOOK_BLOCKED`，日志包含 `tenantId`、`userId`、`traceId`、`requestId`；不通过: 状态码或错误码不一致，或日志字段缺失）[Blocker][`Security`][Spec §错误码与异常策略][Spec §MCP/Skills/Hooks]
- [ ] CHK053 是否完成限流背压事件验证？（通过: 触发 `RATE_LIMITED` 时事件流包含 `BACKPRESSURE_APPLIED` 与 `TOOL_ERROR`，且 payload 含 `governanceType`、`trigger`、`toolName`；不通过: 任一事件缺失或关键字段缺失）[Blocker][`Governance`][Spec §Production 治理][Task T-P2-BP-04][Task T-P2-BP-07]
- [ ] CHK054 是否完成熔断治理事件验证？（通过: 触发 `CIRCUIT_OPEN` 时事件流包含 `CIRCUIT_OPENED`、`BACKPRESSURE_APPLIED`、`TOOL_ERROR`，且 payload 含 `circuitState` 与 `errorCode`；不通过: 任一事件缺失或关键字段缺失）[Blocker][`Governance`][Spec §Production 治理][Task T-P2-BP-04][Task T-P2-BP-07]
- [ ] CHK055 是否完成重试等待事件验证？（通过: 可重试退避阶段出现 `WAITING` 事件，payload 含 `delayMs`、`attempt`、`trigger`；不通过: 事件缺失或关键字段缺失）[Blocker][`Governance`][Spec §Production 治理][Task T-P2-BP-05][Task T-P2-BP-07]
- [ ] CHK056 是否完成上下文压缩阶段事件契约验证？（通过: `CONTEXT_COMPRESSION_STAGE` 事件包含 `stage`、`compressionSummary`、`evidence*` 字段；不通过: 任一关键字段缺失）[Blocker][`Observability`][Spec §DTO 与事件类型定义][Task P3-PKG-002]
- [ ] CHK057 是否完成压缩阶段枚举完整性验证？（通过: `ContextSnapshotStage` 覆盖 `TRIGGERED/SHAPED/EXECUTED/INJECTED/SKIPPED/FAILED/COMPRESSED`；不通过: 任一阶段缺失）[Recommended][`Contract`][Spec §DTO 与事件类型定义][Task P3-PKG-002]
- [ ] CHK058 是否完成运行时压缩结果类型化读取验证？（通过: 消费侧通过 `ContextRuntimeView.getContextCompression()` 读取，不再手工 `Map` key；不通过: 仍存在手工 key 读取）[Blocker][`Architecture`][Spec §模块边界与职责约束][Task P3-PKG-004]
- [ ] CHK059 是否完成压缩观测指标统一命名验证？（通过: 使用统一指标键 `context_compression_*` 且标签键一致；不通过: 存在散落命名或口径冲突）[Recommended][`Observability`][Spec §观测指标][Task P3-PKG-003]

## Notes

- 勾选通过项使用 `[x]`
- 可在条目后追加发现与结论
- 保持条目编号连续，便于引用
