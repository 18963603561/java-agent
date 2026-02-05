package com.example.agent.api.http.controller;

import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.api.http.dto.TaskListResponse;
import com.example.agent.api.http.dto.TaskQuery;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.api.http.dto.TaskResponse;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.orchestration.task.TaskQueryService;
import com.example.agent.orchestration.task.TaskSubmissionService;
import com.example.agent.streaming.observability.TracingPublisher;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.util.StringUtils;

/**
 * 任务控制器，提供任务提交与查询接口。
 * <p>用途：承接外部请求，完成鉴权、租户上下文装配与响应包装。
 * <p>输入：请求体与请求头中的接口密钥。
 * <p>输出：统一响应对象，包含任务信息与链路标识。
 * <p>边界：当租户上下文缺失时直接抛出异常；鉴权失败由鉴权服务统一处理。
 * <p>示例：
 * <pre>{@code
 * POST /api/v1/tasks
 * Header: X-API-Key=demo-key
 * Body: {"query":"查询库存"}
 * }</pre>
 */
@RestController
@Validated
public class TaskController {

    /**
     * 日志记录器，用于记录关键请求节点。
     * <p>示例：记录租户标识、用户标识与任务标识。
     */
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    /**
     * 任务提交服务。
     * <p>示例：用于提交新任务并返回任务标识。
     */
    private final TaskSubmissionService taskSubmissionService;
    /**
     * 任务查询服务。
     * <p>示例：用于查询任务状态与任务列表。
     */
    private final TaskQueryService taskQueryService;
    /**
     * 鉴权服务。
     * <p>示例：解析请求头并返回用户上下文。
     */
    private final AuthService authService;
    /**
     * 链路跟踪发布器。
     * <p>示例：在缺少跟踪标识时补充当前链路标识。
     */
    private final TracingPublisher tracingPublisher;

    /**
     * 构造任务控制器。
     *
     * @param taskSubmissionService 任务提交服务
     * @param taskQueryService 任务查询服务
     * @param authService 鉴权服务
     * @param tracingPublisher 链路跟踪发布器
     */
    public TaskController(TaskSubmissionService taskSubmissionService,
                          TaskQueryService taskQueryService,
                          AuthService authService,
                          TracingPublisher tracingPublisher) {
        this.taskSubmissionService = taskSubmissionService;
        this.taskQueryService = taskQueryService;
        this.authService = authService;
        this.tracingPublisher = tracingPublisher;
    }

    /**
     * 提交任务。
     *
     * <p>输入：任务请求与接口密钥。
     * <p>输出：包含任务标识与状态的响应对象。
     * <p>边界：当租户上下文缺失时抛出异常。
     * <p>示例：
     * <pre>{@code
     * TaskRequest request = new TaskRequest();
     * request.setQuery("统计订单");
     * }</pre>
     *
     * @param request 任务请求
     * @param apiKey 接口密钥
     * @param exchange 请求上下文
     * @return 任务响应
     */
    @PostMapping("/api/v1/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> submitTask(@Valid @RequestBody TaskRequest request,
                                                                @RequestHeader(value = "X-API-Key", required = false)
                                                                String apiKey,
                                                                ServerWebExchange exchange) {
        // 鉴权并获取用户上下文，确保后续流程具备身份信息。
        UserContext userContext = authService.authenticate(apiKey, exchange);
        // 解析租户上下文并写入用户信息，保障多租户隔离。
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        // 提交任务进入编排流程。
        TaskResponse response = taskSubmissionService.submitTask(request, tenantContext);
        // 记录关键链路日志，便于排查提交结果。
        log.info("任务提交完成, tenantId={}, userId={}, taskId={}, traceId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(), response.getTaskId(),
                resolveTraceId(tenantContext));
        HttpStatus status = resolveSubmitStatus(request, response);
        return ResponseEntity.status(status)
                .body(ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId()));
    }

    private HttpStatus resolveSubmitStatus(TaskRequest request, TaskResponse response) {
        if (request != null && request.getExecutionMode() == TaskRequest.ExecutionMode.SYNC) {
            if (response != null && isTerminalStatus(response.getStatus())) {
                return HttpStatus.OK;
            }
            return HttpStatus.ACCEPTED;
        }
        return HttpStatus.ACCEPTED;
    }

    private boolean isTerminalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String upper = status.toUpperCase();
        return "COMPLETED".equals(upper) || "FAILED".equals(upper);
    }

    /**
     * 查询任务状态。
     *
     * <p>输入：任务标识与接口密钥。
     * <p>输出：任务状态与最后更新时间。
     * <p>边界：任务不存在时由查询服务抛出异常。
     * <p>示例：
     * <pre>{@code
     * GET /api/v1/tasks/{taskId}
     * }</pre>
     *
     * @param taskId 任务标识
     * @param apiKey 接口密钥
     * @param exchange 请求上下文
     * @return 任务状态
     */
    @GetMapping("/api/v1/tasks/{taskId}")
    public ApiResponse<TaskStatusResponse> getTask(@PathVariable("taskId") String taskId,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        // 鉴权并加载租户上下文。
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        // 查询任务状态。
        TaskStatusResponse response = taskQueryService.getTask(taskId, tenantContext);
        // 输出查询日志，便于追踪链路。
        log.info("任务状态查询, tenantId={}, userId={}, taskId={}, traceId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(), taskId,
                resolveTraceId(tenantContext));
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询任务列表。
     *
     * <p>输入：分页与过滤条件。
     * <p>输出：任务列表与分页游标。
     * <p>边界：当查询为空时返回默认列表。
     * <p>示例：
     * <pre>{@code
     * GET /api/v1/tasks?status=RUNNING&size=20
     * }</pre>
     *
     * @param query 查询参数
     * @param apiKey 接口密钥
     * @param exchange 请求上下文
     * @return 任务列表
     */
    @GetMapping("/api/v1/tasks")
    public ApiResponse<TaskListResponse> listTasks(@Valid TaskQuery query,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        // 鉴权并装配租户上下文。
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        // 拉取任务列表。
        TaskListResponse response = taskQueryService.listTasks(query, tenantContext);
        // 输出列表查询日志。
        log.info("任务列表查询, tenantId={}, userId={}, traceId={}",
                tenantContext.getTenantId(), tenantContext.getUserId(),
                resolveTraceId(tenantContext));
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 从请求上下文中提取租户信息。
     *
     * <p>输入：请求上下文对象。
     * <p>输出：租户上下文。
     * <p>边界：缺失时抛出错误，避免后续流程失去租户隔离。
     * <p>示例：
     * <pre>{@code
     * TenantContext context = getTenantContext(exchange);
     * }</pre>
     *
     * @param exchange 请求上下文
     * @return 租户上下文
     */
    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            // 无租户上下文直接失败，防止跨租户访问。
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }

    /**
     * 解析链路跟踪标识。
     *
     * <p>输入：租户上下文。
     * <p>输出：跟踪标识字符串。
     * <p>边界：当上下文缺失时使用当前链路标识。
     * <p>示例：
     * <pre>{@code
     * String traceId = resolveTraceId(context);
     * }</pre>
     *
     * @param tenantContext 租户上下文
     * @return 跟踪标识
     */
    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTraceId())) {
            return tenantContext.getTraceId();
        }
        // 兜底使用当前跟踪标识。
        return tracingPublisher.currentTraceId();
    }
}
