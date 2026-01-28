package com.example.agent.domain.event;

/**
 * 事件类型枚举，定义系统内事件语义。
 */
public enum EventType {
    // 工作流启动事件
    WORKFLOW_STARTED,
    // 工作流完成事件
    WORKFLOW_COMPLETED,
    // 工作流暂停事件
    WORKFLOW_PAUSED,
    // 工作流恢复事件
    WORKFLOW_RESUMED,
    // 工作流取消事件
    WORKFLOW_CANCELLED,
    // 智能体启动事件
    AGENT_STARTED,
    // 智能体完成事件
    AGENT_COMPLETED,
    // 错误发生事件
    ERROR_OCCURRED,
    // 大模型提示词发送事件
    LLM_PROMPT,
    // 大模型流式部分输出事件
    LLM_PARTIAL,
    // 大模型输出完成事件
    LLM_OUTPUT,
    // 工具调用开始事件
    TOOL_INVOKED,
    // 工具观测记录事件
    TOOL_OBSERVATION,
    // 工具调用异常事件
    TOOL_ERROR,
    // 步骤开始事件
    STEP_STARTED,
    // 步骤完成事件
    STEP_COMPLETED,
    // 步骤失败事件
    STEP_FAILED,
    // 步骤跳过事件
    STEP_SKIPPED,
    // 步骤等待事件
    STEP_WAITING,
    // 规划生成事件
    PLAN_GENERATED,
    // 规划修订事件
    PLAN_REVISED,
    // 反思开始事件
    REFLECTION_STARTED,
    // 反思完成事件
    REFLECTION_COMPLETED,
    // 工具前置钩子事件
    HOOK_PRE_TOOL,
    // 工具后置钩子事件
    HOOK_POST_TOOL,
    // 步骤前置钩子事件
    HOOK_PRE_STEP,
    // 步骤后置钩子事件
    HOOK_POST_STEP,
    // 交接请求事件
    HANDOFF_REQUESTED,
    // 交接完成事件
    HANDOFF_COMPLETED,
    // 思维展开事件
    THOUGHT_EXPANDED,
    // 辩论轮次完成事件
    DEBATE_ROUND_COMPLETED,
    // 研究来源新增事件
    RESEARCH_SOURCE_ADDED,
    // 链式思考开始事件
    COT_STARTED,
    // 链式思考步骤事件
    COT_STEP,
    // 链式思考完成事件
    COT_COMPLETED,
    // 链式思考中止事件
    COT_STOPPED,
    // 能力评估开始事件
    CAPABILITY_EVAL_STARTED,
    // 能力评估完成事件
    CAPABILITY_EVAL_COMPLETED,
    // 能力评估风险提示事件
    CAPABILITY_EVAL_RISK_RAISED,
    // 委派事件
    DELEGATION,
    // 团队招募事件
    TEAM_RECRUITED,
    // 团队退役事件
    TEAM_RETIRED,
    // 消息发送事件
    MESSAGE_SENT,
    // 消息接收事件
    MESSAGE_RECEIVED,
    // 角色分配事件
    ROLE_ASSIGNED,
    // 进度更新事件
    PROGRESS,
    // 数据处理事件
    DATA_PROCESSING,
    // 等待事件
    WAITING,
    // 团队状态事件
    TEAM_STATUS,
    // 工作区更新事件
    WORKSPACE_UPDATED,
    // 审批请求事件
    APPROVAL_REQUESTED,
    // 审批决策事件
    APPROVAL_DECISION,
    // 记忆保存事件
    MEMORY_SAVED,
    // 调度触发事件
    SCHEDULE_TRIGGERED,
    // 认证失败事件
    AUTH_FAILED,
    // 预算阈值触发事件
    BUDGET_THRESHOLD,
    // 回放开始事件
    REPLAY_STARTED,
    // 回放完成事件
    REPLAY_COMPLETED,
    // 背压触发事件
    BACKPRESSURE_APPLIED,
    // 熔断开启事件
    CIRCUIT_OPENED,
    // 策略拒绝事件
    POLICY_DENIED,
    // 沙箱违规事件
    SANDBOX_VIOLATION,
    // 模型回退生效事件
    MODEL_FALLBACK_APPLIED,
    // 思考开始事件
    THINK_STARTED,
    // 思考完成事件
    THINK_COMPLETED,
    // 行动开始事件
    ACT_STARTED,
    // 行动完成事件
    ACT_COMPLETED,
    // 观察记录事件
    OBSERVE_RECORDED,
    // 反思行动循环开始事件
    REACT_ITERATION_STARTED,
    // 反思行动循环完成事件
    REACT_ITERATION_COMPLETED,
    // 反思行动循环停止事件
    REACT_STOPPED,
    // 上下文快照创建事件
    CONTEXT_SNAPSHOT_CREATED,
    // 上下文裁剪事件
    CONTEXT_PRUNED,
    // 上下文快照阶段事件
    CONTEXT_SNAPSHOT_STAGE
}
