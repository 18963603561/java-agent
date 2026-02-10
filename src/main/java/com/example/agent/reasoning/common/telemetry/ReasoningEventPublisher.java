package com.example.agent.reasoning.common.telemetry;

import com.example.agent.runtime.control.RuntimeControlEventPublisher;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理事件发布器。
 *
 * <p>用途：统一封装推理相关事件发布逻辑，避免策略实现重复拼装事件对象。
 */
@Component
public class ReasoningEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;

    /**
     * 构造推理事件发布器。
     *
     * @param eventPublisher Spring 事件发布器
     * @param eventStreamService 事件流序列服务
     */
    public ReasoningEventPublisher(ApplicationEventPublisher eventPublisher,
                                   EventStreamService eventStreamService) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
    }

    /**
     * 发布推理策略事件。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param eventType 事件类型
     * @param strategyType 推理策略类型
     * @param payload 业务载荷
     */
    public void publishStrategyEvent(TenantContext tenantContext,
                                     String workflowId,
                                     AtomicLong seqCounter,
                                     EventType eventType,
                                     String strategyType,
                                     Map<String, Object> payload) {
        if (tenantContext == null || !StringUtils.hasText(workflowId) || eventType == null) {
            return;
        }
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(eventType);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());

        Map<String, Object> mergedPayload = new HashMap<>();
        if (payload != null && !payload.isEmpty()) {
            mergedPayload.putAll(payload);
        }
        if (StringUtils.hasText(strategyType)) {
            mergedPayload.put("strategyType", strategyType);
        }
        mergedPayload.putIfAbsent("workflowId", workflowId);
        event.setPayload(mergedPayload);
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布运行时控制事件。
     *
     * @param runtimePublisher 运行时事件发布器
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param eventType 事件类型
     * @param strategyType 推理策略类型
     * @param payload 业务载荷
     */
    public void publishRuntimeEvent(RuntimeControlEventPublisher runtimePublisher,
                                    TenantContext tenantContext,
                                    String workflowId,
                                    AtomicLong seqCounter,
                                    EventType eventType,
                                    String strategyType,
                                    Map<String, Object> payload) {
        if (runtimePublisher == null || eventType == null) {
            return;
        }
        Map<String, Object> mergedPayload = new HashMap<>();
        if (payload != null && !payload.isEmpty()) {
            mergedPayload.putAll(payload);
        }
        if (StringUtils.hasText(strategyType)) {
            mergedPayload.put("strategyType", strategyType);
        }
        if (StringUtils.hasText(workflowId)) {
            mergedPayload.putIfAbsent("workflowId", workflowId);
        }
        runtimePublisher.publish(tenantContext, workflowId, seqCounter, eventType, mergedPayload);
    }
}

