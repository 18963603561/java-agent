package com.example.agent.orchestration.multiagent.event;

import com.example.agent.orchestration.multiagent.handoff.HandoffRecord;
import com.example.agent.orchestration.multiagent.handoff.HandoffRequest;
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
            payload.put("fromAgent", request.getFromAgent());
            payload.put("toAgent", request.getToAgent());
            payload.put("context", request.getContext());
            payload.put("idempotencyKey", request.getIdempotencyKey());
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
        payload.put("status", record != null && record.getStatus() != null ? record.getStatus().name() : null);
        payload.put("reason", reason);
        if (request != null) {
            payload.put("fromAgent", request.getFromAgent());
            payload.put("toAgent", request.getToAgent());
            payload.put("idempotencyKey", request.getIdempotencyKey());
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
        payload.put("fromRoleId", fromRoleId);
        payload.put("toRoleId", toRoleId);
        payload.put("message", message);
        payload.put("topic", topic);
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
        payload.put("roleId", roleId);
        payload.put("message", message);
        payload.put("topic", topic);
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
        payload.put("topic", topic);
        payload.put("source", source);
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
        payload.put("handoffId", record.getHandoffId());
        payload.put("version", record.getVersion());
        payload.put("status", record.getStatus() != null ? record.getStatus().name() : null);
        payload.put("errorCode", record.getErrorCode());
        payload.put("failureReason", record.getFailureReason());
        payload.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
        payload.put("updatedAt", record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null);
    }
}

