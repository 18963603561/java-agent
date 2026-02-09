package com.example.agent.governance.replay.domain;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.orchestration.task.TaskRecord;
import com.example.agent.orchestration.task.TaskRepository;
import com.example.agent.security.auth.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 回放任务解析器。
 */
@Component
public class ReplayTaskResolver {

    private static final Logger log = LoggerFactory.getLogger(ReplayTaskResolver.class);

    private final TaskRepository taskRepository;
    private final GovernanceTelemetry governanceTelemetry;

    public ReplayTaskResolver(TaskRepository taskRepository) {
        this(taskRepository, null);
    }

    @Autowired
    public ReplayTaskResolver(TaskRepository taskRepository,
                              GovernanceTelemetry governanceTelemetry) {
        this.taskRepository = taskRepository;
        this.governanceTelemetry = governanceTelemetry;
    }

    /**
     * 解析回放任务。
     *
     * @param command 回放命令
     * @param tenantContext 租户上下文
     * @return 任务状态
     */
    public ReplayTaskSnapshot resolveTask(ReplayCommand command, TenantContext tenantContext) {
        try {
            TaskRecord record = taskRepository.findById(tenantContext.getTenantId(), command.getTaskId());
            if (record == null) {
                logReplayNotFound(tenantContext, command.getTaskId());
                throw new ErrorCodeException(HttpStatus.NOT_FOUND, "REPLAY_NOT_FOUND", "回放任务不存在");
            }
            incrementReplayResolveMetric("success");
            return new ReplayTaskSnapshot(record.getTaskId(), record.getWorkflowId(), record.getStatus());
        } catch (ErrorCodeException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                logReplayNotFound(tenantContext, command.getTaskId());
                throw ex;
            }
            log.error("回放任务查询失败, tenantId={}, taskId={}, traceId={}, requestId={}, code={}",
                    tenantContext.getTenantId(), command.getTaskId(), tenantContext.getTraceId(),
                    tenantContext.getRequestId(), ex.getErrorCode(), ex);
            incrementReplayResolveMetric("failed");
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    "REPLAY_TASK_QUERY_FAILED",
                    "回放任务查询失败");
        } catch (ResponseStatusException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                logReplayNotFound(tenantContext, command.getTaskId());
                throw new ErrorCodeException(HttpStatus.NOT_FOUND, "REPLAY_NOT_FOUND", "回放任务不存在");
            }
            log.error("回放任务查询失败, tenantId={}, taskId={}, traceId={}, requestId={}, status={}",
                    tenantContext.getTenantId(), command.getTaskId(), tenantContext.getTraceId(),
                    tenantContext.getRequestId(), ex.getStatusCode(), ex);
            incrementReplayResolveMetric("failed");
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    "REPLAY_TASK_QUERY_FAILED",
                    "回放任务查询失败");
        } catch (RuntimeException ex) {
            log.error("回放任务查询异常, tenantId={}, taskId={}, traceId={}, requestId={}",
                    tenantContext.getTenantId(), command.getTaskId(), tenantContext.getTraceId(),
                    tenantContext.getRequestId(), ex);
            incrementReplayResolveMetric("exception");
            throw new ErrorCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "REPLAY_TASK_QUERY_EXCEPTION",
                    "回放任务查询异常");
        }
    }

    private void logReplayNotFound(TenantContext tenantContext, String taskId) {
        log.warn("REPLAY_NOT_FOUND, tenantId={}, userId={}, traceId={}, requestId={}, taskId={}",
                tenantContext.getTenantId(),
                tenantContext.getUserId(),
                tenantContext.getTraceId(),
                tenantContext.getRequestId(),
                taskId);
        incrementReplayResolveMetric("not_found");
    }

    private void incrementReplayResolveMetric(String result) {
        if (governanceTelemetry == null) {
            return;
        }
        governanceTelemetry.increment("replay.resolve_task.total",
                "domain", "replay",
                "action", "resolve_task",
                "result", result);
    }
}
