package com.example.agent.runtime.step;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 步骤运行时事件服务。
 *
 * <p>用途：统一处理步骤序列号分配、traceId 解析与运行时事件发布。
 * <p>输入：租户上下文、工作流标识、步骤记录与事件载荷。
 * <p>输出：规范化后的运行时事件。
 * <p>边界：该服务只负责事件层逻辑，不处理步骤状态机与结果装配。
 */
@Component
public class StepRuntimeEventService {

    /**
     * Spring 事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * SSE 序列号服务。
     */
    private final EventStreamService eventStreamService;

    /**
     * 链路追踪发布器。
     */
    private final TracingPublisher tracingPublisher;

    public StepRuntimeEventService(ApplicationEventPublisher eventPublisher,
                                   EventStreamService eventStreamService,
                                   TracingPublisher tracingPublisher) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.tracingPublisher = tracingPublisher;
    }

    /**
     * 生成下一个事件序列号。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 可选内存计数器
     * @return 事件序列号
     */
    public long nextSequence(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            return seqCounter.incrementAndGet();
        }
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }

    /**
     * 发布步骤运行时事件。
     *
     * @param type 事件类型
     * @param record 步骤记录
     * @param seq 事件序列号
     * @param payload 事件载荷
     * @param tenantContext 租户上下文
     */
    public void publish(EventType type,
                        StepRecord record,
                        long seq,
                        Map<String, Object> payload,
                        TenantContext tenantContext) {
        StreamEvent event = new StreamEvent();
        String streamId = record.getWorkflowId();
        Map<String, Object> mutable = payload == null ? new HashMap<>() : new HashMap<>(payload);
        mutable.putIfAbsent("traceId", resolveTraceId(tenantContext));
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(record.getWorkflowId());
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(record.getTenantId());
        event.setPayload(mutable);
        eventPublisher.publishEvent(event);
    }

    /**
     * 解析链路追踪标识。
     *
     * @param tenantContext 租户上下文
     * @return traceId
     */
    public String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && tenantContext.getTraceId() != null
                && !tenantContext.getTraceId().isBlank()) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }
}

