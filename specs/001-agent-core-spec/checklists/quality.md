# 规格质量清单: 001-agent-core-spec

**Purpose**: 用于发布门禁场景下的需求质量检查（完整性、清晰性、一致性、可度量性）
**Created**: 2026-01-26
**Feature**: `../spec.md`

**Note**: 本清单由 `/speckit.checklist` 生成，仅用于需求表达质量校验。

## 需求完整性

- [ ] CHK001 是否覆盖所有核心模块需求与边界（`agent core`、`orchestrator`、`streaming`、`history`、`memory`、`scheduler`、`auth`、`budget`）？ [Completeness, Spec §模块划分]
  验证方式: 手工步骤；验证命令: `rg -n "模块划分" specs/001-agent-core-spec/spec.md`；证据输出: 命中模块划分章节并列出核心模块清单；任务映射: T4-3；规格映射: Spec §模块划分
- [ ] CHK002 是否提供覆盖全部模块的 `Shannon` 对齐映射表？ [Completeness, Spec §`Shannon` 模块对齐映射表]
  验证方式: 手工步骤；验证命令: `rg -n "Shannon 模块对齐映射表" specs/001-agent-core-spec/spec.md`；证据输出: 映射表存在且列出 Shannon 模块与 Java 模块；任务映射: T4-3；规格映射: Spec §`Shannon` 模块对齐映射表
- [ ] CHK003 是否给出模块到接口到数据结构到事件流到存储的落点映射？ [Completeness, Spec §模块到接口到数据结构到事件流到存储落点]
  验证方式: 手工步骤；验证命令: `rg -n "模块到接口到数据结构到事件流到存储落点" specs/001-agent-core-spec/spec.md`；证据输出: 映射表覆盖接口/数据结构/事件流/存储落点；任务映射: T4-3；规格映射: Spec §模块到接口到数据结构到事件流到存储落点
- [ ] CHK004 是否定义 `SSE` 接口的路径、参数、返回结构与订阅规则？ [Completeness, Spec §流式 `SSE` 接口定义]
  验证方式: 手工步骤；验证命令: `rg -n "SSE 接口定义" specs/001-agent-core-spec/spec.md`；证据输出: 包含路径、参数、返回结构与订阅规则；任务映射: T1-2, T1-7, T1-8, T4-1；规格映射: Spec §流式 `SSE` 接口定义
- [ ] CHK005 是否定义统一响应与错误结构字段？ [Completeness, Spec §统一输出对象结构]
  验证方式: 手工步骤；验证命令: `rg -n "统一输出对象结构" specs/001-agent-core-spec/spec.md`；证据输出: 字段包含 code/message/data/traceId/requestId/details；任务映射: T0-2, T0-7；规格映射: Spec §统一输出对象结构
- [ ] CHK006 是否提供完整的错误码与 `HTTP` 状态映射表？ [Completeness, Spec §错误码与异常策略]
  验证方式: 手工步骤；验证命令: `rg -n "错误码与异常策略" specs/001-agent-core-spec/spec.md`；证据输出: 映射表包含 TENANT_MISSING/NOT_FOUND/FORBIDDEN/UNAUTHORIZED 等；任务映射: T0-5, T0-7, T1-19, T1-22；规格映射: Spec §错误码与异常策略
- [ ] CHK007 是否为 `SC-001`~`SC-006` 给出可度量的口径说明？ [Completeness, Spec §Success Criteria]
  验证方式: 手工步骤；验证命令: `rg -n "SC-00[1-6]" specs/001-agent-core-spec/spec.md`；证据输出: SC-001~SC-006 含统计窗口、样本规模、环境与指标口径；任务映射: T4-4~T4-9；规格映射: Spec §Success Criteria

## 需求清晰性

- [ ] CHK008 是否清晰区分 `TENANT_MISSING`、`NOT_FOUND`、`FORBIDDEN` 的语义与适用场景？ [Clarity, Spec §多租户与鉴权策略; Spec §错误码与异常策略]
  验证方式: 手工步骤；验证命令: `rg -n "TENANT_MISSING|NOT_FOUND|FORBIDDEN" specs/001-agent-core-spec/spec.md`；证据输出: 三类错误码语义与适用场景分离描述；任务映射: T0-5, T1-18, T1-19, T1-22；规格映射: Spec §多租户与鉴权策略; Spec §错误码与异常策略
