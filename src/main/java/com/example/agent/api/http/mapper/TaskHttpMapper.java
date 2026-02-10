package com.example.agent.api.http.mapper;

import com.example.agent.api.http.dto.TaskListResponse;
import com.example.agent.api.http.dto.TaskQuery;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.api.http.dto.TaskResponse;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.orchestration.task.contract.TaskExecutionMode;
import com.example.agent.orchestration.task.contract.TaskListView;
import com.example.agent.orchestration.task.contract.TaskQueryCommand;
import com.example.agent.orchestration.task.contract.TaskStatusView;
import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.orchestration.task.TaskStatus;
import com.example.agent.common.error.ErrorCodeException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 任务 HTTP 映射器。
 * <p>用途：负责 HTTP DTO 与编排层契约对象之间的双向转换，避免控制层向下泄漏传输语义。
 */
@Component
public class TaskHttpMapper {

    /**
     * 非法任务状态错误码。
     */
    private static final String INVALID_TASK_STATUS_CODE = "INVALID_TASK_STATUS";

    /**
     * 非法任务状态错误原因。
     */
    private static final String INVALID_TASK_STATUS_REASON = "invalid_task_status";

    /**
     * 将任务提交请求 DTO 映射为编排层提交命令。
     *
     * @param request HTTP 任务提交请求
     * @return 任务提交命令
     */
    public TaskSubmitCommand toSubmitCommand(TaskRequest request) {
        TaskSubmitCommand command = new TaskSubmitCommand();
        if (request == null) {
            return command;
        }
        command.setQuery(request.getQuery());
        command.setSessionId(request.getSessionId());
        command.setSkillName(request.getSkillName());
        command.setContext(request.getContext());
        command.setIdempotencyKey(request.getIdempotencyKey());
        command.setToolChoice(request.getToolChoice());
        command.setExecutionMode(toExecutionMode(request.getExecutionMode()));
        command.setWaitTimeoutMs(request.getWaitTimeoutMs());
        return command;
    }

    /**
     * 将任务查询请求 DTO 映射为编排层查询命令。
     *
     * @param query HTTP 列表查询参数
     * @return 任务查询命令
     */
    public TaskQueryCommand toQueryCommand(TaskQuery query) {
        TaskQueryCommand command = new TaskQueryCommand();
        if (query == null) {
            return command;
        }
        command.setStatus(parseStatus(query.getStatus()));
        command.setCursor(query.getCursor());
        command.setSize(query.getSize());
        return command;
    }

    /**
     * 将编排层提交结果映射为 HTTP 响应 DTO。
     *
     * @param result 编排层提交结果
     * @return HTTP 提交响应
     */
    public TaskResponse toTaskResponse(TaskSubmissionResult result) {
        if (result == null) {
            return new TaskResponse();
        }
        TaskResponse response = new TaskResponse(result.getTaskId(), result.getWorkflowId(), result.getStatus());
        response.setStreamUrl(result.getStreamUrl());
        response.setResult(result.getResult());
        return response;
    }

    /**
     * 将编排层任务状态映射为 HTTP 状态响应 DTO。
     *
     * @param view 编排层任务状态
     * @return HTTP 任务状态响应
     */
    public TaskStatusResponse toTaskStatusResponse(TaskStatusView view) {
        if (view == null) {
            return new TaskStatusResponse();
        }
        String status = view.getStatus() != null ? view.getStatus().value() : null;
        return new TaskStatusResponse(view.getTaskId(), view.getWorkflowId(), status,
                view.getUpdatedAt(), view.getResult());
    }

    /**
     * 将编排层任务列表映射为 HTTP 列表响应 DTO。
     *
     * @param view 编排层任务列表
     * @return HTTP 列表响应
     */
    public TaskListResponse toTaskListResponse(TaskListView view) {
        if (view == null) {
            return new TaskListResponse(List.of(), null, false, 0L);
        }
        List<TaskStatusResponse> tasks = new ArrayList<>();
        if (view.getTasks() != null) {
            for (TaskStatusView task : view.getTasks()) {
                tasks.add(toTaskStatusResponse(task));
            }
        }
        return new TaskListResponse(tasks, view.getNextCursor(), view.isHasMore(), view.getTotal());
    }

    private TaskExecutionMode toExecutionMode(TaskRequest.ExecutionMode executionMode) {
        if (executionMode == null) {
            return null;
        }
        return executionMode == TaskRequest.ExecutionMode.SYNC
                ? TaskExecutionMode.SYNC
                : TaskExecutionMode.ASYNC;
    }

    /**
     * 解析状态过滤参数。
     */
    private TaskStatus parseStatus(String rawStatus) {
        if (!StringUtils.hasText(rawStatus)) {
            return null;
        }
        TaskStatus parsed = TaskStatus.from(rawStatus);
        if (parsed != null) {
            return parsed;
        }
        throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                INVALID_TASK_STATUS_CODE,
                INVALID_TASK_STATUS_REASON);
    }
}
