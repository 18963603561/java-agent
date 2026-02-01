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
 *
 * <p>职责：封装工具调用的审批、事件发布与错误处理。</p>
 * <p>边界：仅处理执行链路，不负责业务编排。</p>
 */
@Service
public class EnforcementGateway {

    private static final Logger log = LoggerFactory.getLogger(EnforcementGateway.class);

    /**
     * 工具执行器。
     */
    private final ToolExecutor toolExecutor;
    /**
     * 事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 审批服务。
     */
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
        return executeInternal(request, tenantContext, workflowId, taskId, seqCounter, toolName, null);
    }

    /**
     * 执行任务请求（带工具参数覆盖）。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     * @param toolName 工具名称
     * @param toolArguments 工具调用参数
     * @return 工具执行结果
     */
    public Map<String, Object> executeWithArguments(TaskRequest request,
                                                    TenantContext tenantContext,
                                                    String workflowId,
                                                    String taskId,
                                                    AtomicLong seqCounter,
                                                    String toolName,
                                                    Map<String, Object> toolArguments) {
        return executeInternal(request, tenantContext, workflowId, taskId, seqCounter, toolName, toolArguments);
    }

    /**
     * 执行内部流程，统一处理审批、事件发布与执行结果回写。
     */
    private Map<String, Object> executeInternal(TaskRequest request,
                                                TenantContext tenantContext,
                                                String workflowId,
                                                String taskId,
                                                AtomicLong seqCounter,
                                                String toolName,
                                                Map<String, Object> toolArguments) {
        // 执行前记录日志，便于跟踪
        log.info("工具执行开始, tenantId={}, workflowId={}, tool={}",
                tenantContext.getTenantId(), workflowId, toolName);
        // 审批参数优先使用外部传入参数
        Map<String, Object> approvalArgs = toolArguments == null
                ? toolExecutor.buildArguments(request)
                : toolExecutor.buildMergedArguments(request, toolArguments);
        handleApprovalIfNeeded(request, tenantContext, workflowId, toolName, seqCounter, approvalArgs);
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
            // 根据是否传入参数选择执行分支
            Map<String, Object> result = toolArguments == null
                    ? toolExecutor.execute(request, tenantContext, usageId, toolName, taskId)
                    : toolExecutor.executeWithArguments(request, tenantContext, usageId, toolName, taskId, toolArguments);
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

    /**
     * 生成递增序列号。
     */
    private long nextSeq(AtomicLong seqCounter) {
        return seqCounter.incrementAndGet();
    }

    /**
     * 生成使用记录标识。
     */
    private String buildUsageId(String taskId, long seq) {
        if (taskId == null || taskId.isBlank()) {
            return "usage:" + seq;
        }
        return taskId + ":" + seq;
    }

    /**
     * 补全计量上下文字段。
     */
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

    /**
     * 构建事件对象并附加追踪信息。
     */
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

    /**
     * 写入追踪上下文。
     */
    private void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", tenantContext.getTraceId());
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

    /**
     * 解析异常对应的错误码。
     */
    private String resolveErrorCode(Throwable ex) {
        if (ex instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }

    /**
     * 根据策略触发审批流程。
     */
    private void handleApprovalIfNeeded(TaskRequest request,
                                        TenantContext tenantContext,
                                        String workflowId,
                                        String toolName,
                                        AtomicLong seqCounter,
                                        Map<String, Object> arguments) {
        if (approvalService == null || !approvalService.isEnabled()
                || !approvalService.isHighRiskTool(toolName)) {
            return;
        }
        Map<String, Object> args = arguments != null ? arguments : toolExecutor.buildArguments(request);
        String argsDigest = approvalService.buildArgsDigest(args);
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

    /**
     * 发布审批申请事件。
     */
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

    /**
     * 发布审批决策事件。
     */
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

    /**
     * 从请求上下文中解析快照标识。
     */
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
