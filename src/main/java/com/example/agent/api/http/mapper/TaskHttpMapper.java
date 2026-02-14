package com.example.agent.api.http.mapper;

import com.example.agent.api.http.config.ApiResponseProperties;
import com.example.agent.api.http.dto.TaskListResponse;
import com.example.agent.api.http.dto.TaskQuery;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.api.http.dto.TaskResponse;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.api.http.response.ResponseMode;
import com.example.agent.api.http.response.ResultPayloadCompactor;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.orchestration.task.TaskStatus;
import com.example.agent.orchestration.task.contract.TaskExecutionMode;
import com.example.agent.orchestration.task.contract.TaskListView;
import com.example.agent.orchestration.task.contract.TaskQueryCommand;
import com.example.agent.orchestration.task.contract.TaskStatusView;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 任务 HTTP 映射器。
 * <p>用途：负责 HTTP DTO 与编排层契约对象之间的双向转换，并统一处理响应展示语义。
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
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(TaskHttpMapper.class);

    /**
     * JSON 转换器，用于展示层组装步骤序号。
     */
    private final ObjectMapper objectMapper;

    /**
     * 结果瘦身处理器。
     */
    private final ResultPayloadCompactor resultPayloadCompactor;

    /**
     * 默认响应模式。
     */
    private final ResponseMode defaultResponseMode;

    public TaskHttpMapper(ObjectMapper objectMapper,
                          ResultPayloadCompactor resultPayloadCompactor,
                          ApiResponseProperties apiResponseProperties) {
        this.objectMapper = objectMapper;
        this.resultPayloadCompactor = resultPayloadCompactor;
        ResponseMode configuredDefaultMode = apiResponseProperties == null
                ? null
                : apiResponseProperties.getDefaultMode();
        this.defaultResponseMode = configuredDefaultMode == null
                ? ResponseMode.COMPACT
                : configuredDefaultMode;
    }

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
     * <p>默认采用配置中的响应模式。
     *
     * @param result 编排层提交结果
     * @return HTTP 提交响应
     */
    public TaskResponse toTaskResponse(TaskSubmissionResult result) {
        return toTaskResponse(result, null);
    }

    /**
     * 将编排层提交结果映射为 HTTP 响应 DTO。
     *
     * @param result 编排层提交结果
     * @param responseMode 响应模式，支持 compact/full
     * @return HTTP 提交响应
     */
    public TaskResponse toTaskResponse(TaskSubmissionResult result, String responseMode) {
        if (result == null) {
            return new TaskResponse();
        }
        TaskResponse response = new TaskResponse(result.getTaskId(), result.getWorkflowId(), result.getStatus());
        response.setStreamUrl(result.getStreamUrl());
        response.setResult(processResultPayload(result.getResult(), responseMode));
        return response;
    }

    /**
     * 将编排层任务状态映射为 HTTP 状态响应 DTO。
     * <p>默认采用配置中的响应模式。
     *
     * @param view 编排层任务状态
     * @return HTTP 任务状态响应
     */
    public TaskStatusResponse toTaskStatusResponse(TaskStatusView view) {
        return toTaskStatusResponse(view, null);
    }

    /**
     * 将编排层任务状态映射为 HTTP 状态响应 DTO。
     *
     * @param view 编排层任务状态
     * @param responseMode 响应模式，支持 compact/full
     * @return HTTP 任务状态响应
     */
    public TaskStatusResponse toTaskStatusResponse(TaskStatusView view, String responseMode) {
        if (view == null) {
            return new TaskStatusResponse();
        }
        TaskStatus statusEnum = view.getStatus();
        String status = statusEnum != null ? statusEnum.value() : null;
        return new TaskStatusResponse(
                view.getTaskId(),
                view.getWorkflowId(),
                status,
                view.getUpdatedAt(),
                processResultPayload(view.getResult(), responseMode)
        );
    }

    /**
     * 将编排层任务列表映射为 HTTP 列表响应 DTO。
     * <p>默认采用配置中的响应模式。
     *
     * @param view 编排层任务列表
     * @return HTTP 列表响应
     */
    public TaskListResponse toTaskListResponse(TaskListView view) {
        return toTaskListResponse(view, null);
    }

    /**
     * 将编排层任务列表映射为 HTTP 列表响应 DTO。
     *
     * @param view 编排层任务列表
     * @param responseMode 响应模式，支持 compact/full
     * @return HTTP 列表响应
     */
    public TaskListResponse toTaskListResponse(TaskListView view, String responseMode) {
        if (view == null) {
            return new TaskListResponse(List.of(), null, false, 0L);
        }
        List<TaskStatusResponse> tasks = new ArrayList<>();
        if (view.getTasks() != null) {
            for (TaskStatusView task : view.getTasks()) {
                tasks.add(toTaskStatusResponse(task, responseMode));
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
     * 处理任务结果输出。
     * <p>处理顺序：先补充展示层 stepIndex，再根据响应模式决定是否 compact 瘦身。
     */
    private Map<String, Object> processResultPayload(Map<String, Object> rawResult, String responseMode) {
        Map<String, Object> indexedResult = appendStepIndex(rawResult);
        ResponseMode resolvedMode = resolveResponseMode(responseMode);
        // 核心流程分支：full 模式直接返回原结构，compact 模式执行去空与去重。
        if (resolvedMode == ResponseMode.FULL) {
            return indexedResult;
        }
        Map<String, Object> compacted = resultPayloadCompactor.compact(indexedResult);
        log.debug("任务响应采用 compact 模式输出, hasResult={}", compacted != null);
        return compacted;
    }

    /**
     * 解析响应模式。
     * <p>非法值回退到默认模式，并记录告警日志，避免影响主流程可用性。
     */
    private ResponseMode resolveResponseMode(String responseMode) {
        if (!StringUtils.hasText(responseMode)) {
            return defaultResponseMode;
        }
        ResponseMode parsed = ResponseMode.from(responseMode);
        if (parsed != null) {
            return parsed;
        }
        // 业务条件判断：外部传入非法模式时回退默认模式，保证接口稳定输出。
        log.warn("非法 responseMode，回退默认模式。responseMode={}, defaultMode={}",
                responseMode, defaultResponseMode.value());
        return defaultResponseMode;
    }

    /**
     * 追加展示层连续步骤编号。
     *
     * <p>输入：任务结果映射，包含 steps 列表。
     * <p>输出：带 stepIndex 的结果副本。
     * <p>边界：结果或 steps 缺失时原样返回。
     *
     * @param result 任务结果映射
     * @return 追加步骤编号后的结果映射
     */
    private Map<String, Object> appendStepIndex(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        Object stepsObj = result.get("steps");
        if (!(stepsObj instanceof List<?> steps)) {
            return result;
        }

        Map<String, Object> copied = new LinkedHashMap<>(result);
        List<Map<String, Object>> indexedSteps = new ArrayList<>();
        int index = 1;
        // 复杂循环：遍历每个步骤并补充连续编号，避免对 seq 的稀疏值产生误解。
        for (Object step : steps) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stepMap = objectMapper.convertValue(step, Map.class);
            if (stepMap == null) {
                stepMap = new LinkedHashMap<>();
            }
            stepMap.put("stepIndex", index);
            indexedSteps.add(stepMap);
            index++;
        }
        copied.put("steps", indexedSteps);
        return copied;
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
