package com.example.agent.runtime.control;

import com.example.agent.common.error.ErrorCodeException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 执行控制服务，管理工作流暂停、恢复、取消与审批状态。
 * 当前采用内存存储，后续可替换为 Redis 或数据库。
 */
@Service
public class ExecutionControlService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionControlService.class);

    /**
     * 工作流状态缓存。
     */
    private final ConcurrentHashMap<String, ControlEntry> controlEntries = new ConcurrentHashMap<>();

    /**
     * 暂停指定工作流。
     *
     * @param workflowId 工作流标识
     * @return 最新状态
     */
    public ExecutionControlState pause(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return ExecutionControlState.RUNNING;
        }
        ControlEntry entry = getEntry(workflowId);
        synchronized (entry.monitor) {
            if (entry.state == ExecutionControlState.CANCELLED
                    || entry.state == ExecutionControlState.WAIT_APPROVAL) {
                return entry.state;
            }
            if (entry.state != ExecutionControlState.PAUSED) {
                entry.state = ExecutionControlState.PAUSED;
                log.info("执行控制暂停, workflowId={}", workflowId);
            }
            entry.monitor.notifyAll();
            return entry.state;
        }
    }

    /**
     * 恢复指定工作流。
     *
     * @param workflowId 工作流标识
     * @return 最新状态
     */
    public ExecutionControlState resume(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return ExecutionControlState.RUNNING;
        }
        ControlEntry entry = getEntry(workflowId);
        synchronized (entry.monitor) {
            if (entry.state == ExecutionControlState.CANCELLED) {
                return entry.state;
            }
            if (entry.state == ExecutionControlState.PAUSED) {
                entry.state = ExecutionControlState.RUNNING;
                log.info("执行控制恢复, workflowId={}", workflowId);
            }
            entry.monitor.notifyAll();
            return entry.state;
        }
    }

    /**
     * 取消指定工作流。
     *
     * @param workflowId 工作流标识
     * @return 最新状态
     */
    public ExecutionControlState cancel(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return ExecutionControlState.RUNNING;
        }
        ControlEntry entry = getEntry(workflowId);
        synchronized (entry.monitor) {
            entry.state = ExecutionControlState.CANCELLED;
            log.info("执行控制取消, workflowId={}", workflowId);
            entry.monitor.notifyAll();
            return entry.state;
        }
    }

    /**
     * 请求人工审批。
     *
     * @param workflowId 工作流标识
     * @param payload 审批载荷
     * @return 最新状态
     */
    public ExecutionControlState requestApproval(String workflowId, Map<String, Object> payload) {
        if (!StringUtils.hasText(workflowId)) {
            return ExecutionControlState.RUNNING;
        }
        ControlEntry entry = getEntry(workflowId);
        synchronized (entry.monitor) {
            if (entry.state == ExecutionControlState.CANCELLED) {
                return entry.state;
            }
            entry.approvalPayload = payload;
            entry.state = ExecutionControlState.WAIT_APPROVAL;
            log.info("执行控制申请审批, workflowId={}", workflowId);
            entry.monitor.notifyAll();
            return entry.state;
        }
    }

    /**
     * 写入审批决策。
     *
     * @param workflowId 工作流标识
     * @param decision 审批决策
     * @return 最新状态
     */
    public ExecutionControlState decideApproval(String workflowId, String decision) {
        if (!StringUtils.hasText(workflowId)) {
            return ExecutionControlState.RUNNING;
        }
        ControlEntry entry = getEntry(workflowId);
        synchronized (entry.monitor) {
            if (entry.state == ExecutionControlState.CANCELLED) {
                return entry.state;
            }
            entry.lastDecision = decision;
            if (isApproved(decision)) {
                entry.state = ExecutionControlState.RUNNING;
            } else {
                entry.state = ExecutionControlState.CANCELLED;
            }
            log.info("执行控制审批决策, workflowId={}, decision={}, state={}",
                    workflowId, decision, entry.state);
            entry.monitor.notifyAll();
            return entry.state;
        }
    }

    /**
     * 获取当前执行状态。
     *
     * @param workflowId 工作流标识
     * @return 当前状态
     */
    public ExecutionControlState getState(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return ExecutionControlState.RUNNING;
        }
        ControlEntry entry = controlEntries.get(workflowId);
        return entry == null ? ExecutionControlState.RUNNING : entry.state;
    }

    /**
     * 当状态处于暂停或审批时阻塞等待。
     *
     * @param workflowId 工作流标识
     * @return 当前状态
     */
    public ExecutionControlState awaitIfBlocked(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return ExecutionControlState.RUNNING;
        }
        ControlEntry entry = getEntry(workflowId);
        synchronized (entry.monitor) {
            while (entry.state == ExecutionControlState.PAUSED
                    || entry.state == ExecutionControlState.WAIT_APPROVAL) {
                try {
                    entry.monitor.wait();
                // 异常捕获：记录上下文并按当前策略处理
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                            "EXECUTION_INTERRUPTED", "执行被中断");
                }
            }
            if (entry.state == ExecutionControlState.CANCELLED) {
                throw new ErrorCodeException(HttpStatus.CONFLICT, "CANCELLED", "工作流已取消");
            }
            return entry.state;
        }
    }

    private ControlEntry getEntry(String workflowId) {
        return controlEntries.computeIfAbsent(workflowId, key -> new ControlEntry());
    }

    private boolean isApproved(String decision) {
        if (!StringUtils.hasText(decision)) {
            return false;
        }
        String normalized = decision.trim().toLowerCase();
        return "approve".equals(normalized)
                || "approved".equals(normalized)
                || "allow".equals(normalized)
                || "true".equals(normalized);
    }

    private static class ControlEntry {
        private final Object monitor = new Object();
        private volatile ExecutionControlState state = ExecutionControlState.RUNNING;
        private Map<String, Object> approvalPayload;
        private String lastDecision;
    }
}
