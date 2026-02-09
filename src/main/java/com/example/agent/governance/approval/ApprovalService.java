package com.example.agent.governance.approval;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.approval.domain.ApprovalArgsDigestBuilder;
import com.example.agent.governance.approval.domain.ApprovalDecisionAwaiter;
import com.example.agent.governance.approval.domain.ApprovalStoreProperties;
import com.example.agent.governance.approval.domain.PendingApprovalRecord;
import com.example.agent.governance.approval.domain.PendingApprovalStore;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 高风险工具审批服务。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_PENDING_TTL_SECONDS = 1800;
    private static final int DEFAULT_PENDING_MAX_SIZE = 2000;
    private static final int DEFAULT_CLEANUP_INTERVAL_SECONDS = 30;

    private final ApprovalProperties properties;
    private final PendingApprovalStore pendingApprovalStore;
    private final ApprovalDecisionAwaiter approvalDecisionAwaiter;
    private final ApprovalArgsDigestBuilder approvalArgsDigestBuilder;
    private final GovernanceTelemetry governanceTelemetry;

    @Autowired
    public ApprovalService(ApprovalProperties properties,
                           PendingApprovalStore pendingApprovalStore,
                           ApprovalDecisionAwaiter approvalDecisionAwaiter,
                           ApprovalArgsDigestBuilder approvalArgsDigestBuilder,
                           GovernanceTelemetry governanceTelemetry) {
        this.properties = properties;
        this.pendingApprovalStore = pendingApprovalStore;
        this.approvalDecisionAwaiter = approvalDecisionAwaiter;
        this.approvalArgsDigestBuilder = approvalArgsDigestBuilder;
        this.governanceTelemetry = governanceTelemetry;
    }

    /**
     * 是否启用审批。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties != null && properties.isEnabled();
    }

    /**
     * 判断工具是否属于高风险。
     *
     * @param toolName 工具名称
     * @return 是否高风险
     */
    public boolean isHighRiskTool(String toolName) {
        if (!StringUtils.hasText(toolName) || properties == null || properties.getHighRiskTools() == null) {
            return false;
        }
        for (String candidate : properties.getHighRiskTools()) {
            if (candidate != null && candidate.trim().equalsIgnoreCase(toolName.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取默认超时时间（秒）。
     *
     * @return 超时秒数
     */
    public int getTimeoutSeconds() {
        if (properties == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.max(1, properties.getTimeoutSeconds());
    }

    /**
     * 获取待审批缓存保留时长（秒）。
     *
     * @return 保留时长
     */
    public int getPendingTtlSeconds() {
        if (properties == null) {
            return DEFAULT_PENDING_TTL_SECONDS;
        }
        return Math.max(1, properties.getPendingTtlSeconds());
    }

    /**
     * 获取待审批缓存最大容量。
     *
     * @return 最大容量
     */
    public int getPendingMaxSize() {
        if (properties == null) {
            return DEFAULT_PENDING_MAX_SIZE;
        }
        return Math.max(1, properties.getPendingMaxSize());
    }

    /**
     * 获取清理周期（秒）。
     *
     * @return 清理周期
     */
    public int getCleanupIntervalSeconds() {
        if (properties == null) {
            return DEFAULT_CLEANUP_INTERVAL_SECONDS;
        }
        return Math.max(1, properties.getCleanupIntervalSeconds());
    }

    /**
     * 申请审批并返回等待句柄。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param snapshotId 快照标识
     * @param toolName 工具名称
     * @param argsDigest 参数摘要
     * @return 审批句柄
     */
    public ApprovalHandle requestApproval(String tenantId,
                                          String workflowId,
                                          String snapshotId,
                                          String toolName,
                                          String argsDigest) {
        String requestId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        CompletableFuture<ApprovalDecision> future = new CompletableFuture<>();
        PendingApprovalRecord pending = new PendingApprovalRecord(tenantId, workflowId, snapshotId, toolName, argsDigest,
                createdAt, future);
        pendingApprovalStore.put(requestId, pending, resolveStoreProperties());
        governanceTelemetry.increment("approval.request.total",
                "domain", "approval",
                "action", "request",
                "result", "pending");
        log.info("审批请求已创建, tenantId={}, workflowId={}, requestId={}, toolName={}",
                tenantId, workflowId, requestId, toolName);
        return new ApprovalHandle(requestId, future, createdAt);
    }

    /**
     * 等待审批决策。
     *
     * @param handle 审批句柄
     * @param timeoutSeconds 超时秒数
     * @return 审批结果
     */
    public ApprovalDecision awaitDecision(ApprovalHandle handle, int timeoutSeconds) {
        int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : getTimeoutSeconds();
        if (handle == null || handle.getFuture() == null) {
            return ApprovalDecision.rejected(null, "approval_handle_missing");
        }
        try {
            ApprovalDecision decision = approvalDecisionAwaiter.await(handle, effectiveTimeout);
            recordDecisionMetrics(handle, decision);
            return decision;
        } finally {
            pendingApprovalStore.remove(handle.getRequestId());
        }
    }

    /**
     * 写入审批决策并唤醒等待方。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param requestId 审批请求标识
     * @param approved 是否通过
     * @param reason 决策原因
     * @return 审批结果
     */
    public ApprovalDecision decide(String tenantId,
                                   String workflowId,
                                   String requestId,
                                   boolean approved,
                                   String reason) {
        if (!StringUtils.hasText(requestId)) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "APPROVAL_REQUEST_MISSING", "requestId 不能为空");
        }
        PendingApprovalRecord pending = pendingApprovalStore.get(requestId, resolveStoreProperties());
        if (pending == null) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "审批请求不存在");
        }
        if (!matchTenant(tenantId, pending.getTenantId()) || !matchWorkflow(workflowId, pending.getWorkflowId())) {
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "APPROVAL_TENANT_MISMATCH", "租户或工作流不匹配");
        }
        ApprovalDecision decision = approved
                ? ApprovalDecision.approved(requestId, reason)
                : ApprovalDecision.rejected(requestId, reason);
        pending.getFuture().complete(decision);
        pendingApprovalStore.remove(requestId);
        governanceTelemetry.increment("approval.decide.total",
                "domain", "approval",
                "action", "decide",
                "result", approved ? "approved" : "rejected");
        log.info("审批决策已提交, tenantId={}, workflowId={}, requestId={}, approved={}, reason={}",
                tenantId, workflowId, requestId, approved, normalizeReason(reason));
        return decision;
    }

    /**
     * 获取当前待审批数量，仅用于测试与诊断。
     *
     * @return 待审批数量
     */
    public int pendingCount() {
        return pendingApprovalStore.size(resolveStoreProperties());
    }

    /**
     * 查询任意一个匹配租户与工作流的待审批请求标识，仅用于测试与诊断。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 请求标识，未命中时返回空
     */
    public String findAnyPendingRequestId(String tenantId, String workflowId) {
        return pendingApprovalStore.findRequestId(tenantId, workflowId, resolveStoreProperties());
    }

    /**
     * 构建参数摘要，用于事件展示与审计。
     *
     * @param arguments 参数
     * @return 参数摘要
     */
    public String buildArgsDigest(Map<String, Object> arguments) {
        return approvalArgsDigestBuilder.buildArgsDigest(arguments);
    }

    private boolean matchTenant(String tenantId, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return StringUtils.hasText(tenantId) && expected.equals(tenantId);
    }

    private boolean matchWorkflow(String workflowId, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return StringUtils.hasText(workflowId) && expected.equals(workflowId);
    }

    private void recordDecisionMetrics(ApprovalHandle handle, ApprovalDecision decision) {
        if (decision == null) {
            return;
        }
        governanceTelemetry.increment("approval.decision.total",
                "domain", "approval",
                "action", "await",
                "result", decision.isApproved() ? "approved" : (decision.isTimeout() ? "timeout" : "rejected"));
        long durationMs = System.currentTimeMillis() - handle.getCreatedAtEpochMs();
        governanceTelemetry.time("approval.await.duration_ms", Math.max(0, durationMs));
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "none";
        }
        return reason;
    }

    private ApprovalStoreProperties resolveStoreProperties() {
        return new ApprovalStoreProperties(getPendingTtlSeconds(), getPendingMaxSize(), getCleanupIntervalSeconds());
    }

}
