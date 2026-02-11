package com.example.agent.orchestration.multiagent.event;

import com.example.agent.orchestration.multiagent.observability.MultiAgentEventKeys;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

/**
 * 事件发布公共支撑。
 *
 * <p>用途：统一处理事件序列号、通用载荷补全与最终发布动作，避免多域重复实现。</p>
 */
public class EventPublishSupport {

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;

    /**
     * 构造公共发布支撑。
     */
    public EventPublishSupport(ApplicationEventPublisher eventPublisher,
                               EventStreamService eventStreamService) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
    }

    /**
     * 发布单条流事件。
     */
    public void publishEvent(TenantContext tenantContext,
                             String workflowId,
                             AtomicLong seqCounter,
                             EventType type,
                             Map<String, Object> payload) {
        if (tenantContext == null || workflowId == null || type == null) {
            return;
        }
        long seq = resolveSequence(tenantContext, workflowId, seqCounter);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(enrichPayload(tenantContext, workflowId, payload));
        eventPublisher.publishEvent(event);
    }

    /**
     * 补充 DAG 回放锚点字段。
     */
    public void appendDagReplayAnchor(Map<String, Object> payload,
                                      String dagRunId,
                                      String nodeId,
                                      Integer attempt,
                                      String reasonCode,
                                      String messageId) {
        if (payload == null) {
            return;
        }
        if (StringUtils.hasText(dagRunId)) {
            payload.put(MultiAgentEventKeys.DAG_RUN_ID, dagRunId);
        }
        if (StringUtils.hasText(nodeId)) {
            payload.put(MultiAgentEventKeys.NODE_ID, nodeId);
        }
        if (attempt != null && attempt > 0) {
            payload.put(MultiAgentEventKeys.ATTEMPT, attempt);
        }
        if (StringUtils.hasText(reasonCode)) {
            payload.put(MultiAgentEventKeys.REASON_CODE, reasonCode);
        }
        if (StringUtils.hasText(messageId)) {
            payload.put(MultiAgentEventKeys.MESSAGE_ID, messageId);
        }
    }

    /**
     * 获取事件序列。
     */
    private long resolveSequence(TenantContext tenantContext,
                                 String workflowId,
                                 AtomicLong seqCounter) {
        if (seqCounter != null) {
            return seqCounter.incrementAndGet();
        }
        if (eventStreamService == null) {
            return System.nanoTime();
        }
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }

    /**
     * 追加通用上下文字段。
     */
    private Map<String, Object> enrichPayload(TenantContext tenantContext,
                                              String workflowId,
                                              Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? new HashMap<>() : new HashMap<>(payload);
        if (!safePayload.containsKey(MultiAgentEventKeys.WORKFLOW_ID)) {
            safePayload.put(MultiAgentEventKeys.WORKFLOW_ID, workflowId);
        }
        if (!safePayload.containsKey(MultiAgentEventKeys.TENANT_ID)
                && StringUtils.hasText(tenantContext.getTenantId())) {
            safePayload.put(MultiAgentEventKeys.TENANT_ID, tenantContext.getTenantId());
        }
        return safePayload;
    }
}
