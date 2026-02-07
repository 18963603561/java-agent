package com.example.agent.runtime.engine;

import com.example.agent.planning.PlanResult;
import com.example.agent.runtime.control.RuntimeControlEventPublisher;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.TracingPublisher;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 运行时事件分发服务。
 *
 * <p>用途：统一封装规划事件与通用运行时事件发布逻辑，避免门面类直接操作事件构建细节。
 * <p>输入：租户上下文、工作流标识、序列计数器、事件类型与事件载荷。
 * <p>输出：无。
 * <p>边界：当规划结果为空时忽略规划事件；当上下文缺失时自动使用 tracing 兜底 traceId。
 */
@Service
public class RuntimeEventDispatchService implements RuntimeControlEventPublisher {

    /**
     * Spring 事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 链路追踪发布器。
     */
    private final TracingPublisher tracingPublisher;

    public RuntimeEventDispatchService(ApplicationEventPublisher eventPublisher,
                                       TracingPublisher tracingPublisher) {
        this.eventPublisher = eventPublisher;
        this.tracingPublisher = tracingPublisher;
    }

    /**
     * 发布规划相关事件。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param plan 规划结果
     * @param type 事件类型
     */
    public void publishPlanEvent(TenantContext tenantContext,
                                 String workflowId,
                                 AtomicLong seqCounter,
                                 PlanResult plan,
                                 EventType type) {
        if (plan == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (plan.getPlanId() != null) {
            payload.put("planId", plan.getPlanId());
        }
        if (plan.getSummary() != null) {
            payload.put("summary", plan.getSummary());
        }
        payload.put("steps", plan.getSteps() != null ? plan.getSteps().size() : 0);
        publish(tenantContext, workflowId, seqCounter, type, payload);
    }

    /**
     * 发布通用运行时事件。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param type 事件类型
     * @param payload 事件载荷
     */
    @Override
    public void publish(TenantContext tenantContext,
                        String workflowId,
                        AtomicLong seqCounter,
                        EventType type,
                        Map<String, Object> payload) {
        long seq = seqCounter.incrementAndGet();
        StreamEvent event = new StreamEvent();
        Map<String, Object> mutable = payload == null ? new HashMap<>() : new HashMap<>(payload);
        attachTraceContext(mutable, tenantContext);
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext != null ? tenantContext.getTenantId() : null);
        event.setPayload(mutable);
        eventPublisher.publishEvent(event);
    }

    /**
     * 将链路标识写入事件载荷。
     *
     * @param payload 事件载荷
     * @param tenantContext 租户上下文
     */
    void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", resolveTraceId(tenantContext));
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

    /**
     * 解析 traceId。
     *
     * @param tenantContext 租户上下文
     * @return traceId
     */
    String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && tenantContext.getTraceId() != null
                && !tenantContext.getTraceId().isBlank()) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }
}
