package com.example.agent.gateway.controller;

import com.example.agent.auth.AuthService;
import com.example.agent.auth.TenantContext;
import com.example.agent.auth.UserContext;
import com.example.agent.common.ApiResponse;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.scheduler.ScheduleManager;
import com.example.agent.scheduler.SchedulePage;
import com.example.agent.scheduler.ScheduleQuery;
import com.example.agent.scheduler.ScheduleResponse;
import com.example.agent.scheduler.ScheduleSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 调度任务接口控制器。
 */
@RestController
@Validated
public class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);

    private final ScheduleManager scheduleManager;
    private final AuthService authService;

    public ScheduleController(ScheduleManager scheduleManager, AuthService authService) {
        this.scheduleManager = scheduleManager;
        this.authService = authService;
    }

    /**
     * 创建调度任务。
     *
     * @param spec 调度定义
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 创建结果
     */
    @PostMapping("/api/v1/schedules")
    public ApiResponse<ScheduleResponse> create(@RequestBody ScheduleSpec spec,
                                                @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        ScheduleResponse response = scheduleManager.create(spec, tenantContext);
        log.info("调度创建完成, tenantId={}, scheduleId={}",
                tenantContext.getTenantId(), response.getScheduleId());
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 更新调度任务。
     *
     * @param spec 调度定义
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 更新结果
     */
    @PutMapping("/api/v1/schedules")
    public ApiResponse<ScheduleResponse> update(@RequestBody ScheduleSpec spec,
                                                @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        ScheduleResponse response = scheduleManager.update(spec, tenantContext);
        log.info("调度更新完成, tenantId={}, scheduleId={}",
                tenantContext.getTenantId(), response.getScheduleId());
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询调度任务列表。
     *
     * @param status 状态过滤
     * @param cursor 游标
     * @param size 分页大小
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 调度分页结果
     */
    @GetMapping("/api/v1/schedules")
    public ApiResponse<SchedulePage> list(@RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "cursor", required = false) String cursor,
                                          @RequestParam(value = "size", required = false) Integer size,
                                          @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        ScheduleQuery query = new ScheduleQuery();
        query.setStatus(status);
        query.setCursor(cursor);
        query.setSize(size);
        SchedulePage page = scheduleManager.list(query, tenantContext);
        log.info("调度查询完成, tenantId={}, count={}",
                tenantContext.getTenantId(), page.getSchedules().size());
        return ApiResponse.success(page, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 暂停调度任务。
     *
     * @param scheduleId 调度标识
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 暂停结果
     */
    @PostMapping("/api/v1/schedules/{scheduleId}/pause")
    public ApiResponse<ScheduleResponse> pause(@PathVariable("scheduleId") String scheduleId,
                                               @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                               ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        ScheduleResponse response = scheduleManager.pause(scheduleId, tenantContext);
        log.info("调度暂停完成, tenantId={}, scheduleId={}",
                tenantContext.getTenantId(), scheduleId);
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 恢复调度任务。
     *
     * @param scheduleId 调度标识
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 恢复结果
     */
    @PostMapping("/api/v1/schedules/{scheduleId}/resume")
    public ApiResponse<ScheduleResponse> resume(@PathVariable("scheduleId") String scheduleId,
                                                @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        ScheduleResponse response = scheduleManager.resume(scheduleId, tenantContext);
        log.info("调度恢复完成, tenantId={}, scheduleId={}",
                tenantContext.getTenantId(), scheduleId);
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 删除调度任务。
     *
     * @param scheduleId 调度标识
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 删除结果
     */
    @DeleteMapping("/api/v1/schedules/{scheduleId}")
    public ApiResponse<Void> delete(@PathVariable("scheduleId") String scheduleId,
                                    @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                    ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        scheduleManager.delete(scheduleId, tenantContext);
        log.info("调度删除完成, tenantId={}, scheduleId={}",
                tenantContext.getTenantId(), scheduleId);
        return ApiResponse.success(null, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    private TenantContext authenticate(ServerWebExchange exchange, String apiKey) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        return tenantContext;
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}
