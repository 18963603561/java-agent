package com.example.agent.governance.approval.domain;

import com.example.agent.governance.approval.ApprovalDecision;
import com.example.agent.governance.approval.ApprovalHandle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审批决策等待器。
 */
@Component
public class ApprovalDecisionAwaiter {

    private static final Logger log = LoggerFactory.getLogger(ApprovalDecisionAwaiter.class);

    /**
     * 等待审批结果。
     *
     * @param handle 审批句柄
     * @param timeoutSeconds 超时秒数
     * @return 审批决策
     */
    public ApprovalDecision await(ApprovalHandle handle, int timeoutSeconds) {
        if (handle == null || handle.getFuture() == null) {
            return ApprovalDecision.rejected(null, "approval_handle_missing");
        }
        int effectiveTimeout = Math.max(1, timeoutSeconds);
        try {
            return handle.getFuture().get(effectiveTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            ApprovalDecision decision = ApprovalDecision.timeout(handle.getRequestId(), "timeout");
            handle.getFuture().complete(decision);
            log.warn("审批等待超时, requestId={}, timeoutSeconds={}", handle.getRequestId(), effectiveTimeout);
            return decision;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ApprovalDecision decision = ApprovalDecision.rejected(handle.getRequestId(), "interrupted");
            handle.getFuture().complete(decision);
            log.warn("审批等待中断, requestId={}", handle.getRequestId(), ex);
            return decision;
        } catch (ExecutionException ex) {
            ApprovalDecision decision = ApprovalDecision.rejected(handle.getRequestId(), "decision_failed");
            handle.getFuture().complete(decision);
            log.warn("审批等待异常, requestId={}", handle.getRequestId(), ex);
            return decision;
        }
    }
}

