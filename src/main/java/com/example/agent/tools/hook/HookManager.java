package com.example.agent.tools.hook;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.runtime.StepRecord;
import com.example.agent.streaming.EventStreamService;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Hook 管理器，负责 pre/post tool 与 pre/post step 的决策与记录。
 */
@Component
public class HookManager {

    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private final HookProperties hookProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final MetricsPublisher metricsPublisher;

    private static final int MAX_RECORDS = 1000;

    private final Deque<HookRecord> records = new ArrayDeque<>();
    private final Object recordLock = new Object();

    public HookManager(HookProperties hookProperties,
                       ApplicationEventPublisher eventPublisher,
                       EventStreamService eventStreamService,
                       MetricsPublisher metricsPublisher) {
        this.hookProperties = hookProperties;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
    }

    public void preTool(TenantContext tenantContext, StepRecord stepRecord, String toolName) {
        HookDecision decision = evaluate(toolName);
        recordDecision(HookType.PRE_TOOL, tenantContext, stepRecord, toolName, decision);
        if (!decision.isAllowed()) {
            metricsPublisher.increment("hook.block.count");
            log.warn("HOOK_BLOCKED, tenantId={}, userId={}, traceId={}, requestId={}, toolName={}, reason={}",
                    tenantContext.getTenantId(),
                    tenantContext.getUserId(),
                    tenantContext.getTraceId(),
                    tenantContext.getRequestId(),
                    toolName,
                    decision.getReason());
            throw new ErrorCodeException(HttpStatus.CONFLICT, "HOOK_BLOCKED", decision.getReason());
        }
        publishHookEvent(EventType.HOOK_PRE_TOOL, tenantContext, stepRecord, toolName, decision);
    }

    public void postTool(TenantContext tenantContext, StepRecord stepRecord, String toolName,
                         Map<String, Object> result) {
        HookDecision decision = new HookDecision(true, "ok",
                result == null ? Collections.emptyMap() : result);
        recordDecision(HookType.POST_TOOL, tenantContext, stepRecord, toolName, decision);
        publishHookEvent(EventType.HOOK_POST_TOOL, tenantContext, stepRecord, toolName, decision);
    }

    public void preStep(TenantContext tenantContext, StepRecord stepRecord) {
        HookDecision decision = new HookDecision(true, "ok", Collections.emptyMap());
        recordDecision(HookType.PRE_STEP, tenantContext, stepRecord, null, decision);
        publishHookEvent(EventType.HOOK_PRE_STEP, tenantContext, stepRecord, null, decision);
    }

    public void postStep(TenantContext tenantContext, StepRecord stepRecord) {
        HookDecision decision = new HookDecision(true, "ok", Collections.emptyMap());
        recordDecision(HookType.POST_STEP, tenantContext, stepRecord, null, decision);
        publishHookEvent(EventType.HOOK_POST_STEP, tenantContext, stepRecord, null, decision);
    }

    private HookDecision evaluate(String toolName) {
        if (!hookProperties.isEnabled()) {
            return new HookDecision(true, "disabled", Collections.emptyMap());
        }
        if (toolName != null && hookProperties.getBlockedTools().stream()
                .anyMatch(blocked -> blocked.equalsIgnoreCase(toolName))) {
            return new HookDecision(false, "工具被 Hook 阻断", Map.of("toolName", toolName));
        }
        return new HookDecision(true, "ok", Collections.emptyMap());
    }

    private void recordDecision(HookType type,
                                TenantContext tenantContext,
                                StepRecord stepRecord,
                                String toolName,
                                HookDecision decision) {
        HookRecord record = new HookRecord();
        record.setHookId(UUID.randomUUID().toString());
        record.setHookType(type);
        record.setToolName(toolName);
        record.setStepId(stepRecord != null ? stepRecord.getStepId() : null);
        record.setTenantId(tenantContext.getTenantId());
        record.setAllowed(decision.isAllowed());
        record.setReason(decision.getReason());
        record.setExecutedAt(Instant.now());
        synchronized (recordLock) {
            if (records.size() >= MAX_RECORDS) {
                records.removeFirst();
            }
            records.addLast(record);
        }
        log.info("Hook 执行, tenantId={}, type={}, tool={}, allowed={}",
                tenantContext.getTenantId(), type, toolName, decision.isAllowed());
    }

    private void publishHookEvent(EventType type,
                                  TenantContext tenantContext,
                                  StepRecord stepRecord,
                                  String toolName,
                                  HookDecision decision) {
        if (stepRecord == null) {
            return;
        }
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), stepRecord.getWorkflowId());
        StreamEvent event = new StreamEvent();
        event.setEventId(stepRecord.getWorkflowId() + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(stepRecord.getWorkflowId());
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(stepRecord.getWorkflowId());
        event.setTenantId(tenantContext.getTenantId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("stepId", stepRecord.getStepId());
        if (toolName != null) {
            payload.put("toolName", toolName);
        }
        payload.put("allowed", decision.isAllowed());
        payload.put("reason", decision.getReason());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }
}
