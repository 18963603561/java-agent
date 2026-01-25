# 实现任务清单

## 1 里程碑定义
M1
- 端到端最小闭环可运行
- 覆盖端到端流程层最小链路
```
A1
A2
A3
A4
A5
A6
A7
```
- 提供可验证的接口、实时流、编排、执行、观测、记忆与反馈闭环
- 交付可运行演示与自动化验收脚本

M2
- 稳定性与治理增强
- 多智能体依赖编排与汇总
- 周期性任务与补偿增强
- 成本与速率感知治理
- 审计与时间线增强

M3
- 跨地域容灾与审计归集
- 跨组织协作与授权
- 长期记忆与知识库
- 策略与成本自优化

## 2 M1
任务清单

### M1-TASK-001
Task ID
```
M1-TASK-001
```
Title
- 搭建基础模块骨架与配置
Scope
- 包含模块目录、基础配置与构建可用性
- 不包含业务逻辑与外部接口
Modules
```
server
orchestrator
runtime
persistence
observability
governance
```
Data
无
Events
无
Acceptance Criteria
- 构建流程执行无失败
- 服务可启动并暴露基础健康检查
Dependencies
无

### M1-TASK-002
Task ID
```
M1-TASK-002
```
Title
- 建立任务生命周期接口
Scope
- 包含任务创建、查询、取消、暂停、恢复、重放接口
- 包含必要标识校验与租户隔离
- 不包含实时事件流订阅
Modules
```
server
orchestrator
persistence
```
Data
```
task_run
task_step
event_log
```
Events
```
WORKFLOW_STARTED
WORKFLOW_COMPLETED
ERROR_OCCURRED
PROGRESS
```
Acceptance Criteria
- 六类操作均可通过接口触发并返回确定结果
- 标识缺失或租户不一致的请求被拒绝
- 状态变更在存储中可查询
Dependencies
```
M1-TASK-001
M1-TASK-011
M1-TASK-017
```

### M1-TASK-003
Task ID
```
M1-TASK-003
```
Title
- 建立实时事件流订阅与断线续传
Scope
- 包含实时事件流订阅能力
```
SSE
```
- 包含断线续传与游标恢复
- 不包含订阅过滤与聚合
Modules
```
server
observability
persistence
```
Data
```
event_log
```
Events
```
WORKFLOW_STARTED
TOOL_INVOKED
ERROR_OCCURRED
PROGRESS
```
Acceptance Criteria
- 订阅后可按序接收事件
- 断线后可按游标续传且不丢不重
Dependencies
```
M1-TASK-001
M1-TASK-011
```

### M1-TASK-004
Task ID
```
M1-TASK-004
```
Title
- 落地任务状态机与幂等规则
Scope
- 包含状态流转、终态约束与幂等创建
- 不包含多智能体协作
Modules
```
orchestrator
persistence
```
Data
```
task_run
task_step
```
Events
```
WORKFLOW_STARTED
WORKFLOW_COMPLETED
ERROR_OCCURRED
```
Acceptance Criteria
- 非法状态流转被拒绝
- 终态不可逆规则可验证
- 幂等创建返回同一状态与结果
Dependencies
```
M1-TASK-001
M1-TASK-011
```

### M1-TASK-005
Task ID
```
M1-TASK-005
```
Title
- 建立战略层目标与约束最小处理
Scope
- 包含目标、成功标准、预算上限与优先级生成
- 包含决策摘要落库与审计
- 不包含多目标权衡
Modules
```
orchestrator
governance
persistence
```
Data
```
task_run
audit_log
event_log
```
Events
```
AGENT_THINKING
DATA_PROCESSING
PROGRESS
```
Acceptance Criteria
- 目标与约束摘要可查询
- 决策记录进入审计并可追溯
Dependencies
```
M1-TASK-002
M1-TASK-011
M1-TASK-013
```

### M1-TASK-006
Task ID
```
M1-TASK-006
```
Title
- 建立战术层任务拆解与依赖占位
Scope
- 包含最小任务拆解与依赖关系生成
- 不包含多智能体并行调度
Modules
```
orchestrator
persistence
```
Data
```
task_step
event_log
```
Events
```
PROGRESS
DEPENDENCY_SATISFIED
```
Acceptance Criteria
- 拆解后可查询子任务与依赖关系
- 依赖满足事件可被记录与回放
Dependencies
```
M1-TASK-004
M1-TASK-011
```

