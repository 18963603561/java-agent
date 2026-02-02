# 上下文工程组件清单

## 扫描范围与说明
- 扫描目录：`src/main/java`、`doc/`、`specs/`
- 只整理与上下文构建、预算、裁剪、压缩、提示词装配、证据与记忆相关的核心类
- 类名与字段名均以源码为准，不存在的类不列入清单

## 模块与关键类（按包分组）
### com.example.agent.context
- `ContextBuilder`：上下文构建器接口，定义构建快照的入口
- `DefaultContextBuilder`：构建 `ContextSnapshot`，串联预算分配、裁剪与压缩并发布事件
- `ContextAssembler`：上下文装配接口，产出提示词装配输入
- `DefaultContextAssembler`：生成 `PromptAssemblyInput` 并补齐三段提示词文本与预算统计
- `ContextSnapshot`：上下文快照载体，包含运行元信息与分区内容
- `ContextBuildRequest`：构建请求载体，汇聚任务、召回与运行时上下文
- `ContextBuildResult`：构建结果载体，返回快照、预算与裁剪信息
- `PromptAssemblyInput`：三段提示词与预算统计的输入载体
- `WorkingMemory`：工作记忆分区，承载摘要、要点与 `EvidencePack`
- `EvidencePack`：证据聚合载体，记录工具、记忆与研究引用
- `EvidencePackService`：创建与追加证据，并维护证据统计
- `ContextPolicy`：上下文策略，控制召回优先级与裁剪上限
- `DomainKnowledge`：领域知识分区，承载引用与外部知识
- `LongTermMemory`：长期记忆分区，承载 `MemoryRef` 列表
- `ToolState`：工具状态分区，记录可用与选择工具
- `RoleBoundary`：角色边界分区，记录风险与权限约束
- `TaskIntent`：任务意图分区，记录输入与约束
- `RuntimeMeta`：运行元信息分区，记录租户与追踪信息
- `BudgetState`：预算状态分区，记录分配与使用情况
- `AuditMetadata`：审计元数据
- `Citation`：引用数据对象
- `MemoryEvidence`：记忆证据对象
- `MemoryRef`：记忆引用对象
- `ToolCallEvidence`：工具调用证据对象
- `ToolCallState`：工具调用状态对象
- `EvidenceStats`：证据统计对象
- `EvidenceItem`：证据条目对象（历史兼容）

### com.example.agent.budget
- `ContextBudgetPolicy`：上下文预算策略，定义分区比例与版本
- `ContextBudgetProperties`：预算配置，提供默认比例与裁剪顺序
- `ContextBudgetRequest`：预算分配请求
- `ContextBudgetAllocator`：预算分配接口
- `DefaultContextBudgetAllocator`：按比例分配预算并受 `TokenBudgetManager` 阈值约束
- `ContextBudgetAllocation`：预算分配结果，包含各 `ContextSection` 预算
- `ContextSection`：上下文分区枚举
- `ContextTrimSection`：裁剪顺序分组枚举
- `ContextPruner`：裁剪接口
- `DefaultContextPruner`：按 `ContextPolicy` 裁剪 `EvidencePack`、记忆与引用
- `ContextPruneResult`：裁剪结果载体
- `ContextTrimmer`：裁剪接口
- `DefaultContextTrimmer`：按预算裁剪，输出 `ContextTrimReport`
- `ContextTrimReport`：裁剪报告，记录前后令牌与移除统计
- `ContextTrimResult`：裁剪结果载体
- `ContextCompressionController`：预算超限时触发压缩并回填摘要
- `ContextCompressionProperties`：压缩触发开关与冷却配置
- `ContextCompressionRequest`：压缩请求载体
- `ContextCompressionResult`：压缩结果载体
- `TokenBudgetManager`：预算计量与阈值控制，影响预算上限

