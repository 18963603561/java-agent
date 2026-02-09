package com.example.agent.budget.token.event;

import com.example.agent.capabilities.llm.provider.ModelFallbackDecision;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 默认预算事件发布器，统一封装事件模型构造与发布流程。
 */
@Component
public class DefaultBudgetEventPublisher implements BudgetEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;

    public DefaultBudgetEventPublisher(ApplicationEventPublisher eventPublisher,
                                       EventStreamService eventStreamService) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
    }

    @Override
    public void publishThresholdEvent(TenantContext tenantContext, String taskId, int totalTokens) {
        String streamId = taskId != null ? taskId : tenantContext.getTenantId();
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), streamId);
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(streamId);
        event.setType(EventType.BUDGET_THRESHOLD);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of("taskId", taskId, "totalTokens", totalTokens));
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishFallbackEvent(TenantContext tenantContext, ModelFallbackDecision decision) {
        String streamId = decision.getTaskId() != null ? decision.getTaskId() : tenantContext.getTenantId();
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), streamId);
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(streamId);
        event.setType(EventType.MODEL_FALLBACK_APPLIED);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of(
                "taskId", decision.getTaskId(),
                "fromModel", decision.getFromModel(),
                "toModel", decision.getToModel(),
                "reason", decision.getReason()
        ));
        eventPublisher.publishEvent(event);
    }
}