### M1-TASK-007
Task ID
```
M1-TASK-007
```
Title
- 建立推理层工具选择占位
Scope
- 包含工具候选选择与可行性记录
- 不包含多策略对比与复杂推理
Modules
```
orchestrator
runtime
persistence
```
Data
```
tool_registry
task_step
```
Events
```
AGENT_THINKING
DATA_PROCESSING
```
Acceptance Criteria
- 工具选择结果可查询与复测
- 无可用工具时产生失败事件
Dependencies
```
M1-TASK-006
M1-TASK-012
```

### M1-TASK-008
Task ID
```
M1-TASK-008
```
Title
- 建立行动计划生成与校验占位
Scope
- 包含步骤计划生成与最小校验
- 包含人工介入点占位
- 不包含计划优化与回滚
Modules
```
orchestrator
governance
persistence
```
Data
```
task_step
audit_log
```
Events
```
PROGRESS
APPROVAL_REQUESTED
APPROVAL_DECISION
```
Acceptance Criteria
- 计划步骤可查询且包含校验状态
- 触发人工介入时生成审批事件
Dependencies
```
M1-TASK-005
M1-TASK-006
M1-TASK-013
```

### M1-TASK-009
Task ID
```
M1-TASK-009
```
Title
- 建立工具注册与执行最小闭环
Scope
- 包含工具注册、发现与执行
- 包含统一结果语义
```
text
json
```
- 不包含多模态结果与签名
Modules
```
runtime
governance
persistence
```
Data
```
tool_registry
artifact
event_log
```
Events
```
TOOL_INVOKED
TOOL_OBSERVATION
ERROR_OCCURRED
```
Acceptance Criteria
- 工具注册后可被检索
- 执行结果包含必要元数据与错误分类
Dependencies
```
M1-TASK-012
M1-TASK-013
```

### M1-TASK-010
Task ID
```
M1-TASK-010
```
Title
- 接入长任务编排最小闭环
Scope
- 包含最小编排与重试超时策略
```
Temporal
```
- 包含重放语义与断点恢复占位
- 不包含补偿路径增强
Modules
```
orchestrator
runtime
persistence
```
Data
```
task_run
task_step
event_log
```
Events
```
ERROR_RECOVERY
ERROR_OCCURRED
PROGRESS
```
Acceptance Criteria
- 重试与超时触发可被验证
- 重放输出与历史一致
Dependencies
```
M1-TASK-004
M1-TASK-009
M1-TASK-011
```

### M1-TASK-011
Task ID
```
M1-TASK-011
```
Title
- 落库任务与事件核心表
Scope
- 包含任务与事件核心表结构与索引
- 不包含成本与配额表
Modules
```
persistence
```
Data
```
task_run
task_step
event_log
audit_log
```
Events
无
Acceptance Criteria
- 数据结构可创建并通过校验
- 索引可用于按任务与时间查询
Dependencies
```
M1-TASK-001
```

### M1-TASK-012
Task ID
```
M1-TASK-012
```
Title
- 落库工具与成本治理表
Scope
- 包含工具注册、配额与成本表结构
- 包含会话上下文快照表
- 不包含长期记忆索引表
Modules
```
persistence
```
Data
```
tool_registry
tenant_quota
cost_ledger
context_snapshot
```
Events
无
Acceptance Criteria
- 表结构与索引可创建并通过校验
- 版本字段与审计字段满足查询要求
Dependencies
```
M1-TASK-001
```

### M1-TASK-013
Task ID
```
M1-TASK-013
```
Title
- 实现最小预算与限流治理
Scope
- 包含租户维度限流
- 包含超预算阻断
- 不包含速率感知预算
Modules
```
governance
server
orchestrator
```
Data
```
tenant_quota
cost_ledger
audit_log
```
Events
```
ERROR_OCCURRED
PROGRESS
```
Acceptance Criteria
- 超预算请求被阻断并写入审计
- 超限请求被阻断且记录原因
Dependencies
```
M1-TASK-012
M1-TASK-002
```

### M1-TASK-014
Task ID
```
M1-TASK-014
```
Title
- 建立基础可观测与日志规范
Scope
- 包含日志字段规范与关键指标
- 不包含跨服务追踪聚合
Modules
```
observability
server
orchestrator
runtime
```
Data
```
event_log
```
Events
```
PROGRESS
ERROR_OCCURRED
TOOL_INVOKED
```
Acceptance Criteria
- 日志包含指定字段且可检索
- 成功率、延迟、预算超限、工具错误指标可观测
Dependencies
```
M1-TASK-011
M1-TASK-003
M1-TASK-009
```

