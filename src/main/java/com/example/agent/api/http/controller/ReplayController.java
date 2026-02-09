package com.example.agent.api.http.controller;

import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.governance.ReplayRequest;
import com.example.agent.api.http.dto.governance.ReplayResponse;
import com.example.agent.governance.replay.domain.ReplayCommand;
import com.example.agent.governance.replay.domain.ReplayResult;
import com.example.agent.governance.replay.ReplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 回放接口控制器。
 */
@RestController
@Validated
public class ReplayController {

    private static final Logger log = LoggerFactory.getLogger(ReplayController.class);

    private final ReplayService replayService;
    private final AuthService authService;

    public ReplayController(ReplayService replayService, AuthService authService) {
        this.replayService = replayService;
        this.authService = authService;
    }

    /**
     * 执行任务回放。
     *
     * @param request 回放请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 回放结果
     */
    @PostMapping("/api/v1/replay")
    public ApiResponse<ReplayResponse> replay(@RequestBody ReplayRequest request,
                                              @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        ReplayCommand command = new ReplayCommand(request.getTaskId(),
                request.getFromStepId(),
                request.getToStepId(),
                request.getMode());
        ReplayResult result = replayService.replay(command, tenantContext);
        ReplayResponse response = new ReplayResponse(result.getReplayId(),
                result.getStatus(),
                result.getStartedAt(),
                result.getCompletedAt());
        log.info("回放接口完成, tenantId={}, replayId={}, taskId={}",
                tenantContext.getTenantId(), response.getReplayId(), request.getTaskId());
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
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