- [ ] CHK009 是否明确 `STREAM_GAP` 与 `STREAM_TIMEOUT` 的触发条件与处理语义？ [Clarity, Spec §流式 `SSE` 接口定义; Spec §错误码与异常策略]
  验证方式: 手工步骤；验证命令: `rg -n "STREAM_GAP|STREAM_TIMEOUT" specs/001-agent-core-spec/spec.md`；证据输出: 触发条件与处理语义明确；任务映射: T1-7, T1-11, T1-12；规格映射: Spec §流式 `SSE` 接口定义; Spec §错误码与异常策略
- [ ] CHK010 是否明确 `idempotencyKey` 的作用范围与冲突处理规则？ [Clarity, Spec §并发、幂等、重试与失败恢复]
  验证方式: 手工步骤；验证命令: `rg -n "idempotencyKey" specs/001-agent-core-spec/spec.md`；证据输出: 作用范围与冲突处理规则明确；任务映射: T1-3, T1-5, T1-10, T1-15；规格映射: Spec §并发、幂等、重试与失败恢复
- [ ] CHK011 是否明确 `eventId=streamId:seq` 与序列单调规则？ [Clarity, Spec §流式 `SSE` 接口定义]
  验证方式: 手工步骤；验证命令: `rg -n "streamId:seq" specs/001-agent-core-spec/spec.md`；证据输出: `eventId=streamId:seq` 与序列单调规则明示；任务映射: T1-2, T1-5, T1-11；规格映射: Spec §流式 `SSE` 接口定义
- [ ] CHK012 是否明确 `usageId` 的生成规则与去重边界？ [Clarity, Spec §预算计量与存储]
  验证方式: 手工步骤；验证命令: `rg -n "usageId" specs/001-agent-core-spec/spec.md`；证据输出: 生成规则与去重边界描述明确；任务映射: T3-6, T3-7, T3-9；规格映射: Spec §预算计量与存储
- [ ] CHK013 错误码映射表是否明确每个错误码的语义与可重试性？ [Clarity, Spec §错误码与异常策略]
  验证方式: 手工步骤；验证命令: `rg -n "错误码与异常策略" specs/001-agent-core-spec/spec.md`；证据输出: 每个错误码包含语义与可重试性列；任务映射: T0-7, T1-7, T2-3, T1-19；规格映射: Spec §错误码与异常策略

## 需求一致性

- [ ] CHK014 用户故事中的跨租户拒绝场景与多租户策略是否一致？ [Consistency, Spec §`User Scenarios & Testing`; Spec §多租户与鉴权策略]
  验证方式: 手工步骤；验证命令: `rg -n "User Scenarios|多租户与鉴权策略" specs/001-agent-core-spec/spec.md`；证据输出: 用户故事跨租户拒绝与策略条款一致；任务映射: T1-18, T3-1, T1-22；规格映射: Spec §`User Scenarios & Testing`; Spec §多租户与鉴权策略
- [ ] CHK015 错误码列表是否覆盖所有章节引用的错误码（如 `EVENT_PERSIST_FAILED`、`STREAM_GAP`）？ [Consistency, Spec §错误码与异常策略; Spec §任务历史与时间线模型; Spec §流式 `SSE` 接口定义]
  验证方式: 手工步骤；验证命令: `rg -n "EVENT_PERSIST_FAILED|STREAM_GAP" specs/001-agent-core-spec/spec.md`；证据输出: 错误码列表覆盖章节引用的错误码；任务映射: T0-7, T1-7, T2-3, T1-11；规格映射: Spec §错误码与异常策略; Spec §任务历史与时间线模型; Spec §流式 `SSE` 接口定义
