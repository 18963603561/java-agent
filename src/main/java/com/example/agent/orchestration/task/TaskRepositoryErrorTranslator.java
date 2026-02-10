package com.example.agent.orchestration.task;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.auth.TenantContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 任务仓储异常翻译器。
 * <p>用途：统一编排层仓储异常日志、事件发射与错误码翻译逻辑。
 */
@Service
public class TaskRepositoryErrorTranslator {

    /**
     * 任务仓储失败错误码。
     */
    public static final String TASK_REPOSITORY_FAILURE_CODE = "TASK_REPOSITORY_FAILURE";

    /**
     * 任务仓储失败原因。
     */
    public static final String TASK_REPOSITORY_FAILURE_REASON = "task_repository_failure";

    private static final Logger log = LoggerFactory.getLogger(TaskRepositoryErrorTranslator.class);

    private final TaskEventPublisher taskEventPublisher;

    public TaskRepositoryErrorTranslator(TaskEventPublisher taskEventPublisher) {
        this.taskEventPublisher = taskEventPublisher;
    }

    /**
     * 翻译仓储异常为统一业务异常。
     */
    public ErrorCodeException translate(TaskRepositoryException exception,
                                        String stage,
                                        TenantContext tenantContext,
                                        String workflowId,
                                        String taskId,
                                        boolean publishErrorEvent) {
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        String resolvedWorkflowId = StringUtils.hasText(workflowId) ? workflowId : exception.getWorkflowId();
        String resolvedTaskId = StringUtils.hasText(taskId) ? taskId : exception.getTaskId();
        log.error("任务仓储操作失败, stage={}, operation={}, tenantId={}, taskId={}, workflowId={}",
                stage, exception.getOperation(), tenantId, resolvedTaskId, resolvedWorkflowId, exception);
        if (publishErrorEvent) {
            taskEventPublisher.publishRepositoryError(tenantContext,
                    resolvedWorkflowId,
                    resolvedTaskId,
                    exception.getOperation(),
                    null);
        }
        return new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                TASK_REPOSITORY_FAILURE_CODE,
                TASK_REPOSITORY_FAILURE_REASON);
    }

    /**
     * 处理工作流执行阶段的仓储异常。
     */
    public void handleDuringWorkflow(TaskRepositoryException exception,
                                     TenantContext tenantContext,
                                     TaskRecord record,
                                     String workflowId,
                                     AtomicLong seqCounter,
                                     TaskLifecycleService taskLifecycleService) {
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        String taskId = record != null ? record.getTaskId() : null;
        log.error("任务执行期间仓储失败, operation={}, tenantId={}, taskId={}, workflowId={}",
                exception.getOperation(), tenantId, taskId, workflowId, exception);
        Map<String, Object> payload = taskEventPublisher.publishRepositoryError(tenantContext,
                workflowId,
                taskId,
                exception.getOperation(),
                seqCounter);
        taskLifecycleService.updateInMemoryRecord(record, TaskStatus.FAILED, payload);
    }

    /**
     * 安全读取任务，仓储异常时返回空并记录日志。
     */
    public TaskRecord safeFindTask(TaskRepository taskRepository,
                                   String tenantId,
                                   String taskId,
                                   String workflowId) {
        try {
            return taskRepository.findById(tenantId, taskId);
        } catch (TaskRepositoryException exception) {
            log.error("任务状态读取失败，使用内存状态兜底, tenantId={}, taskId={}, workflowId={}, operation={}",
                    tenantId, taskId, workflowId, exception.getOperation(), exception);
            return null;
        }
    }
}