### M1-TASK-015
Task ID
```
M1-TASK-015
```
Title
- 建立会话上下文快照与压缩占位
Scope
- 包含上下文快照写入与最小压缩策略
- 不包含长期记忆与语义检索
Modules
```
persistence
orchestrator
```
Data
```
context_snapshot
```
Events
```
WORKSPACE_UPDATED
DATA_PROCESSING
```
Acceptance Criteria
- 上下文快照可写入与查询
- 超阈值触发压缩且记录摘要
Dependencies
```
M1-TASK-012
M1-TASK-002
```

### M1-TASK-016
Task ID
```
M1-TASK-016
```
Title
- 建立最小反馈闭环
Scope
- 包含结果评估事件与归因字段
- 包含改进建议占位
- 不包含多轮反馈
Modules
```
orchestrator
observability
persistence
```
Data
```
event_log
audit_log
```
Events
```
DATA_PROCESSING
PROGRESS
```
Acceptance Criteria
- 任务完成后生成反馈事件
- 反馈事件包含归因与建议字段
Dependencies
```
M1-TASK-004
M1-TASK-014
```

### M1-TASK-017
Task ID
```
M1-TASK-017
```
Title
- 建立最小身份与多租户隔离
Scope
- 包含身份认证与租户隔离校验
```
JWT
API Key
```
- 不包含跨组织联合身份
Modules
```
server
governance
persistence
```
Data
```
audit_log
tenant_quota
```
Events
```
ERROR_OCCURRED
```
Acceptance Criteria
- 未认证请求被拒绝
- 跨租户访问被阻断并记录审计
Dependencies
```
M1-TASK-001
M1-TASK-011
```

## 3 M2
任务清单

### M2-TASK-001
Task ID
```
M2-TASK-001
```
Title
- 建立多智能体依赖编排与汇总
Scope
- 包含多智能体协作与依赖汇总
- 不包含跨组织协作
Modules
```
orchestrator
persistence
observability
```
Data
```
task_step
event_log
```
Events
```
DELEGATION
TEAM_RECRUITED
TEAM_STATUS
DEPENDENCY_SATISFIED
```
Acceptance Criteria
- 多智能体任务可并行执行并汇总
- 依赖满足事件可追溯
Dependencies
```
M1-TASK-006
M1-TASK-014
```

### M2-TASK-002
Task ID
```
M2-TASK-002
```
Title
- 建立周期性任务与调度
Scope
- 包含周期任务创建、启停与执行记录
- 不包含跨地域调度
Modules
```
orchestrator
persistence
governance
```
Data
```
schedule
task_run
```
Events
```
WORKFLOW_STARTED
PROGRESS
```
Acceptance Criteria
- 周期任务按计划触发并可查询
- 启停操作可追溯
Dependencies
```
M1-TASK-011
M1-TASK-002
```

### M2-TASK-003
Task ID
```
M2-TASK-003
```
Title
- 增强补偿与失败分类处理
Scope
- 包含失败分类驱动的补偿路径
- 不包含跨任务补偿
Modules
```
orchestrator
runtime
persistence
```
Data
```
task_step
event_log
```
Events
```
ERROR_OCCURRED
ERROR_RECOVERY
```
Acceptance Criteria
- 失败后补偿路径可触发并记录
- 补偿失败进入人工介入
Dependencies
```
M1-TASK-010
M1-TASK-009
```

### M2-TASK-004
Task ID
```
M2-TASK-004
```
Title
- 建立速率感知预算治理
Scope
- 包含速率感知预算决策
- 不包含策略自优化
Modules
```
governance
observability
persistence
```
Data
```
cost_ledger
tenant_quota
```
Events
```
DATA_PROCESSING
ERROR_OCCURRED
```
Acceptance Criteria
- 速率变化触发预算决策记录
- 决策结果进入审计与指标
Dependencies
```
M1-TASK-013
M1-TASK-014
```

### M2-TASK-005
Task ID
```
M2-TASK-005
```
Title
- 增强审计与时间线查询能力
Scope
- 包含审计查询、时间线筛选与回放
- 不包含跨地域审计归集
Modules
```
persistence
observability
server
```
Data
```
audit_log
event_log
```
Events
```
PROGRESS
DATA_PROCESSING
```
Acceptance Criteria
- 时间线查询支持条件筛选
- 审计记录可回放与导出
Dependencies
```
M1-TASK-011
M1-TASK-003
```