- [ ] CHK016 成功标准与计划中的性能目标是否一致？ [Consistency, Spec §Success Criteria; Plan §`Technical Context`]
  验证方式: 手工步骤；验证命令: `rg -n "Success Criteria" specs/001-agent-core-spec/spec.md; rg -n "Technical Context" specs/001-agent-core-spec/plan.md`；证据输出: 成功标准指标与计划性能目标一致；任务映射: T4-4~T4-9；规格映射: Spec §Success Criteria; Plan §`Technical Context`
- [ ] CHK017 计划与任务的阶段结构是否一致（包含 `Phase 4`）？ [Consistency, Plan §`Phase 4`：文档与验证收尾; Tasks §`Phase 4`]
  验证方式: 手工步骤；验证命令: `rg -n "Phase 4" specs/001-agent-core-spec/plan.md; rg -n "Phase 4" specs/001-agent-core-spec/tasks.md`；证据输出: 计划与任务均包含 Phase 4 且描述一致；任务映射: T4-1~T4-9；规格映射: Plan §`Phase 4`; Tasks §`Phase 4`

## 验收标准质量

- [ ] CHK018 功能需求验收要点是否可被客观验证？ [Acceptance Criteria, Spec §功能需求验收要点]
  验证方式: 手工步骤；验证命令: `rg -n "功能需求验收要点" specs/001-agent-core-spec/spec.md`；证据输出: 验收要点为可量化或可判定表述；任务映射: T4-2；规格映射: Spec §功能需求验收要点
- [ ] CHK019 用户故事验收场景是否与功能需求验收要点一致？ [Consistency, Spec §`User Scenarios & Testing`; Spec §功能需求验收要点]
  验证方式: 手工步骤；验证命令: `rg -n "User Scenarios|功能需求验收要点" specs/001-agent-core-spec/spec.md`；证据输出: 用户故事验收与功能验收要点一致；任务映射: T4-2；规格映射: Spec §`User Scenarios & Testing`; Spec §功能需求验收要点
- [ ] CHK020 `SC-001`~`SC-006` 是否包含统计窗口、样本规模、环境与指标口径？ [Acceptance Criteria, Spec §Success Criteria]
  验证方式: 手工步骤；验证命令: `rg -n "SC-00[1-6]" specs/001-agent-core-spec/spec.md`；证据输出: 口径覆盖统计窗口、样本规模、环境与指标口径；任务映射: T4-4~T4-9；规格映射: Spec §Success Criteria

## 场景覆盖

- [ ] CHK021 是否覆盖主要流程：任务提交、事件订阅、历史查询、记忆检索、调度、预算查询？ [Coverage, Spec §`User Scenarios & Testing`; Spec §模块划分]
  验证方式: 手工步骤；验证命令: `rg -n "User Scenarios|模块划分" specs/001-agent-core-spec/spec.md`；证据输出: 场景覆盖任务提交/事件订阅/历史查询/记忆检索/调度/预算；任务映射: T1-5, T1-7, T1-9, T2-5, T2-8, T3-5, T3-9；规格映射: Spec §`User Scenarios & Testing`; Spec §模块划分
- [ ] CHK022 是否覆盖鉴权失败与租户缺失的拒绝场景？ [Coverage, Spec §多租户与鉴权策略; Spec §错误码与异常策略]
  验证方式: 手工步骤；验证命令: `rg -n "鉴权|TENANT_MISSING" specs/001-agent-core-spec/spec.md`；证据输出: 鉴权失败与租户缺失拒绝场景明确；任务映射: T1-19, T1-20, T1-22；规格映射: Spec §多租户与鉴权策略; Spec §错误码与异常策略
- [ ] CHK023 是否覆盖预算阈值事件的触发与传播场景？ [Coverage, Spec §预算计量与存储]
  验证方式: 手工步骤；验证命令: `rg -n "BUDGET_THRESHOLD|预算阈值" specs/001-agent-core-spec/spec.md`；证据输出: 预算阈值事件触发与传播场景定义；任务映射: T3-8, T3-9；规格映射: Spec §预算计量与存储
