package com.example.agent.api.http.controller;

import com.example.agent.api.http.dto.TaskListResponse;
import com.example.agent.api.http.dto.TaskQuery;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.api.http.dto.TaskResponse;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.api.http.mapper.TaskHttpMapper;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.orchestration.task.TaskQueryService;
import com.example.agent.orchestration.task.TaskSubmissionService;
import com.example.agent.orchestration.task.contract.TaskExecutionMode;
import com.example.agent.orchestration.task.contract.TaskListView;
import com.example.agent.orchestration.task.contract.TaskStatusView;
import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.streaming.observability.TracingPublisher;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 任务控制器，提供任务提交与查询接口。
 * <p>用途：承接外部请求，完成鉴权、租户上下文装配、DTO 映射与响应封装。
 * <p>输入：HTTP 请求头与请求体参数。
 * <p>输出：统一 API 响应对象。
 * <p>边界：缺失租户上下文时直接抛出 TENANT_MISSING 错误。
 */
@RestController
@Validated
public class TaskController {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    /**
     * 任务提交服务。
     */
    private final TaskSubmissionService taskSubmissionService;

    /**
     * 任务查询服务。
     */
    private final TaskQueryService taskQueryService;

    /**
     * 鉴权服务。
     */
    private final AuthService authService;

    /**
     * HTTP 映射器。
     */
    private final TaskHttpMapper taskHttpMapper;

    /**
     * 链路追踪发布器。
     */
    private final TracingPublisher tracingPublisher;

    public TaskController(TaskSubmissionService taskSubmissionService,
                          TaskQueryService taskQueryService,
                          AuthService authService,
                          TaskHttpMapper taskHttpMapper,
                          TracingPublisher tracingPublisher) {
        this.taskSubmissionService = taskSubmissionService;
        this.taskQueryService = taskQueryService;
        this.authService = authService;
        this.taskHttpMapper = taskHttpMapper;
        this.tracingPublisher = tracingPublisher;
    }

    /**
     * 提交任务。
     *
     * @param request 请求体
     * @param apiKey 接口密钥
     * @param exchange Web 请求上下文
     * @return 提交结果
     */
    @PostMapping("/api/v1/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> submitTask(@Valid @RequestBody TaskRequest request,
                                                                @RequestHeader(value = "X-API-Key", required = false)
                                                                String apiKey,
                                                                ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);

        TaskSubmitCommand command = taskHttpMapper.toSubmitCommand(request);
        TaskSubmissionResult submitResult = taskSubmissionService.submitTask(command, tenantContext);
        TaskResponse response = taskHttpMapper.toTaskResponse(submitResult);

        log.info("任务提交完成, tenantId={}, userId={}, taskId={}, traceId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(), response.getTaskId(),
                resolveTraceId(tenantContext));
        HttpStatus status = resolveSubmitStatus(command, submitResult);
        return ResponseEntity.status(status)
                .body(ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId()));
    }

    /**
     * 查询任务状态。
     *
     * @param taskId 任务标识
     * @param apiKey 接口密钥
     * @param exchange Web 请求上下文
     * @return 任务状态
     */
    @GetMapping("/api/v1/tasks/{taskId}")
    public ApiResponse<TaskStatusResponse> getTask(@PathVariable("taskId") String taskId,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);

        TaskStatusView view = taskQueryService.getTask(taskId, tenantContext);
        TaskStatusResponse response = taskHttpMapper.toTaskStatusResponse(view);
        log.info("任务状态查询, tenantId={}, userId={}, taskId={}, traceId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(), taskId,
                resolveTraceId(tenantContext));
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询任务列表。
     *
     * @param query 查询参数
     * @param apiKey 接口密钥
     * @param exchange Web 请求上下文
     * @return 任务列表
     */
    @GetMapping("/api/v1/tasks")
    public ApiResponse<TaskListResponse> listTasks(@Valid TaskQuery query,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);

        TaskListView view = taskQueryService.listTasks(taskHttpMapper.toQueryCommand(query), tenantContext);
        TaskListResponse response = taskHttpMapper.toTaskListResponse(view);
        log.info("任务列表查询, tenantId={}, userId={}, traceId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(), resolveTraceId(tenantContext));
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 判断提交响应状态码。
     * <p>同步模式下若任务已终态则返回 200，否则返回 202。
     */
    private HttpStatus resolveSubmitStatus(TaskSubmitCommand command, TaskSubmissionResult result) {
        if (command != null && command.getExecutionMode() == TaskExecutionMode.SYNC) {
            if (result != null && isTerminalStatus(result.getStatus())) {
                return HttpStatus.OK;
            }
            return HttpStatus.ACCEPTED;
        }
        return HttpStatus.ACCEPTED;
    }

    /**
     * 判断状态是否终态。
     */
    private boolean isTerminalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String upper = status.toUpperCase();
        return "COMPLETED".equals(upper) || "FAILED".equals(upper);
    }

    /**
     * 获取租户上下文。
     */
    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }

    /**
     * 解析链路追踪标识。
     */
    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTraceId())) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }
}