### M2-TASK-006
Task ID
```
M2-TASK-006
```
Title
- 建立工具版本与依赖治理
Scope
- 包含工具版本切换与冲突检测
- 不包含跨组织工具共享
Modules
```
runtime
governance
persistence
```
Data
```
tool_registry
policy
```
Events
```
PROGRESS
ERROR_OCCURRED
```
Acceptance Criteria
- 版本切换可回溯并可审计
- 冲突检测结果可复测
Dependencies
```
M1-TASK-009
```

### M2-TASK-007
Task ID
```
M2-TASK-007
```
Title
- 增强事件流订阅过滤与负载控制
Scope
- 包含按事件类型订阅与过滤
- 不包含跨地域流聚合
Modules
```
server
observability
persistence
```
Data
```
event_log
```
Events
```
PROGRESS
WAITING
```
Acceptance Criteria
- 过滤订阅可按类型生效
- 高负载下可维持顺序与延迟
Dependencies
```
M1-TASK-003
M1-TASK-014
```

### M2-TASK-008
Task ID
```
M2-TASK-008
```
Title
- 建立长期记忆与语义检索
Scope
- 包含长期记忆写入与检索
- 不包含跨地域同步
Modules
```
persistence
orchestrator
```
Data
```
memory_item
context_snapshot
```
Events
```
DATA_PROCESSING
WORKSPACE_UPDATED
```
Acceptance Criteria
- 长期记忆可写入与检索
- 版本管理可追溯
Dependencies
```
M1-TASK-015
```

### M2-TASK-009
Task ID
```
M2-TASK-009
```
Title
- 增强观测与追踪聚合能力
Scope
- 包含跨服务追踪关联与成本维度统计
- 不包含跨地域聚合
Modules
```
observability
```
Data
```
event_log
cost_ledger
```
Events
```
PROGRESS
ERROR_OCCURRED
```
Acceptance Criteria
- 追踪关联可跨服务查询
- 成本维度统计可按租户与工具聚合
Dependencies
```
M1-TASK-014
```

### M2-TASK-010
Task ID
```
M2-TASK-010
```
Title
- 建立协作消息与角色管理
Scope
- 包含协作消息与角色分配
- 不包含跨组织消息通道
Modules
```
orchestrator
persistence
```
Data
```
event_log
```
Events
```
MESSAGE_SENT
MESSAGE_RECEIVED
ROLE_ASSIGNED
TEAM_STATUS
```
Acceptance Criteria
- 协作消息可发送与回放
- 角色分配记录可追溯
Dependencies
```
M2-TASK-001
```

## 4 M3
任务清单

### M3-TASK-001
Task ID
```
M3-TASK-001
```
Title
- 建立跨地域容灾与恢复能力
Scope
- 包含跨地域故障切换与恢复演练
- 不包含跨组织协作
Modules
```
orchestrator
persistence
observability
```
Data
```
task_run
event_log
```
Events
```
ERROR_RECOVERY
PROGRESS
```
Acceptance Criteria
- 容灾演练中断时间达到目标
- 任务恢复一致性可验证
Dependencies
```
M2-TASK-003
M2-TASK-005
```

### M3-TASK-002
Task ID
```
M3-TASK-002
```
Title
- 建立跨组织协作与授权机制
Scope
- 包含跨组织授权与审计
- 不包含跨地域调度
Modules
```
server
governance
persistence
```
Data
```
audit_log
tenant_quota
```
Events
```
APPROVAL_REQUESTED
APPROVAL_DECISION
TEAM_STATUS
```
Acceptance Criteria
- 跨组织访问需授权并可审计
- 未授权访问被阻断
Dependencies
```
M1-TASK-017
M2-TASK-010
```

### M3-TASK-003
Task ID
```
M3-TASK-003
```
Title
- 建立长期记忆与知识库深化能力
Scope
- 包含知识库索引与跨任务复用
- 不包含跨组织共享
Modules
```
persistence
orchestrator
runtime
```
Data
```
memory_item
artifact
```
Events
```
DATA_PROCESSING
WORKSPACE_UPDATED
```
Acceptance Criteria
- 知识库可检索并支持版本追踪
- 复用命中可回放验证
Dependencies
```
M2-TASK-008
```

