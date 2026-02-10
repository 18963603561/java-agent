package com.example.agent.orchestration.multiagent;

import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 多智能体事件发布器。
 * <p>用途：集中处理团队编排相关事件发布，隔离协调器中的事件样板代码。
 */
@Component
public class MultiAgentEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;

    public MultiAgentEventPublisher(ApplicationEventPublisher eventPublisher,
                                    EventStreamService eventStreamService) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
    }

    /**
     * 发布团队事件。
     */
    public void publishTeamEvents(com.example.agent.security.auth.TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  List<AgentRole> roles) {
        publishEvent(tenantContext, workflowId, seqCounter, EventType.TEAM_RECRUITED,
                Map.of("teamSize", roles.size()));
        for (AgentRole role : roles) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("roleId", role.getRoleId());
            payload.put("name", role.getName());
            payload.put("description", role.getDescription());
            if (role.getModelId() != null) {
                payload.put("modelId", role.getModelId());
            }
            publishEvent(tenantContext, workflowId, seqCounter, EventType.ROLE_ASSIGNED, payload);
        }
    }

    private void publishEvent(com.example.agent.security.auth.TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        if (tenantContext == null || workflowId == null) {
            return;
        }
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }
}