- [ ] CHK024 是否覆盖断线续传的替代路径（`Last-Event-ID` 与参数游标）？ [Coverage, Spec §流式 `SSE` 接口定义]
  验证方式: 手工步骤；验证命令: `rg -n "Last-Event-ID|cursor" specs/001-agent-core-spec/spec.md`；证据输出: 断线续传路径包含 Last-Event-ID 与游标；任务映射: T1-7, T1-12；规格映射: Spec §流式 `SSE` 接口定义

## 边界与异常覆盖

- [ ] CHK025 是否定义 `last_event_id` 超窗处理与 `STREAM_GAP` 行为？ [Edge Case, Spec §流式 `SSE` 接口定义]
  验证方式: 手工步骤；验证命令: `rg -n "last_event_id|STREAM_GAP" specs/001-agent-core-spec/spec.md`；证据输出: 超窗处理与 STREAM_GAP 行为定义；任务映射: T1-7, T1-12；规格映射: Spec §流式 `SSE` 接口定义
- [ ] CHK026 是否定义持久化失败的重试与告警策略？ [Edge Case, Spec §任务历史与时间线模型; Spec §并发、幂等、重试与失败恢复]
  验证方式: 手工步骤；验证命令: `rg -n "重试|告警|EVENT_PERSIST_FAILED" specs/001-agent-core-spec/spec.md`；证据输出: 持久化失败重试与告警策略描述；任务映射: T2-3, T0-7；规格映射: Spec §任务历史与时间线模型; Spec §并发、幂等、重试与失败恢复
- [ ] CHK027 是否定义 `token` 为零或预算为零的处理规则？ [Edge Case, Spec §预算计量与存储]
  验证方式: 手工步骤；验证命令: `rg -n "token 为零|预算为零" specs/001-agent-core-spec/spec.md`；证据输出: token/预算为零处理规则明确；任务映射: T3-6, T3-7；规格映射: Spec §预算计量与存储
- [ ] CHK028 是否定义跨租户访问的存在性隐藏规则与返回码？ [Edge Case, Spec §多租户与鉴权策略; Spec §错误码与异常策略]
  验证方式: 手工步骤；验证命令: `rg -n "存在性|NOT_FOUND" specs/001-agent-core-spec/spec.md`；证据输出: 跨租户存在性隐藏与返回码定义；任务映射: T1-18, T2-5, T3-5；规格映射: Spec §多租户与鉴权策略; Spec §错误码与异常策略

## 非功能性要求

- [ ] CHK029 观测指标是否覆盖任务、事件、预算、存储、调度全链路？ [Non-Functional, Spec §观测指标]
  验证方式: 手工步骤；验证命令: `rg -n "观测指标" specs/001-agent-core-spec/spec.md`；证据输出: 指标清单覆盖任务/事件/预算/存储/调度；任务映射: T0-3, T1-5, T1-7, T3-7；规格映射: Spec §观测指标
- [ ] CHK030 性能目标是否覆盖关键入口与主要路径？ [Non-Functional, Spec §Success Criteria]
  验证方式: 手工步骤；验证命令: `rg -n "SC-00[1-6]" specs/001-agent-core-spec/spec.md`；证据输出: 性能目标覆盖关键入口与主路径；任务映射: T4-4~T4-9；规格映射: Spec §Success Criteria
- [ ] CHK031 多租户与鉴权是否明确不可绕过的要求？ [Non-Functional, Spec §宪章约束; Spec §多租户与鉴权策略]
  验证方式: 手工步骤；验证命令: `rg -n "不可绕过|多租户|鉴权" specs/001-agent-core-spec/spec.md`；证据输出: 不可绕过条款与边界描述清晰；任务映射: T1-19, T1-20, T1-21, T1-22, T3-1；规格映射: Spec §宪章约束; Spec §多租户与鉴权策略
- [ ] CHK032 可靠性是否包含退避重试与失败事件输出要求？ [Non-Functional, Spec §并发、幂等、重试与失败恢复]
  验证方式: 手工步骤；验证命令: `rg -n "退避|重试|失败" specs/001-agent-core-spec/spec.md`；证据输出: 可靠性策略包含退避重试与失败事件输出；任务映射: T2-3, T1-5；规格映射: Spec §并发、幂等、重试与失败恢复