### M3-TASK-004
Task ID
```
M3-TASK-004
```
Title
- 建立策略自优化与成本自适应
Scope
- 包含基于成本与成功率的策略调整
- 不包含跨组织策略协商
Modules
```
governance
observability
```
Data
```
cost_ledger
policy
```
Events
```
DATA_PROCESSING
PROGRESS
```
Acceptance Criteria
- 策略调整可追溯并可回滚
- 成本阈值触发优化事件
Dependencies
```
M2-TASK-004
M2-TASK-009
```

### M3-TASK-005
Task ID
```
M3-TASK-005
```
Title
- 建立跨地域事件流聚合能力
Scope
- 包含跨地域事件流合并与顺序保证
- 不包含跨组织共享
Modules
```
observability
server
```
Data
```
event_log
```
Events
```
PROGRESS
WAITING
```
Acceptance Criteria
- 跨地域事件流可合并并保持顺序
- 断线续传可在聚合流中生效
Dependencies
```
M2-TASK-007
```

### M3-TASK-006
Task ID
```
M3-TASK-006
```
Title
- 建立跨组织工具共享与治理
Scope
- 包含工具共享授权与审计
- 不包含工具市场化运营
Modules
```
runtime
governance
persistence
```
Data
```
tool_registry
policy
audit_log
```
Events
```
APPROVAL_REQUESTED
APPROVAL_DECISION
```
Acceptance Criteria
- 跨组织共享需审批并可审计
- 未授权共享被阻断
Dependencies
```
M2-TASK-006
M3-TASK-002
```

### M3-TASK-007
Task ID
```
M3-TASK-007
```
Title
- 建立物理级隔离与专用资源策略
Scope
- 包含物理级隔离策略与配额实施
- 不包含跨地域容灾
Modules
```
governance
persistence
```
Data
```
tenant_quota
audit_log
```
Events
```
ERROR_OCCURRED
```
Acceptance Criteria
- 隔离策略可验证并可审计
- 违规访问被阻断
Dependencies
```
M3-TASK-002
```

### M3-TASK-008
Task ID
```
M3-TASK-008
```
Title
- 建立跨地域审计归集与统一视图
Scope
- 包含审计归集与统一检索视图
- 不包含跨组织合规协商
Modules
```
observability
persistence
```
Data
```
audit_log
event_log
```
Events
```
PROGRESS
```
Acceptance Criteria
- 归集后审计记录可统一检索
- 归集延迟满足目标要求
Dependencies
```
M2-TASK-005
```

## 5 并行与关键路径

并行建议
- 基础存储与表结构可并行
```
M1-TASK-011
M1-TASK-012
```
- 接口与事件流可并行
```
M1-TASK-002
M1-TASK-003
```
- 治理与安全可并行
```
M1-TASK-013
M1-TASK-017
```
- 推理与行动计划可并行
```
M1-TASK-007
M1-TASK-008
```
- 记忆与反馈可并行
```
M1-TASK-015
M1-TASK-016
```

关键路径
```
M1-TASK-001
M1-TASK-011
M1-TASK-004
M1-TASK-010
M1-TASK-009
M1-TASK-002
M1-TASK-003
M1-TASK-014
M1-TASK-016
```

推荐执行顺序
```
M1-TASK-001
M1-TASK-011
M1-TASK-012
M1-TASK-004
M1-TASK-013
M1-TASK-002
M1-TASK-003
M1-TASK-005
M1-TASK-006
M1-TASK-007
M1-TASK-008
M1-TASK-009
M1-TASK-010
M1-TASK-014
M1-TASK-015
M1-TASK-016
M1-TASK-017
```

## 6 覆盖矩阵

端到端流程层覆盖

A1
```
M1-TASK-002
M1-TASK-005
```
A2
```
M1-TASK-006
```
A3
```
M1-TASK-007
```
A4
```
M1-TASK-008
```
A5
```
M1-TASK-009
M1-TASK-010
```
A6
```
M1-TASK-016
```
A7
```
M1-TASK-015
```

能力域覆盖

任务编排与生命周期
```
M1-TASK-002
M1-TASK-004
M1-TASK-005
M1-TASK-006
M1-TASK-008
M1-TASK-010
M1-TASK-016
```
工具执行与结果规范
```
M1-TASK-007
M1-TASK-009
```
长任务与恢复
```
M1-TASK-010
```
上下文与记忆
```
M1-TASK-015
```
可观测性
```
M1-TASK-003
M1-TASK-014
```
安全与治理
```
M1-TASK-013
M1-TASK-017
```
对外接口与集成
```
M1-TASK-002
M1-TASK-003
```
