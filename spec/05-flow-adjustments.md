# 流程图对齐调整建议

## 范围与目标
- 对齐需求规格中的七个能力域，确保流程图覆盖任务编排、工具执行、长任务治理、上下文记忆、可观测性、安全治理与对外接口
- 结合生产级系统关注点补齐治理与运行形态，避免仅停留在认知与规划视角
- 本文件仅描述调整点，不包含实现细节

## 总体结论
- 现有流程覆盖感知、拆解、执行与反馈闭环，但缺少生产系统所需的治理与可观测闭环
- 任务生命周期、事件与时间线、审计回放、预算与限流、多租户隔离在流程中缺位
- 工具运行时的注册治理、参数校验、资源隔离与统一结果语义未在流程中体现
- 强化学习与世界模型训练不在本期范围，应从主流程移出

## 调整清单

### 1 任务编排与生命周期
M1
- 在任务拆解与任务发布之间补充任务生命周期状态机与控制信号，覆盖创建、暂停、恢复、取消、超时与重放
- 在任务完成判断前增加终态规则与失败分类，明确失败、取消、超时均为终态且不可逆
- 增加事件流与任务历史生成节点，并与状态流转同步，用于时间线回放与审计
- 增加周期性任务入口，并与任务发布阶段汇合
M2
- 增加策略路由与模板化编排节点，用于多智能体策略选择与流程复用
M3
- 增加跨组织协作编排节点，标注为未来能力

### 2 工具执行与结果规范
M1
- 在动作执行之前补充工具注册、发现与版本治理输入
- 在执行前加入参数校验、权限校验与内容安全校验关口，校验失败直接终止
- 在执行环节标出资源限制、隔离与超时终止，避免工具滥用与资源耗尽
- 在结果输出补充统一结果语义与产物审计记录，确保结果可追溯
M2
- 增加工具级成本与异常统计节点
M3
- 增加跨组织工具共享与授权节点

### 3 长任务、重试、补偿
M1
- 在规划与执行循环中补充幂等键、重试上限与超时边界，明确失败恢复语义
- 增加补偿路径与人工介入触发点，确保长任务可恢复
- 标注状态以事件历史为唯一事实来源，回放输出与历史一致
M2
- 增加按失败类型选择补偿路径节点
M3
- 增加跨地域恢复与容灾节点

### 4 上下文与记忆
M1
- 补充会话上下文生命周期与压缩路径，与预算控制联动
- 增加上下文清理与隐私隔离节点，覆盖删除请求与隔离约束
M2
- 增加长期记忆与语义检索分支，标注为扩展能力
M3
- 增加跨地域同步与一致性节点

### 5 可观测性
M1
- 在关键节点补充日志、指标、追踪与成本事件采集点
- 增加实时事件流与历史审计双轨持久化节点，支撑回放与取证
- 增加告警与服务水平目标闭环，关联成功率与延迟目标
M2
- 增加多维成本统计与查询节点
M3
- 增加跨地域审计归集节点

### 6 安全与治理
M1
- 在用户输入后增加身份鉴别与租户上下文注入节点
- 在任务发布与工具执行之前增加策略评估与审批节点，失败直接阻断
- 增加默认拒绝外网访问与安全沙箱隔离标注
- 在存储、记忆与事件路径标注租户隔离与审计记录
M2
- 增加策略灰度发布与回滚节点
M3
- 增加跨组织身份联合与协商节点

### 7 对外接口与集成
M1
- 在结果输出并列增加实时流接口与断线续传能力
- 增加任务历史与时间线查询接口节点
- 增加周期性任务管理接口节点
M2
- 增加批量查询与筛选接口节点
M3
- 增加跨组织管理视图节点

## 需要从主流程移出的内容
- 将强化学习与世界模型训练从主流程移出，标注为未来研究或离线能力
- 将奖励信号限定为离线评估与成本分析输入，不进入生产执行闭环

## 参考文件
```
spec/02-requirements.md
vendor/Shannon/README.md
vendor/Shannon/ROADMAP.md
vendor/Shannon/docs/agent-core-architecture.md
vendor/Shannon/docs/multi-agent-workflow-architecture.md
vendor/Shannon/docs/streaming-api.md
vendor/Shannon/docs/event-types.md
vendor/Shannon/docs/task-history-and-timeline.md
vendor/Shannon/docs/memory-system-architecture.md
vendor/Shannon/docs/context-window-management.md
vendor/Shannon/docs/token-budget-tracking.md
vendor/Shannon/docs/rate-aware-budgeting.md
vendor/Shannon/docs/authentication-and-multitenancy.md
vendor/Shannon/docs/scheduled-tasks.md
vendor/Shannon/docs/control-signals.md
vendor/ai-agent-book/zh/Part7-生产架构/第20章：三层架构设计.md
vendor/ai-agent-book/zh/Part7-生产架构/第21章：Temporal工作流.md
vendor/ai-agent-book/zh/Part7-生产架构/第22章：可观测性.md
vendor/ai-agent-book/zh/Part8-企业级特性/第23章：Token预算控制.md
vendor/ai-agent-book/zh/Part8-企业级特性/第24章：策略治理.md
vendor/ai-agent-book/zh/Part8-企业级特性/第25章：安全执行.md
vendor/ai-agent-book/zh/Part8-企业级特性/第26章：多租户设计.md
```
