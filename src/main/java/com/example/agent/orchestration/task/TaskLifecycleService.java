package com.example.agent.orchestration.task;

import com.example.agent.orchestration.task.contract.TaskStatusView;
import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 任务生命周期服务。
 * <p>用途：集中管理任务创建、状态迁移、状态判断与结果负载构建。
 */
@Service
public class TaskLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TaskLifecycleService.class);

    private final TaskRepository taskRepository;

    public TaskLifecycleService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 创建并持久化任务。
     */
    public TaskRecord createTask(TaskSubmitCommand command, TenantContext tenantContext) {
        String tenantId = tenantContext.getTenantId();
        String taskId = UUID.randomUUID().toString();
        String workflowId = UUID.randomUUID().toString();

        TaskRecord record = new TaskRecord();
        record.setTaskId(taskId);
        record.setWorkflowId(workflowId);
        record.setStatus(TaskStatus.SUBMITTED);
        record.setTenantId(tenantId);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(record.getCreatedAt());
        record.setIdempotencyKey(command != null ? command.getIdempotencyKey() : null);
        record.setRequest(buildRequestPayload(command));

        taskRepository.save(record);
        log.info("任务创建, tenantId={}, taskId={}, workflowId={}", tenantId, taskId, workflowId);
        return record;
    }

    /**
     * 更新任务状态并持久化。
     */
    public void updateTaskStatus(TaskRecord record, TaskStatus status, Map<String, Object> result) {
        if (record == null || status == null) {
            return;
        }
        record.setStatus(status);
        record.setUpdatedAt(Instant.now());
        if (result != null) {
            record.setResult(result);
            if (status == TaskStatus.COMPLETED) {
                log.info("任务结果写入, tenantId={}, taskId={}, workflowId={}, resultKeys={}",
                        record.getTenantId(), record.getTaskId(), record.getWorkflowId(), result.keySet());
            }
        }
        taskRepository.save(record);
    }

    /**
     * 更新内存记录状态。
     */
    public void updateInMemoryRecord(TaskRecord record, TaskStatus status, Map<String, Object> result) {
        if (record == null || status == null) {
            return;
        }
        record.setStatus(status);
        record.setUpdatedAt(Instant.now());
        if (result != null) {
            record.setResult(result);
        }
    }

    /**
     * 根据运行时结果构建持久化负载。
     */
    public Map<String, Object> buildResultPayload(RuntimeResult runtimeResult) {
        if (runtimeResult == null) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", runtimeResult.getPlanId());
        payload.put("planSummary", runtimeResult.getPlanSummary());
        payload.put("steps", runtimeResult.getSteps());
        payload.put("finalOutput", runtimeResult.getFinalOutput());
        return payload;
    }

    /**
     * 判断状态是否终态。
     */
    public boolean isTerminalStatus(String status) {
        return isTerminalStatus(TaskStatus.from(status));
    }

    /**
     * 判断状态是否终态。
     */
    public boolean isTerminalStatus(TaskStatus status) {
        return status != null && status.isTerminal();
    }

    /**
     * 任务记录转换为状态视图。
     */
    public TaskStatusView toStatusView(TaskRecord record) {
        if (record == null) {
            return new TaskStatusView();
        }
        return new TaskStatusView(record.getTaskId(), record.getWorkflowId(), record.getStatus(),
                record.getUpdatedAt(), record.getResult());
    }

    /**
     * 构建请求持久化负载。
     */
    public Map<String, Object> buildRequestPayload(TaskSubmitCommand command) {
        if (command == null) {
            return Map.of();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", command.getQuery());
        payload.put("sessionId", command.getSessionId());
        payload.put("skillName", command.getSkillName());
        payload.put("context", command.getContext());
        payload.put("idempotencyKey", command.getIdempotencyKey());
        payload.put("toolChoice", command.getToolChoice());
        payload.put("executionMode", command.getExecutionMode());
        payload.put("waitTimeoutMs", command.getWaitTimeoutMs());
        return payload;
    }
}
