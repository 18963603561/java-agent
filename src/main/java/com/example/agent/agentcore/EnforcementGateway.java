package com.example.agent.agentcore;

import com.example.agent.approval.ApprovalDecision;
import com.example.agent.approval.ApprovalHandle;
import com.example.agent.approval.ApprovalService;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.common.ErrorCodeProvider;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 执行网关，负责调用工具执行链路并发布事件。
 */
@Service
public class EnforcementGateway {

    private static final Logger log = LoggerFactory.getLogger(EnforcementGateway.class);

    private final ToolExecutor toolExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final ApprovalService approvalService;

    public EnforcementGateway(ToolExecutor toolExecutor,
                              ApplicationEventPublisher eventPublisher,
                              ApprovalService approvalService) {
        this.toolExecutor = toolExecutor;
        this.eventPublisher = eventPublisher;
        this.approvalService = approvalService;
    }

    /**
     * 执行任务请求。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     * @param toolName 工具名称
     * @return 工具执行结果
     */
    public Map<String, Object> execute(TaskRequest request,
                                       TenantContext tenantContext,
                                       String workflowId,
                                       String taskId,
                                       AtomicLong seqCounter,
                                       String toolName) {
        log.info("工具执行开始, tenantId={}, workflowId={}, tool={}",
                tenantContext.getTenantId(), workflowId, toolName);
        handleApprovalIfNeeded(request, tenantContext, workflowId, toolName, seqCounter);
        long invokedSeq = nextSeq(seqCounter);
        String usageId = buildUsageId(taskId, invokedSeq);
        Map<String, Object> invokedPayload = new HashMap<>();
        invokedPayload.put("tool", toolName);
        invokedPayload.put("query", request.getQuery());
        invokedPayload.put("usageId", usageId);
        StreamEvent invoked = buildEvent(tenantContext, workflowId, EventType.TOOL_INVOKED, invokedSeq,
                invokedPayload);
        eventPublisher.publishEvent(invoked);

        try {
            Map<String, Object> result = toolExecutor.execute(request, tenantContext, usageId, toolName, taskId);
            ensureUsageContext(result, tenantContext, taskId, usageId);
            long observationSeq = nextSeq(seqCounter);
            StreamEvent observation = buildEvent(tenantContext, workflowId, EventType.TOOL_OBSERVATION, observationSeq,
                    result);
            eventPublisher.publishEvent(observation);
            log.info("工具执行完成, tenantId={}, workflowId={}, tool={}",
                    tenantContext.getTenantId(), workflowId, toolName);
            return result;
        } catch (RuntimeException ex) {
            long errorSeq = nextSeq(seqCounter);
            StreamEvent error = buildEvent(tenantContext, workflowId, EventType.TOOL_ERROR, errorSeq,
                    Map.of(
                            "tool", toolName,
                            "error", ex.getMessage() == null ? "tool_failed" : ex.getMessage(),
                            "errorCode", resolveErrorCode(ex)
                    ));
            eventPublisher.publishEvent(error);
            log.error("工具执行失败, tenantId={}, workflowId={}, tool={}",
                    tenantContext.getTenantId(), workflowId, toolName, ex);
            throw ex;
        }
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

    private void ensureUsageContext(Map<String, Object> result,
                                    TenantContext tenantContext,
                                    String taskId,
                                    String usageId) {
        Object tokenUsage = result.get("tokenUsage");
        if (tokenUsage instanceof Map<?, ?> usageMap) {
            Map<Object, Object> mutable = new HashMap<>(usageMap);
            mutable.putIfAbsent("usageId", usageId);
            mutable.putIfAbsent("tenantId", tenantContext.getTenantId());
            if (taskId != null) {
                mutable.putIfAbsent("taskId", taskId);
            }
            result.put("tokenUsage", mutable);
        }
    }

    private StreamEvent buildEvent(TenantContext tenantContext, String workflowId, EventType type, long seq,
                                   Map<String, Object> payload) {
        String streamId = workflowId;
        Map<String, Object> mutable = payload == null ? new HashMap<>() : new HashMap<>(payload);
        attachTraceContext(mutable, tenantContext);
        StreamEvent event = new StreamEvent();
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(mutable);
        return event;
    }

    private void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", tenantContext.getTraceId());
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

    private String resolveErrorCode(Throwable ex) {
        if (ex instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }

    private void handleApprovalIfNeeded(TaskRequest request,
                                        TenantContext tenantContext,
                                        String workflowId,
                                        String toolName,
                                        AtomicLong seqCounter) {
        if (approvalService == null || !approvalService.isEnabled()
                || !approvalService.isHighRiskTool(toolName)) {
            return;
        }
        Map<String, Object> arguments = toolExecutor.buildArguments(request);
        String argsDigest = approvalService.buildArgsDigest(arguments);
        String snapshotId = resolveSnapshotId(request);
        ApprovalHandle handle = approvalService.requestApproval(
                tenantContext.getTenantId(),
                workflowId,
                snapshotId,
                toolName,
                argsDigest);

        publishApprovalRequested(tenantContext, workflowId, snapshotId, toolName, argsDigest,
                handle.getRequestId(), seqCounter);
        log.info("工具审批请求, tenantId={}, workflowId={}, requestId={}, toolName={}",
                tenantContext.getTenantId(), workflowId, handle.getRequestId(), toolName);

        ApprovalDecision decision = approvalService.awaitDecision(handle, approvalService.getTimeoutSeconds());
        publishApprovalDecision(tenantContext, workflowId, snapshotId, toolName, argsDigest, decision, seqCounter);
        if (!decision.isApproved()) {
            if (decision.isTimeout()) {
                log.warn("工具审批超时, tenantId={}, workflowId={}, requestId={}, toolName={}",
                        tenantContext.getTenantId(), workflowId, handle.getRequestId(), toolName);
                throw new ErrorCodeException(HttpStatus.REQUEST_TIMEOUT,
                        "APPROVAL_TIMEOUT", "工具调用审批超时");
            }
            String reason = decision.getReason() == null ? "approval_rejected" : decision.getReason();
            log.info("工具审批拒绝, tenantId={}, workflowId={}, requestId={}, toolName={}, reason={}",
                    tenantContext.getTenantId(), workflowId, handle.getRequestId(), toolName, reason);
            throw new ErrorCodeException(HttpStatus.FORBIDDEN,
                    "APPROVAL_REJECTED", "工具调用审批被拒绝");
        }
        log.info("工具审批通过, tenantId={}, workflowId={}, requestId={}, toolName={}",
                tenantContext.getTenantId(), workflowId, handle.getRequestId(), toolName);
    }

    private void publishApprovalRequested(TenantContext tenantContext,
                                          String workflowId,
                                          String snapshotId,
                                          String toolName,
                                          String argsDigest,
                                          String requestId,
                                          AtomicLong seqCounter) {
        long seq = nextSeq(seqCounter);
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantContext.getTenantId());
        payload.put("workflowId", workflowId);
        if (snapshotId != null && !snapshotId.isBlank()) {
            payload.put("snapshotId", snapshotId);
        }
        payload.put("toolName", toolName);
        payload.put("argsDigest", argsDigest);
        payload.put("requestId", requestId);
        payload.put("traceRequestId", tenantContext.getRequestId());
        StreamEvent event = buildEvent(tenantContext, workflowId, EventType.APPROVAL_REQUESTED, seq, payload);
        eventPublisher.publishEvent(event);
    }

    private void publishApprovalDecision(TenantContext tenantContext,
                                         String workflowId,
                                         String snapshotId,
                                         String toolName,
                                         String argsDigest,
                                         ApprovalDecision decision,
                                         AtomicLong seqCounter) {
        long seq = nextSeq(seqCounter);
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantContext.getTenantId());
        payload.put("workflowId", workflowId);
        if (snapshotId != null && !snapshotId.isBlank()) {
            payload.put("snapshotId", snapshotId);
        }
        payload.put("toolName", toolName);
        payload.put("argsDigest", argsDigest);
        if (decision != null) {
            payload.put("requestId", decision.getRequestId());
            payload.put("approved", decision.isApproved());
            if (decision.getReason() != null && !decision.getReason().isBlank()) {
                payload.put("reason", decision.getReason());
            }
            if (decision.isTimeout()) {
                payload.put("timeout", true);
            }
        }
        payload.put("traceRequestId", tenantContext.getRequestId());
        StreamEvent event = buildEvent(tenantContext, workflowId, EventType.APPROVAL_DECISION, seq, payload);
        eventPublisher.publishEvent(event);
    }

    private String resolveSnapshotId(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return null;
        }
        Object value = request.getContext().get("snapshotId");
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }
}
