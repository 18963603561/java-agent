package com.example.agent.governance.approval.domain;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.common.state.GenericStateStore;
import com.example.agent.governance.common.state.StateStorePolicy;
import com.example.agent.governance.common.state.StoreMetricsRecorder;
import com.example.agent.governance.approval.ApprovalDecision;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 待审批存储，负责容量控制与过期清理。
 */
@Component
public class PendingApprovalStore {

    private static final Logger log = LoggerFactory.getLogger(PendingApprovalStore.class);

    private final GenericStateStore<PendingApprovalRecord> stateStore;

    public PendingApprovalStore(MetricsPublisher metricsPublisher) {
        this.stateStore = new GenericStateStore<>(
                PendingApprovalRecord::getCreatedAtEpochMs,
                (requestId, record) -> record.getFuture().complete(ApprovalDecision.timeout(requestId, "pending_expired")),
                new StoreMetricsRecorder<>() {
                    @Override
                    public void onCapacityRejected(PendingApprovalRecord value, int currentSize, int maxSize) {
                        metricsPublisher.incrementWithTags("governance.approval.pending_rejected_total", "reason", "capacity");
                        log.warn("审批缓存容量超限, tenantId={}, workflowId={}, toolName={}, currentSize={}, maxSize={}",
                                value != null ? value.getTenantId() : null,
                                value != null ? value.getWorkflowId() : null,
                                value != null ? value.getToolName() : null,
                                currentSize,
                                maxSize);
                    }

                    @Override
                    public void onCleanup(int removed, int currentSize) {
                        for (int index = 0; index < removed; index++) {
                            metricsPublisher.incrementWithTags("governance.approval.pending_cleanup_total", "cause", "expired");
                        }
                        metricsPublisher.recordSummary("governance.approval.pending_cleanup_removed", removed);
                        log.info("审批缓存清理完成, removed={}, currentSize={}", removed, currentSize);
                    }
                });
    }

    /**
     * 写入待审批记录。
     *
     * @param requestId 请求标识
     * @param record 待审批记录
     * @param properties 存储配置
     */
    public void put(String requestId, PendingApprovalRecord record, ApprovalStoreProperties properties) {
        StateStorePolicy policy = toPolicy(properties);
        boolean stored = stateStore.put(requestId, record, policy);
        if (!stored) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    "APPROVAL_PENDING_FULL",
                    "审批系统繁忙，请稍后重试");
        }
    }

    /**
     * 根据请求标识读取待审批记录。
     *
     * @param requestId 请求标识
     * @param properties 存储配置
     * @return 待审批记录
     */
    public PendingApprovalRecord get(String requestId, ApprovalStoreProperties properties) {
        return stateStore.get(requestId, toPolicy(properties));
    }

    /**
     * 删除待审批记录。
     *
     * @param requestId 请求标识
     */
    public void remove(String requestId) {
        stateStore.remove(requestId);
    }

    /**
     * 获取当前待审批数量。
     *
     * @param properties 存储配置
     * @return 数量
     */
    public int size(ApprovalStoreProperties properties) {
        return stateStore.size(toPolicy(properties));
    }

    /**
     * 查询任意一个匹配租户与工作流的待审批请求标识。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param properties 存储配置
     * @return 请求标识，未命中时返回空
     */
    public String findRequestId(String tenantId, String workflowId, ApprovalStoreProperties properties) {
        return stateStore.findFirstKey(toPolicy(properties),
                record -> matchField(tenantId, record.getTenantId())
                        && matchField(workflowId, record.getWorkflowId()));
    }

    private boolean matchField(String actual, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return StringUtils.hasText(actual) && expected.equals(actual);
    }

    private StateStorePolicy toPolicy(ApprovalStoreProperties properties) {
        return new StateStorePolicy(
                properties.getPendingTtlSeconds(),
                properties.getPendingMaxSize(),
                properties.getCleanupIntervalSeconds());
    }
}
