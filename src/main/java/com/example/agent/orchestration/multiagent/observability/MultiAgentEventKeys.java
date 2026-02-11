package com.example.agent.orchestration.multiagent.observability;

/**
 * 多智能体事件载荷键字典。
 * <p>用途：统一事件 payload 字段键，避免跨发布器硬编码与命名漂移。</p>
 */
public final class MultiAgentEventKeys {

    public static final String WORKFLOW_ID = "workflowId";
    public static final String TENANT_ID = "tenantId";
    public static final String DAG_RUN_ID = "dagRunId";
    public static final String NODE_ID = "nodeId";
    public static final String ROLE_ID = "roleId";
    public static final String REASON = "reason";
    public static final String REASON_CODE = "reasonCode";
    public static final String MESSAGE_ID = "messageId";
    public static final String ATTEMPT = "attempt";
    public static final String DELAY_MS = "delayMs";
    public static final String WAITED_MS = "waitedMs";
    public static final String QUEUE_SIZE = "queueSize";
    public static final String CAPACITY = "capacity";
    public static final String PRODUCED_TOPICS = "producedTopics";

    public static final String TEAM_SIZE = "teamSize";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String MODEL_ID = "modelId";
    public static final String STATUS = "status";

    public static final String HANDOFF_ID = "handoffId";
    public static final String VERSION = "version";
    public static final String ERROR_CODE = "errorCode";
    public static final String FAILURE_REASON = "failureReason";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String FROM_AGENT = "fromAgent";
    public static final String TO_AGENT = "toAgent";
    public static final String CONTEXT = "context";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String FROM_ROLE_ID = "fromRoleId";
    public static final String TO_ROLE_ID = "toRoleId";
    public static final String MESSAGE = "message";
    public static final String TOPIC = "topic";
    public static final String SOURCE = "source";

    private MultiAgentEventKeys() {
    }
}

