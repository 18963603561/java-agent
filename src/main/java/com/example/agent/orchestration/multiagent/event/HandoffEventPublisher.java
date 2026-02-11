package com.example.agent.orchestration.multiagent.event;

import com.example.agent.orchestration.multiagent.handoff.HandoffRecord;
import com.example.agent.orchestration.multiagent.handoff.HandoffRequest;
import com.example.agent.orchestration.multiagent.observability.MultiAgentEventKeys;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handoff 领域事件发布器。
 *
 * <p>用途：聚合 handoff 相关事件载荷构建与发布，避免跨领域逻辑混杂。</p>
 */
public class HandoffEventPublisher {

    private final EventPublishSupport publishSupport;

    /**
     * 构造 handoff 事件发布器。
     */
    public HandoffEventPublisher(EventPublishSupport publishSupport) {
        this.publishSupport = publishSupport;
    }

    /**
     * 发布 handoff 请求事件。
     */
    public void publishHandoffRequested(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        HandoffRequest request,
                                        HandoffRecord record) {
        Map<String, Object> payload = new HashMap<>();
        appendHandoffRecordPayload(payload, record);
        if (request != null) {
            payload.put(MultiAgentEventKeys.FROM_AGENT, request.getFromAgent());
            payload.put(MultiAgentEventKeys.TO_AGENT, request.getToAgent());
            payload.put(MultiAgentEventKeys.CONTEXT, request.getContext());
            payload.put(MultiAgentEventKeys.IDEMPOTENCY_KEY, request.getIdempotencyKey());
        }
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.HANDOFF_REQUESTED,
                payload);
    }

    /**
     * 发布 handoff 完成事件。
     */
    public void publishHandoffCompleted(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        HandoffRequest request,
                                        HandoffRecord record,
                                        String reason) {
        Map<String, Object> payload = new HashMap<>();
        appendHandoffRecordPayload(payload, record);
        payload.put(MultiAgentEventKeys.STATUS,
                record != null && record.getStatus() != null ? record.getStatus().name() : null);
        payload.put(MultiAgentEventKeys.REASON, reason);
        if (request != null) {
            payload.put(MultiAgentEventKeys.FROM_AGENT, request.getFromAgent());
            payload.put(MultiAgentEventKeys.TO_AGENT, request.getToAgent());
            payload.put(MultiAgentEventKeys.IDEMPOTENCY_KEY, request.getIdempotencyKey());
        }
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.HANDOFF_COMPLETED,
                payload);
    }

    /**
     * 发布消息发送事件。
     */
    public void publishMessageSent(TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String fromRoleId,
                                   String toRoleId,
                                   String message,
                                   String topic) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(MultiAgentEventKeys.FROM_ROLE_ID, fromRoleId);
        payload.put(MultiAgentEventKeys.TO_ROLE_ID, toRoleId);
        payload.put(MultiAgentEventKeys.MESSAGE, message);
        payload.put(MultiAgentEventKeys.TOPIC, topic);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.MESSAGE_SENT,
                payload);
    }

    /**
     * 发布消息接收事件。
     */
    public void publishMessageReceived(TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter,
                                       String roleId,
                                       String message,
                                       String topic) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(MultiAgentEventKeys.ROLE_ID, roleId);
        payload.put(MultiAgentEventKeys.MESSAGE, message);
        payload.put(MultiAgentEventKeys.TOPIC, topic);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.MESSAGE_RECEIVED,
                payload);
    }

    /**
     * 发布工作区更新事件。
     */
    public void publishWorkspaceUpdated(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        String topic,
                                        String source) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(MultiAgentEventKeys.TOPIC, topic);
        payload.put(MultiAgentEventKeys.SOURCE, source);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.WORKSPACE_UPDATED,
                payload);
    }

    /**
     * 补充 handoff 记录核心字段。
     */
    private void appendHandoffRecordPayload(Map<String, Object> payload, HandoffRecord record) {
        if (payload == null || record == null) {
            return;
        }
        payload.put(MultiAgentEventKeys.HANDOFF_ID, record.getHandoffId());
        payload.put(MultiAgentEventKeys.VERSION, record.getVersion());
        payload.put(MultiAgentEventKeys.STATUS, record.getStatus() != null ? record.getStatus().name() : null);
        payload.put(MultiAgentEventKeys.ERROR_CODE, record.getErrorCode());
        payload.put(MultiAgentEventKeys.FAILURE_REASON, record.getFailureReason());
        payload.put(MultiAgentEventKeys.CREATED_AT,
                record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
        payload.put(MultiAgentEventKeys.UPDATED_AT,
                record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null);
    }
}
