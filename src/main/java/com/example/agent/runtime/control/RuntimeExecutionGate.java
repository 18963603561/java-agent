package com.example.agent.runtime.control;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 运行时执行门禁。
 *
 * <p>用途：统一处理暂停、恢复、审批阻塞与取消状态，确保主编排流程仅保留调用点。
 * <p>输入：工作流标识、租户上下文、序列计数器与事件发布器。
 * <p>输出：无。
 * <p>边界：取消态会抛出异常；暂停恢复会发布对应事件。
 */
@Component
public class RuntimeExecutionGate {

    private static final Logger log = LoggerFactory.getLogger(RuntimeExecutionGate.class);

    /**
     * 执行控制服务。
     */
    private final ExecutionControlService executionControlService;

    public RuntimeExecutionGate(ExecutionControlService executionControlService) {
        this.executionControlService = executionControlService;
    }

    /**
     * 应用执行控制门禁。
     *
     * <p>输入：工作流标识、租户上下文、序列计数器与事件发布器。
     * <p>输出：无。
     * <p>边界：阻塞等待期间若收到取消会抛出异常。
     */
    public void apply(String workflowId,
                      TenantContext tenantContext,
                      AtomicLong seqCounter,
                      RuntimeControlEventPublisher eventPublisher) {
        ExecutionControlState state = executionControlService.getState(workflowId);
        if (state == ExecutionControlState.RUNNING) {
            return;
        }
        if (state == ExecutionControlState.PAUSED) {
            log.info("执行控制命中暂停, tenantId={}, workflowId={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId);
            publish(eventPublisher, tenantContext, workflowId, seqCounter, EventType.WORKFLOW_PAUSED,
                    Map.of("state", ExecutionControlState.PAUSED.name()));
        } else if (state == ExecutionControlState.WAIT_APPROVAL) {
            log.info("执行控制等待审批, tenantId={}, workflowId={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId);
        }

        try {
            executionControlService.awaitIfBlocked(workflowId);
        } catch (ErrorCodeException ex) {
            if (isCancelled(ex)) {
                publish(eventPublisher, tenantContext, workflowId, seqCounter, EventType.WORKFLOW_CANCELLED,
                        Map.of("state", ExecutionControlState.CANCELLED.name()));
            }
            throw ex;
        }

        if (state == ExecutionControlState.PAUSED) {
            publish(eventPublisher, tenantContext, workflowId, seqCounter, EventType.WORKFLOW_RESUMED,
                    Map.of("state", ExecutionControlState.RUNNING.name()));
        }
    }

    private void publish(RuntimeControlEventPublisher eventPublisher,
                         TenantContext tenantContext,
                         String workflowId,
                         AtomicLong seqCounter,
                         EventType type,
                         Map<String, Object> payload) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publish(tenantContext, workflowId, seqCounter, type, payload);
    }

    private boolean isCancelled(ErrorCodeException ex) {
        return ex != null && "CANCELLED".equals(ex.getErrorCode());
    }
}
