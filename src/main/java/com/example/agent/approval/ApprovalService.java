package com.example.agent.approval;

import com.example.agent.common.ErrorCodeException;
import com.example.agent.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 高风险工具审批服务。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private static final int MAX_DIGEST_CHARS = 800;
    private static final int MAX_DIGEST_KEYS = 20;

    private final ApprovalProperties properties;
    private final MetricsPublisher metricsPublisher;
    private final ConcurrentHashMap<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    public ApprovalService(ApprovalProperties properties, MetricsPublisher metricsPublisher) {
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
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
            return 300;
        }
        return Math.max(1, properties.getTimeoutSeconds());
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
        PendingApproval pending = new PendingApproval(tenantId, workflowId, snapshotId, toolName, argsDigest,
                createdAt, future);
        pendingApprovals.put(requestId, pending);
        incrementMetric("approval_requested_total");
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
        if (handle == null || handle.getFuture() == null) {
            return ApprovalDecision.rejected(null, "approval_handle_missing");
        }
        int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : getTimeoutSeconds();
        try {
            ApprovalDecision decision = handle.getFuture().get(effectiveTimeout, TimeUnit.SECONDS);
            recordDecisionMetrics(handle, decision);
            return decision;
        } catch (TimeoutException ex) {
            ApprovalDecision decision = ApprovalDecision.timeout(handle.getRequestId(), "timeout");
            handle.getFuture().complete(decision);
            recordDecisionMetrics(handle, decision);
            incrementMetric("approval_timeout_total");
            log.warn("审批等待超时, requestId={}, timeoutSeconds={}", handle.getRequestId(), effectiveTimeout);
            return decision;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ApprovalDecision decision = ApprovalDecision.rejected(handle.getRequestId(), "interrupted");
            handle.getFuture().complete(decision);
            recordDecisionMetrics(handle, decision);
            log.warn("审批等待中断, requestId={}", handle.getRequestId());
            return decision;
        } catch (ExecutionException ex) {
            ApprovalDecision decision = ApprovalDecision.rejected(handle.getRequestId(), "decision_failed");
            handle.getFuture().complete(decision);
            recordDecisionMetrics(handle, decision);
            log.warn("审批等待异常, requestId={}", handle.getRequestId(), ex);
            return decision;
        } finally {
            pendingApprovals.remove(handle.getRequestId());
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
        PendingApproval pending = pendingApprovals.get(requestId);
        if (pending == null) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "审批请求不存在");
        }
        if (!matchTenant(tenantId, pending.tenantId) || !matchWorkflow(workflowId, pending.workflowId)) {
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "APPROVAL_TENANT_MISMATCH", "租户或工作流不匹配");
        }
        ApprovalDecision decision = approved
                ? ApprovalDecision.approved(requestId, reason)
                : ApprovalDecision.rejected(requestId, reason);
        pending.future.complete(decision);
        pendingApprovals.remove(requestId);
        log.info("审批决策已提交, tenantId={}, workflowId={}, requestId={}, approved={}, reason={}",
                tenantId, workflowId, requestId, approved, normalizeReason(reason));
        return decision;
    }

    /**
     * 构建参数摘要，用于事件展示与审计。
     *
     * @param arguments 参数
     * @return 参数摘要
     */
    public String buildArgsDigest(Map<String, Object> arguments) {
        if (arguments == null) {
            return null;
        }
        return buildDigest(arguments);
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
        incrementMetricWithTags("approval_decision_total", "approved", String.valueOf(decision.isApproved()));
        long durationMs = System.currentTimeMillis() - handle.getCreatedAtEpochMs();
        recordTime("approval_wait_duration_ms", Math.max(0, durationMs));
    }

    private void incrementMetric(String name) {
        if (metricsPublisher != null) {
            metricsPublisher.increment(name);
        }
    }

    private void incrementMetricWithTags(String name, String... tags) {
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags(name, tags);
        }
    }

    private void recordTime(String name, long millis) {
        if (metricsPublisher != null) {
            metricsPublisher.recordTime(name, millis);
        }
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "none";
        }
        return reason;
    }

    private String buildDigest(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return buildMapDigest(map);
        }
        if (value instanceof List<?> list) {
            return "list(size=" + list.size() + ")";
        }
        if (value instanceof String text) {
            return truncate(text, MAX_DIGEST_CHARS);
        }
        return truncate(value.toString(), MAX_DIGEST_CHARS);
    }

    private String buildMapDigest(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        List<String> keys = new ArrayList<>();
        for (Object key : map.keySet()) {
            if (key == null) {
                continue;
            }
            keys.add(key.toString());
            if (keys.size() >= MAX_DIGEST_KEYS) {
                break;
            }
        }
        StringBuilder builder = new StringBuilder("keys=").append(keys);
        if (map.size() > keys.size()) {
            builder.append("...");
        }
        builder.append(",size=").append(map.size());
        return truncate(builder.toString(), MAX_DIGEST_CHARS);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return value;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    static class PendingApproval {
        private final String tenantId;
        private final String workflowId;
        private final String snapshotId;
        private final String toolName;
        private final String argsDigest;
        private final long createdAtEpochMs;
        private final CompletableFuture<ApprovalDecision> future;

        PendingApproval(String tenantId,
                        String workflowId,
                        String snapshotId,
                        String toolName,
                        String argsDigest,
                        long createdAtEpochMs,
                        CompletableFuture<ApprovalDecision> future) {
            this.tenantId = tenantId;
            this.workflowId = workflowId;
            this.snapshotId = snapshotId;
            this.toolName = toolName;
            this.argsDigest = argsDigest;
            this.createdAtEpochMs = createdAtEpochMs;
            this.future = future;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public String getSnapshotId() {
            return snapshotId;
        }

        public String getToolName() {
            return toolName;
        }

        public String getArgsDigest() {
            return argsDigest;
        }

        public long getCreatedAtEpochMs() {
            return createdAtEpochMs;
        }

        public CompletableFuture<ApprovalDecision> getFuture() {
            return future;
        }
    }
}
