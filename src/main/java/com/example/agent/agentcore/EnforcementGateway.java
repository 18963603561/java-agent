package com.example.agent.agentcore;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 执行网关，负责调用工具执行链路并发布事件。
 */
@Service
public class EnforcementGateway {

    private static final Logger log = LoggerFactory.getLogger(EnforcementGateway.class);

    private final ToolExecutor toolExecutor;
    private final ApplicationEventPublisher eventPublisher;

    public EnforcementGateway(ToolExecutor toolExecutor, ApplicationEventPublisher eventPublisher) {
        this.toolExecutor = toolExecutor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行任务请求。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     */
    public void execute(TaskRequest request, TenantContext tenantContext, String workflowId, String taskId,
                        AtomicLong seqCounter) {
        log.info("Tool execution started, tenantId={}, workflowId={}", tenantContext.getTenantId(), workflowId);
        long invokedSeq = nextSeq(seqCounter);
        String usageId = buildUsageId(taskId, invokedSeq);
        Map<String, Object> invokedPayload = new HashMap<>();
        invokedPayload.put("query", request.getQuery());
        invokedPayload.put("tokenUsage", buildTokenUsage(tenantContext, taskId, usageId));
        StreamEvent invoked = buildEvent(tenantContext, workflowId, EventType.TOOL_INVOKED, invokedSeq,
                invokedPayload);
        eventPublisher.publishEvent(invoked);

        Map<String, Object> result = toolExecutor.execute(request, tenantContext, usageId);
        ensureUsageContext(result, tenantContext, taskId, usageId);
        long observationSeq = nextSeq(seqCounter);
        StreamEvent observation = buildEvent(tenantContext, workflowId, EventType.TOOL_OBSERVATION, observationSeq,
                result);
        eventPublisher.publishEvent(observation);

        log.info("Tool execution completed, tenantId={}, workflowId={}", tenantContext.getTenantId(), workflowId);
    }

    private long nextSeq(AtomicLong seqCounter) {
        return seqCounter.incrementAndGet();
    }

    private String buildUsageId(String taskId, long seq) {
        if (taskId == null || taskId.isBlank()) {
            return "usage:" + seq;
        }
        return taskId + ":" + seq;
    }

    private Map<String, Object> buildTokenUsage(TenantContext tenantContext, String taskId, String usageId) {
        Map<String, Object> tokenUsage = new HashMap<>();
        tokenUsage.put("usageId", usageId);
        tokenUsage.put("tenantId", tenantContext.getTenantId());
        if (taskId != null) {
            tokenUsage.put("taskId", taskId);
        }
        tokenUsage.put("inputTokens", 0);
        tokenUsage.put("outputTokens", 0);
        tokenUsage.put("totalTokens", 0);
        tokenUsage.put("costUsd", 0);
        return tokenUsage;
    }

    private void ensureUsageContext(Map<String, Object> result, TenantContext tenantContext,
                                    String taskId, String usageId) {
        Object tokenUsage = result.get("tokenUsage");
        if (tokenUsage instanceof Map<?, ?> usageMap) {
            Map<Object, Object> mutable = new HashMap<>(usageMap);
            mutable.putIfAbsent("usageId", usageId);
            mutable.putIfAbsent("tenantId", tenantContext.getTenantId());
            if (taskId != null) {
                mutable.putIfAbsent("taskId", taskId);
            }
            result.put("tokenUsage", mutable);
        } else {
            result.put("tokenUsage", buildTokenUsage(tenantContext, taskId, usageId));
        }
    }

    private StreamEvent buildEvent(TenantContext tenantContext, String workflowId, EventType type, long seq,
                                   Map<String, Object> payload) {
        String streamId = workflowId;
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        return event;
    }
}