### com.example.agent.memory
- `MemoryStore`：记忆存取服务，提供保存、检索与压缩
- `RecentMemoryStore`：近期记忆存取
- `SemanticMemoryStore`：语义检索通道
- `CompressedMemoryStore`：压缩记忆生成与检索
- `MemoryRecallService`：记忆召回并生成摘要，写入 `EvidencePack`
- `MemoryRecallProperties`：召回配置
- `MemoryRecallResult`：召回结果载体
- `MemoryWriteService`：任务输入输出写入记忆
- `MemoryWriteProperties`：写入配置
- `MemoryRecord`：记忆记录实体
- `ConversationSummary`：会话摘要结构化对象
- `WorkingMemorySummary`：工作记忆摘要结构化对象
- `MemoryPolicy`：自动压缩触发策略
- `MemoryExpireProperties`：过期配置
- `MemoryExpirationService`：过期处理
- `VectorStore`：向量存储接口
- `EmbeddingService`：向量嵌入接口
- `TokenEstimator`：令牌估算器

### com.example.agent.model
- `PromptAssembler`：提示词装配接口
- `DefaultPromptAssembler`：按预算裁剪提示词并输出 `PromptBundle`
- `PromptBundle`：提示词消息集合与裁剪结果
- `PromptTemplate`：提示词模板渲染器
- `PromptMessage`：提示词消息对象
- `PromptRole`：提示词角色枚举
- `ModelInvocationService`：模型调用服务
- `ModelRequest`：模型请求载体

### com.example.agent.streaming
- `ContextEventPublisher`：发布上下文快照与阶段事件
- `ContextSnapshotStage`：阶段枚举（装配、工具、裁剪、压缩）
- `ContextSnapshotEventPayload`：阶段事件载荷结构
- `ContextBudgetSummary`：预算摘要载荷
- `ContextTrimSummary`：裁剪摘要载荷
- `ContextCompressionSummary`：压缩摘要载荷
- `ContextSnapshotSummary`：快照摘要载荷
- `ContextPruneSummary`：裁剪摘要载荷
- `ContextDelta`：快照变化摘要

### 运行与接入链路
- `AgentRuntime`：运行主链路，驱动召回、构建、规划与执行
- `PlannerService`：规划入口，触发提示词装配阶段事件
- `ToolExecutor`：工具调用执行器，写入证据并发布阶段事件
- `ResearchPipeline`：研究引用生成与事件发布

## 关键配置项（读取自 `application.yml`）
### 上下文预算
| 配置项 | 默认值 | 影响 |
| --- | --- | --- |
| `agent.context.budget.enabled` | `true` | 控制是否启用预算分配与裁剪链路 |
| `agent.context.budget.total-budget-tokens` | `8192` | 上下文总预算上限 |
| `agent.context.budget.ratios.system-policy` | `0.05` | 系统策略分区预算比例 |
| `agent.context.budget.ratios.developer-policy` | `0.05` | 开发者策略分区预算比例 |
| `agent.context.budget.ratios.task-intent` | `0.40` | 任务意图分区预算比例 |
| `agent.context.budget.ratios.working-memory` | `0.20` | 工作记忆分区预算比例 |
| `agent.context.budget.ratios.domain-knowledge` | `0.10` | 领域知识分区预算比例 |
| `agent.context.budget.ratios.long-term-memory` | `0.10` | 长期记忆分区预算比例 |
| `agent.context.budget.ratios.tool-summaries` | `0.05` | 工具摘要分区预算比例 |
| `agent.context.budget.ratios.tool-schema` | `0.03` | 工具模式分区预算比例 |
| `agent.context.budget.ratios.evidence-pack` | `0.02` | 证据包分区预算比例 |
| `agent.context.budget.ratios.slack` | `0.0` | 余量分区预算比例 |
| `agent.context.budget.trim-order` | `evidence-pack > recalled-memories > working-memory > tool-summary > task-and-system` | 控制 `DefaultContextTrimmer` 的裁剪顺序 |

