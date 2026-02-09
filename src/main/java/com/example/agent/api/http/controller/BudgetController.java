package com.example.agent.api.http.controller;

import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.budget.token.application.TokenBudgetManager;
import com.example.agent.budget.token.model.TokenUsageInput;
import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.budget.token.model.TokenUsageSummary;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.common.error.ErrorCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 预算计量接口控制器。
 */
@RestController
@Validated
public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);

    private final TokenBudgetManager tokenBudgetManager;
    private final AuthService authService;

    public BudgetController(TokenBudgetManager tokenBudgetManager, AuthService authService) {
        this.tokenBudgetManager = tokenBudgetManager;
        this.authService = authService;
    }

    /**
     * 记录预算使用。
     *
     * @param input 预算输入
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 预算记录
     */
    @PostMapping("/api/v1/budget/usage")
    public ApiResponse<TokenUsageRecord> recordUsage(@RequestBody TokenUsageInput input,
                                                     @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                     ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        if (input == null || !StringUtils.hasText(input.getUsageId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "计量标识不能为空");
        }
        input.setTenantId(tenantContext.getTenantId());
        TokenUsageRecord record = tokenBudgetManager.recordUsage(input, tenantContext);
        log.info("预算计量完成, tenantId={}, usageId={}, taskId={}",
                tenantContext.getTenantId(), record.getUsageId(), record.getTaskId());
        return ApiResponse.success(record, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询预算汇总。
     *
     * @param taskId 任务标识
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 汇总结果
     */
    @GetMapping("/api/v1/budget/summary")
    public ApiResponse<TokenUsageSummary> summary(@RequestParam("taskId") String taskId,
                                                  @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                  ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        if (!StringUtils.hasText(taskId)) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "任务标识不能为空");
        }
        TokenUsageSummary summary = tokenBudgetManager.summarize(taskId, tenantContext);
        log.info("预算汇总完成, tenantId={}, taskId={}, totalTokens={}",
                tenantContext.getTenantId(), taskId, summary.getTotalTokens());
        return ApiResponse.success(summary, tenantContext.getTraceId(), tenantContext.getRequestId());
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


