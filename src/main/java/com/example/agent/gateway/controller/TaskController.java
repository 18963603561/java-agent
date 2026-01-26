package com.example.agent.gateway.controller;

import com.example.agent.auth.AuthService;
import com.example.agent.auth.TenantContext;
import com.example.agent.auth.UserContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.common.ApiResponse;
import com.example.agent.common.TaskListResponse;
import com.example.agent.common.TaskQuery;
import com.example.agent.common.TaskRequest;
import com.example.agent.common.TaskResponse;
import com.example.agent.common.TaskStatusResponse;
import com.example.agent.orchestrator.TaskQueryService;
import com.example.agent.orchestrator.TaskSubmissionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
 */
@RestController
@Validated
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskSubmissionService taskSubmissionService;
    private final TaskQueryService taskQueryService;
    private final AuthService authService;

    public TaskController(TaskSubmissionService taskSubmissionService,
                          TaskQueryService taskQueryService,
                          AuthService authService) {
        this.taskSubmissionService = taskSubmissionService;
        this.taskQueryService = taskQueryService;
        this.authService = authService;
    }

    /**
     * 提交任务。
     *
     * @param request 任务请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 任务响应
     */
    @PostMapping("/api/v1/tasks")
    public ApiResponse<TaskResponse> submitTask(@Valid @RequestBody TaskRequest request,
                                                @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        TaskResponse response = taskSubmissionService.submitTask(request, tenantContext);
        log.info("任务提交完成, tenantId={}, userId={}, taskId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(), response.getTaskId());
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询任务状态。
     *
     * @param taskId 任务标识
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 任务状态
     */
    @GetMapping("/api/v1/tasks/{taskId}")
    public ApiResponse<TaskStatusResponse> getTask(@PathVariable("taskId") String taskId,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        TaskStatusResponse response = taskQueryService.getTask(taskId, tenantContext);
        log.info("任务状态查询, tenantId={}, userId={}, taskId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(), taskId);
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询任务列表。
     *
     * @param query 查询参数
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 任务列表
     */
    @GetMapping("/api/v1/tasks")
    public ApiResponse<TaskListResponse> listTasks(@Valid TaskQuery query,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        TaskListResponse response = taskQueryService.listTasks(query, tenantContext);
        log.info("任务列表查询, tenantId={}, userId={}", tenantContext.getTenantId(), tenantContext.getUserId());
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}