### 上下文压缩
| 配置项 | 默认值 | 影响 |
| --- | --- | --- |
| `agent.context.compression.enabled` | `true` | 是否允许 `ContextCompressionController` 触发压缩 |
| `agent.context.compression.trigger-over-total-budget` | `true` | 总预算超限时是否触发压缩 |
| `agent.context.compression.trigger-over-section-budget` | `true` | 分区预算超限时是否触发压缩 |
| `agent.context.compression.min-interval-seconds` | `30` | 压缩触发最小间隔，避免频繁压缩 |

### 提示词裁剪
- `application.yml` 未显式配置 `agent.prompt.trim.enabled`，代码默认值来自 `DefaultPromptAssembler` 的 `@Value("${agent.prompt.trim.enabled:true}")`
- 影响：关闭后不裁剪三段提示词，`truncatedSections` 为空

### 脱敏治理
| 配置项 | 默认值 | 影响 |
| --- | --- | --- |
| `agent.security.redaction.enabled` | `true` | 是否启用脱敏处理 |
| `agent.security.redaction.reject-on-secrets` | `true` | 命中密钥类规则时拒写 |
| `agent.security.redaction.redact-on-pii` | `true` | 命中个人信息时进行替换 |
| `agent.security.redaction.ignore-keys` | `[]` | 忽略字段列表 |
| `agent.security.redaction.max-scan-chars` | `8000` | 单次扫描最大字符数 |

### 工具审批
| 配置项 | 默认值 | 影响 |
| --- | --- | --- |
| `agent.tool.approval.enabled` | `false` | 是否启用高风险工具审批 |
| `agent.tool.approval.high-risk-tools` | `[]` | 高风险工具清单 |
| `agent.tool.approval.timeout-seconds` | `300` | 审批等待超时 |

### 预算阈值（与上下文预算联动）
| 配置项 | 默认值 | 影响 |
| --- | --- | --- |
| `agent.budget.enabled` | `true` | 是否启用预算计量与阈值控制 |
| `agent.budget.threshold-tokens` | `10000` | `TokenBudgetManager` 阈值，限制上下文总预算上限 |

## 关键事件与载荷字段
### 阶段枚举
- `ContextSnapshotStage`：`PLAN_ASSEMBLED`、`TOOL_OBSERVED`、`CONTEXT_TRIMMED`、`CONTEXT_COMPRESSED`

### 证据统计字段（`ContextSnapshotEventPayload`）
- `evidencePackPresent`
- `evidenceToolCallsCount`
- `evidenceMemoriesCount`
- `evidenceCitationsCount`
- `evidenceApproxChars`
- `evidencePackVersion`

### 裁剪摘要字段（`ContextTrimSummary`）
- `version`
- `beforeTokens`
- `afterTokens`
- `removedBySection`
- `reasons`

### 压缩摘要字段（`ContextCompressionSummary`）
- `triggerReason`
- `beforeTokens`
- `afterTokens`
- `durationMs`
- `summaryVersion`

## 回滚点（开关与回退方式）
- 上下文预算分配：`agent.context.budget.enabled=false`，预算分配与裁剪跳过，快照内容不按预算裁剪
- 上下文压缩：`agent.context.compression.enabled=false` 或关闭触发开关，压缩不执行，回退为仅裁剪
- 提示词裁剪：`agent.prompt.trim.enabled=false`，提示词不裁剪，`truncatedSections` 为空
- 脱敏治理：`agent.security.redaction.enabled=false`，不做脱敏与拒写，直接使用原文本
- 工具审批：`agent.tool.approval.enabled=false`，审批链路关闭，工具直接执行
- 记忆召回：`agent.memory.recall.enabled=false`，召回跳过，`WorkingMemory` 仅来自观察与运行时上下文
- 记忆写入：`agent.memory.write.enabled=false`，写入跳过，不落盘 `MemoryRecord`
- 预算阈值：`agent.budget.enabled=false`，不触发预算阈值事件，预算上限不被阈值压缩