## 依赖与假设

- [ ] CHK033 是否明确 `PostgreSQL`、`Redis`、`VectorStore` 的依赖与替代边界？ [Dependency, Spec §依赖与假设]
  验证方式: 手工步骤；验证命令: `rg -n "PostgreSQL|Redis|VectorStore" specs/001-agent-core-spec/spec.md`；证据输出: 依赖与替代边界说明；任务映射: T2-2, T1-7, T2-7；规格映射: Spec §依赖与假设
- [ ] CHK034 是否说明非 `Spring` 组件引入的原因与风险？ [Dependency, Spec §记忆抽象与落盘策略; Spec §依赖与假设]
  验证方式: 手工步骤；验证命令: `rg -n "记忆抽象与落盘策略|依赖与假设" specs/001-agent-core-spec/spec.md`；证据输出: 非 Spring 组件引入原因与风险描述；任务映射: T2-7；规格映射: Spec §记忆抽象与落盘策略; Spec §依赖与假设
- [ ] CHK035 是否明确外部模型或工具调用的可用性假设？ [Assumption, Spec §依赖与假设]
  验证方式: 手工步骤；验证命令: `rg -n "外部模型|工具调用|依赖与假设" specs/001-agent-core-spec/spec.md`；证据输出: 外部模型/工具可用性假设明确；任务映射: T1-6, T2-7；规格映射: Spec §依赖与假设

## 歧义与冲突

- [ ] CHK036 “不泄漏存在性”是否定义为可判定条件？ [Ambiguity, Spec §多租户与鉴权策略]
  验证方式: 手工步骤；验证命令: `rg -n "不泄漏存在性" specs/001-agent-core-spec/spec.md`；证据输出: 存在性隐藏定义可判定；任务映射: T1-18, T2-5, T3-5；规格映射: Spec §多租户与鉴权策略
- [ ] CHK037 “关键事件”范围是否定义并与持久化策略一致？ [Ambiguity, Spec §任务历史与时间线模型]
  验证方式: 手工步骤；验证命令: `rg -n "关键事件|事件持久化|任务历史" specs/001-agent-core-spec/spec.md`；证据输出: 关键事件范围与持久化策略一致；任务映射: T2-3, T1-5；规格映射: Spec §任务历史与时间线模型
- [ ] CHK038 错误码语义与用户故事验收是否存在冲突？ [Conflict, Spec §`User Scenarios & Testing`; Spec §错误码与异常策略]
  验证方式: 手工步骤；验证命令: `rg -n "错误码与异常策略|User Scenarios" specs/001-agent-core-spec/spec.md`；证据输出: 错误码语义与用户故事验收无冲突；任务映射: T0-7, T1-18, T2-5；规格映射: Spec §`User Scenarios & Testing`; Spec §错误码与异常策略

## 可追溯性

- [ ] CHK039 需求编号与验收要点是否一一对应且可追溯？ [Traceability, Spec §`Functional Requirements`; Spec §功能需求验收要点]
  验证方式: 手工步骤；验证命令: `rg -n "Functional Requirements|功能需求验收要点" specs/001-agent-core-spec/spec.md`；证据输出: 需求编号与验收要点一一对应；任务映射: T4-3；规格映射: Spec §`Functional Requirements`; Spec §功能需求验收要点
- [ ] CHK040 映射表是否可追溯到 `Java` 包/接口/类？ [Traceability, Spec §模块到接口到数据结构到事件流到存储落点; Spec §`Shannon` 模块对齐映射表]
  验证方式: 手工步骤；验证命令: `rg -n "模块到接口到数据结构到事件流到存储落点|Shannon 模块对齐映射表" specs/001-agent-core-spec/spec.md`；证据输出: 映射表可追溯到 Java 包/接口/类；任务映射: T4-3；规格映射: Spec §模块到接口到数据结构到事件流到存储落点; Spec §`Shannon` 模块对齐映射表

## Notes

- 勾选通过项使用 `[x]`
- 可在条目后追加发现与结论
- 条目编号按顺序递增，便于引用
