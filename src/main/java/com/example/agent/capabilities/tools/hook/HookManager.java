package com.example.agent.capabilities.tools.hook;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.runtime.engine.StepRecord;
import com.example.agent.streaming.sse.EventStreamService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private final List<HookHandler> hookHandlers;
    private final ExecutorService hookExecutor = Executors.newCachedThreadPool();

    private static final int MAX_RECORDS = 1000;

    private final Deque<HookRecord> records = new ArrayDeque<>();
    private final Object recordLock = new Object();

    public HookManager(HookProperties hookProperties,
                       ApplicationEventPublisher eventPublisher,
                       EventStreamService eventStreamService,
                       MetricsPublisher metricsPublisher,
                       List<HookHandler> hookHandlers) {
        this.hookProperties = hookProperties;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
        this.hookHandlers = hookHandlers == null ? List.of() : new ArrayList<>(hookHandlers);
    }

    public void preTool(TenantContext tenantContext, StepRecord stepRecord, String toolName) {
        HookDecision decision = executeHooks(HookType.PRE_TOOL, tenantContext, stepRecord, toolName,
                buildPayload(stepRecord, null), true);
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
        publishHookEvent(EventType.HOOK_PRE_TOOL, tenantContext, stepRecord, toolName, decision, null);
    }

    public void postTool(TenantContext tenantContext, StepRecord stepRecord, String toolName,
                         Map<String, Object> result) {
        HookDecision decision = executeHooks(HookType.POST_TOOL, tenantContext, stepRecord, toolName,
                buildPayload(stepRecord, result), false);
        publishHookEvent(EventType.HOOK_POST_TOOL, tenantContext, stepRecord, toolName, decision, result);
    }

    public void preStep(TenantContext tenantContext, StepRecord stepRecord) {
        HookDecision decision = executeHooks(HookType.PRE_STEP, tenantContext, stepRecord, null,
                buildPayload(stepRecord, null), true);
        publishHookEvent(EventType.HOOK_PRE_STEP, tenantContext, stepRecord, null, decision, null);
    }

    public void postStep(TenantContext tenantContext, StepRecord stepRecord) {
        HookDecision decision = executeHooks(HookType.POST_STEP, tenantContext, stepRecord, null,
                buildPayload(stepRecord, null), false);
        publishHookEvent(EventType.HOOK_POST_STEP, tenantContext, stepRecord, null, decision, null);
    }

    /**
     * 记忆召回后置 Hook。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param payload 召回结果摘要
     */
    public void postRecall(TenantContext tenantContext, String workflowId, Map<String, Object> payload) {
        HookDecision decision = executeHooks(HookType.POST_RECALL, tenantContext, null, null,
                payload == null ? Collections.emptyMap() : new HashMap<>(payload), false);
        publishHookEvent(EventType.HOOK_POST_RECALL, tenantContext, null, null, decision, payload, workflowId);
    }

    /**
     * 研究引用后置 Hook。
     *
     * @param tenantContext 租户上下文
     * @param stepRecord 步骤记录
     * @param payload 研究结果摘要
     */
    public void postResearch(TenantContext tenantContext, StepRecord stepRecord, Map<String, Object> payload) {
        HookDecision decision = executeHooks(HookType.POST_RESEARCH, tenantContext, stepRecord, null,
                payload == null ? Collections.emptyMap() : new HashMap<>(payload), false);
        publishHookEvent(EventType.HOOK_POST_RESEARCH, tenantContext, stepRecord, null, decision, payload);
    }

    /**
     * 上下文裁剪后置 Hook。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param payload 裁剪摘要
     */
    public void postTrim(TenantContext tenantContext, String workflowId, Map<String, Object> payload) {
        HookDecision decision = executeHooks(HookType.POST_TRIM, tenantContext, null, null,
                payload == null ? Collections.emptyMap() : new HashMap<>(payload), false);
        publishHookEvent(EventType.HOOK_POST_TRIM, tenantContext, null, null, decision, payload, workflowId);
    }

    public List<HookRecord> listRecords() {
        synchronized (recordLock) {
            return new ArrayList<>(records);
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        hookExecutor.shutdownNow();
    }

    private HookDecision executeHooks(HookType hookType,
                                      TenantContext tenantContext,
                                      StepRecord stepRecord,
                                      String toolName,
                                      Map<String, Object> payload,
                                      boolean enforceBlock) {
        if (!hookProperties.isEnabled()) {
            HookDecision decision = new HookDecision(true, "disabled", Collections.emptyMap());
            recordExecution("hook_disabled", hookType, tenantContext, stepRecord, toolName, decision,
                    false, Instant.now(), Instant.now(), 0);
            return decision;
        }
        List<HookHandler> handlers = resolveOrderedHandlers();
        HookDecision finalDecision = new HookDecision(true, "ok", Collections.emptyMap());
        HookContext context = new HookContext(hookType, toolName,
                stepRecord != null ? stepRecord.getStepId() : null,
                tenantContext != null ? tenantContext.getTenantId() : null,
                payload == null ? Collections.emptyMap() : payload);
        if (handlers.isEmpty()) {
            recordExecution("hook_default", hookType, tenantContext, stepRecord, toolName, finalDecision,
                    false, Instant.now(), Instant.now(), 0);
            return finalDecision;
        }
        for (HookHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            HookProperties.HookConfig config = resolveHookConfig(handler.getHookId());
            HookExecutionResult execution = executeWithTimeout(handler, context, config);
            recordExecution(handler.getHookId(), hookType, tenantContext, stepRecord, toolName,
                    execution.decision, execution.timeout, execution.startedAt, execution.endedAt, execution.durationMs);
            if (!execution.decision.isAllowed()) {
                finalDecision = execution.decision;
                if (enforceBlock) {
                    break;
                }
            }
        }
        return finalDecision;
    }

    private HookExecutionResult executeWithTimeout(HookHandler handler,
                                                   HookContext context,
                                                   HookProperties.HookConfig config) {
        Instant startedAt = Instant.now();
        boolean timeout = false;
        HookDecision decision = null;
        long timeoutMs = config != null ? config.getTimeoutMs() : 0;
        Future<HookDecision> future = null;
        try {
            if (timeoutMs > 0) {
                future = hookExecutor.submit(() -> handler.handle(context));
                decision = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                decision = handler.handle(context);
            }
        } catch (TimeoutException ex) {
            timeout = true;
            if (future != null) {
                future.cancel(true);
            }
            decision = buildTimeoutDecision(handler.getHookId(), timeoutMs);
        } catch (ExecutionException ex) {
            decision = buildErrorDecision(handler.getHookId(), ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            decision = buildErrorDecision(handler.getHookId(), ex);
        } catch (Exception ex) {
            decision = buildErrorDecision(handler.getHookId(), ex);
        }
        if (decision == null) {
            decision = new HookDecision(true, "empty", Collections.emptyMap());
        }
        Instant endedAt = Instant.now();
        long durationMs = Math.max(0L, endedAt.toEpochMilli() - startedAt.toEpochMilli());
        return new HookExecutionResult(decision, timeout, startedAt, endedAt, durationMs);
    }

    private HookDecision buildTimeoutDecision(String hookId, long timeoutMs) {
        boolean allow = resolveTimeoutPolicy() == HookTimeoutPolicy.FAIL_OPEN;
        String reason = allow ? "hook_timeout_allow" : "hook_timeout_block";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("hookId", hookId);
        metadata.put("timeoutMs", timeoutMs);
        return new HookDecision(allow, reason, metadata);
    }

    private HookDecision buildErrorDecision(String hookId, Throwable throwable) {
        boolean allow = resolveTimeoutPolicy() == HookTimeoutPolicy.FAIL_OPEN;
        String reason = allow ? "hook_error_allow" : "hook_error_block";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("hookId", hookId);
        if (throwable != null && throwable.getMessage() != null) {
            metadata.put("error", throwable.getMessage());
        }
        return new HookDecision(allow, reason, metadata);
    }

    private HookTimeoutPolicy resolveTimeoutPolicy() {
        HookTimeoutPolicy policy = hookProperties.getTimeoutPolicy();
        return policy != null ? policy : HookTimeoutPolicy.FAIL_OPEN;
    }

    private List<HookHandler> resolveOrderedHandlers() {
        if (hookHandlers.isEmpty()) {
            return List.of();
        }
        Map<String, HookProperties.HookConfig> configMap = buildHookConfigMap();
        List<HookHandler> ordered = new ArrayList<>(hookHandlers);
        ordered.sort(Comparator.comparingInt((HookHandler handler) -> resolveOrder(handler, configMap))
                .thenComparing(handler -> normalizeHookId(handler != null ? handler.getHookId() : null)));
        return ordered;
    }

    private int resolveOrder(HookHandler handler, Map<String, HookProperties.HookConfig> configMap) {
        if (handler == null) {
            return 0;
        }
        HookProperties.HookConfig config = resolveHookConfig(handler.getHookId(), configMap);
        return config != null ? config.getOrder() : 0;
    }

    private HookProperties.HookConfig resolveHookConfig(String hookId) {
        return resolveHookConfig(hookId, buildHookConfigMap());
    }

    private HookProperties.HookConfig resolveHookConfig(String hookId,
                                                        Map<String, HookProperties.HookConfig> configMap) {
        if (configMap == null || configMap.isEmpty()) {
            return null;
        }
        String normalized = normalizeHookId(hookId);
        return configMap.get(normalized);
    }

    private Map<String, HookProperties.HookConfig> buildHookConfigMap() {
        Map<String, HookProperties.HookConfig> map = new HashMap<>();
        if (hookProperties.getHooks() == null) {
            return map;
        }
        for (HookProperties.HookConfig config : hookProperties.getHooks()) {
            if (config == null || !StringUtils.hasText(config.getHookId())) {
                continue;
            }
            map.put(normalizeHookId(config.getHookId()), config);
        }
        return map;
    }

    private String normalizeHookId(String hookId) {
        if (!StringUtils.hasText(hookId)) {
            return "";
        }
        return hookId.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> buildPayload(StepRecord stepRecord, Map<String, Object> result) {
        Map<String, Object> payload = new HashMap<>();
        if (stepRecord != null) {
            payload.put("stepId", stepRecord.getStepId());
            payload.put("workflowId", stepRecord.getWorkflowId());
            if (stepRecord.getType() != null) {
                payload.put("stepType", stepRecord.getType());
            }
            payload.put("stepSeq", stepRecord.getStepSeq());
        }
        if (result != null) {
            payload.put("result", result);
        }
        return payload;
    }

    private void recordExecution(String hookId,
                                 HookType type,
                                 TenantContext tenantContext,
                                 StepRecord stepRecord,
                                 String toolName,
                                 HookDecision decision,
                                 boolean timeout,
                                 Instant startedAt,
                                 Instant endedAt,
                                 long durationMs) {
        HookRecord record = new HookRecord();
        record.setHookId(StringUtils.hasText(hookId) ? hookId : "unknown");
        record.setHookType(type);
        record.setToolName(toolName);
        record.setStepId(stepRecord != null ? stepRecord.getStepId() : null);
        record.setTenantId(tenantContext != null ? tenantContext.getTenantId() : null);
        record.setAllowed(decision != null && decision.isAllowed());
        record.setReason(decision != null ? decision.getReason() : "unknown");
        record.setStartedAt(startedAt);
        record.setEndedAt(endedAt);
        record.setDurationMs(durationMs);
        record.setTimeout(timeout);
        String result = decision != null && decision.isAllowed() ? "ALLOW" : "BLOCK";
        if (timeout) {
            result = "TIMEOUT_" + result;
        }
        record.setResult(result);
        record.setExecutedAt(endedAt != null ? endedAt : Instant.now());
        synchronized (recordLock) {
            if (records.size() >= MAX_RECORDS) {
                records.removeFirst();
            }
            records.addLast(record);
        }
        log.info("Hook 执行, tenantId={}, hookId={}, type={}, tool={}, allowed={}, timeout={}, durationMs={}",
                record.getTenantId(), record.getHookId(), type, toolName, record.isAllowed(), timeout, durationMs);
    }

    private static class HookExecutionResult {
        private final HookDecision decision;
        private final boolean timeout;
        private final Instant startedAt;
        private final Instant endedAt;
        private final long durationMs;

        private HookExecutionResult(HookDecision decision,
                                    boolean timeout,
                                    Instant startedAt,
                                    Instant endedAt,
                                    long durationMs) {
            this.decision = decision;
            this.timeout = timeout;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.durationMs = durationMs;
        }
    }

    private void publishHookEvent(EventType type,
                                  TenantContext tenantContext,
                                  StepRecord stepRecord,
                                  String toolName,
                                  HookDecision decision,
                                  Map<String, Object> detailPayload) {
        publishHookEvent(type, tenantContext, stepRecord, toolName, decision, detailPayload,
                stepRecord != null ? stepRecord.getWorkflowId() : null);
    }

    private void publishHookEvent(EventType type,
                                  TenantContext tenantContext,
                                  StepRecord stepRecord,
                                  String toolName,
                                  HookDecision decision,
                                  Map<String, Object> detailPayload,
                                  String workflowId) {
        if (tenantContext == null || workflowId == null || workflowId.isBlank()) {
            return;
        }
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        Map<String, Object> payload = new HashMap<>();
        if (stepRecord != null) {
            payload.put("stepId", stepRecord.getStepId());
        }
        if (toolName != null) {
            payload.put("toolName", toolName);
        }
        payload.put("allowed", decision != null && decision.isAllowed());
        payload.put("reason", decision != null ? decision.getReason() : "unknown");
        if (detailPayload != null && !detailPayload.isEmpty()) {
            payload.putAll(detailPayload);
        }
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }
}
