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
import org.springframework.util.StringUtils;
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
        ReplayResolveContext context = validateAndBuildContext(command, tenantContext);
        try {
            TaskRecord record = taskRepository.findById(context.tenantId(), context.taskId());
            if (record == null) {
                throw replayNotFound(context);
            }
            incrementReplayResolveMetric("success");
            String status = record.getStatus() != null ? record.getStatus().value() : null;
            return new ReplayTaskSnapshot(record.getTaskId(), record.getWorkflowId(), status);
        } catch (ErrorCodeException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                if ("REPLAY_NOT_FOUND".equals(ex.getErrorCode())) {
                    throw ex;
                }
                throw replayNotFound(context);
            }
            if (HttpStatus.BAD_REQUEST.equals(ex.getStatusCode())) {
                throw ex;
            }
            log.error("回放任务查询失败, tenantId={}, taskId={}, traceId={}, requestId={}, code={}",
                    context.tenantId(), context.taskId(), context.traceId(),
                    context.requestId(), ex.getErrorCode(), ex);
            incrementReplayResolveMetric("failed");
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    "REPLAY_TASK_QUERY_FAILED",
                    "回放任务查询失败");
        } catch (ResponseStatusException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                throw replayNotFound(context);
            }
            log.error("回放任务查询失败, tenantId={}, taskId={}, traceId={}, requestId={}, status={}",
                    context.tenantId(), context.taskId(), context.traceId(),
                    context.requestId(), ex.getStatusCode(), ex);
            incrementReplayResolveMetric("failed");
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    "REPLAY_TASK_QUERY_FAILED",
                    "回放任务查询失败");
        } catch (RuntimeException ex) {
            log.error("回放任务查询异常, tenantId={}, taskId={}, traceId={}, requestId={}",
                    context.tenantId(), context.taskId(), context.traceId(),
                    context.requestId(), ex);
            incrementReplayResolveMetric("exception");
            throw new ErrorCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "REPLAY_TASK_QUERY_EXCEPTION",
                    "回放任务查询异常");
        }
    }

    /**
     * 校验回放入参并构建解析上下文。
     *
     * @param command 回放命令
     * @param tenantContext 租户上下文
     * @return 解析上下文
     */
    private ReplayResolveContext validateAndBuildContext(ReplayCommand command, TenantContext tenantContext) {
        if (tenantContext == null || !StringUtils.hasText(tenantContext.getTenantId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "REPLAY_TENANT_MISSING",
                    "回放请求缺少租户信息");
        }
        if (command == null || !StringUtils.hasText(command.getTaskId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "REPLAY_TASK_MISSING",
                    "回放请求缺少任务标识");
        }
        return new ReplayResolveContext(
                tenantContext.getTenantId(),
                tenantContext.getUserId(),
                tenantContext.getTraceId(),
                tenantContext.getRequestId(),
                command.getTaskId());
    }

    /**
     * 构建并记录任务不存在错误。
     *
     * @param context 回放上下文
     * @return 错误对象
     */
    private ErrorCodeException replayNotFound(ReplayResolveContext context) {
        log.warn("REPLAY_NOT_FOUND, tenantId={}, userId={}, traceId={}, requestId={}, taskId={}",
                context.tenantId(),
                context.userId(),
                context.traceId(),
                context.requestId(),
                context.taskId());
        incrementReplayResolveMetric("not_found");
        return new ErrorCodeException(HttpStatus.NOT_FOUND, "REPLAY_NOT_FOUND", "回放任务不存在");
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

    /**
     * 回放解析上下文。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param traceId 链路标识
     * @param requestId 请求标识
     * @param taskId 任务标识
     */
    private record ReplayResolveContext(String tenantId,
                                        String userId,
                                        String traceId,
                                        String requestId,
                                        String taskId) {
    }
}
