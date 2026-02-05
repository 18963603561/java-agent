package com.example.agent.api.http.controller;

import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.history.eventlog.EventLogPage;
import com.example.agent.history.eventlog.EventLogService;
import com.example.agent.history.eventlog.EventQuery;
import com.example.agent.history.timeline.TimelineRequest;
import com.example.agent.history.timeline.TimelineResponse;
import com.example.agent.runtime.engine.StepRuntimeService;
import com.example.agent.runtime.engine.StepTimelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 时间线与事件查询接口。
 */
@RestController
@Validated
public class TimelineController {

    private static final Logger log = LoggerFactory.getLogger(TimelineController.class);

    private final EventLogService eventLogService;
    private final com.example.agent.history.timeline.TimelineService timelineService;
    private final StepRuntimeService stepRuntimeService;
    private final AuthService authService;

    public TimelineController(EventLogService eventLogService,
                              com.example.agent.history.timeline.TimelineService timelineService,
                              StepRuntimeService stepRuntimeService,
                              AuthService authService) {
        this.eventLogService = eventLogService;
        this.timelineService = timelineService;
        this.stepRuntimeService = stepRuntimeService;
        this.authService = authService;
    }

    /**
     * 查询事件日志。
     *
     * @param workflowId 工作流标识
     * @param cursor 游标
     * @param size 分页大小
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 事件日志分页
     */
    @GetMapping("/api/v1/events")
    public ApiResponse<EventLogPage> listEvents(@RequestParam("workflowId") String workflowId,
                                                @RequestParam(value = "cursor", required = false) String cursor,
                                                @RequestParam(value = "size", required = false) Integer size,
                                                @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        EventQuery query = new EventQuery();
        query.setWorkflowId(workflowId);
        query.setCursor(cursor);
        query.setSize(size);
        EventLogPage page = eventLogService.listEvents(query, tenantContext);
        log.info("事件日志查询, tenantId={}, workflowId={}, size={}",
                tenantContext.getTenantId(), workflowId, page.getEvents().size());
        return ApiResponse.success(page, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询时间线。
     *
     * @param workflowId 工作流标识
     * @param mode 模式
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 时间线响应
     */
    @GetMapping("/api/v1/timeline")
    public ApiResponse<TimelineResponse> getTimeline(@RequestParam("workflowId") String workflowId,
                                                     @RequestParam(value = "mode", required = false) String mode,
                                                     @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                     ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        TimelineRequest request = new TimelineRequest();
        request.setWorkflowId(workflowId);
        request.setMode(mode);
        TimelineResponse response = timelineService.getTimeline(request, tenantContext);
        log.info("时间线查询, tenantId={}, workflowId={}, mode={}",
                tenantContext.getTenantId(), workflowId, response.getMode());
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询步骤时间线。
     *
     * @param workflowId 工作流标识
     * @param cursor 游标
     * @param size 分页大小
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 步骤时间线
     */
    @GetMapping("/api/v1/timeline/steps")
    public ApiResponse<StepTimelineResponse> listSteps(@RequestParam("workflowId") String workflowId,
                                                       @RequestParam(value = "cursor", required = false) String cursor,
                                                       @RequestParam(value = "size", required = false) Integer size,
                                                       @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                       ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        StepTimelineResponse response = stepRuntimeService.listSteps(workflowId, cursor, size, tenantContext);
        log.info("步骤时间线查询, tenantId={}, workflowId={}, count={}",
                tenantContext.getTenantId(), workflowId, response.getSteps().size());
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
