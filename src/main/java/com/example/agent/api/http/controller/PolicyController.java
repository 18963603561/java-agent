package com.example.agent.api.http.controller;

import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.governance.PolicyEvaluateRequest;
import com.example.agent.api.http.dto.governance.PolicyEvaluateResponse;
import com.example.agent.governance.policy.PolicyEngine;
import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.governance.policy.domain.PolicyEvaluationResult;
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
 * 策略评估接口控制器。
 */
@RestController
@Validated
public class PolicyController {

    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    private final PolicyEngine policyEngine;
    private final AuthService authService;

    public PolicyController(PolicyEngine policyEngine, AuthService authService) {
        this.policyEngine = policyEngine;
        this.authService = authService;
    }

    /**
     * 评估策略请求。
     *
     * @param request 策略请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 策略评估结果
     */
    @PostMapping("/api/v1/policy/evaluate")
    public ApiResponse<PolicyEvaluateResponse> evaluate(@RequestBody PolicyEvaluateRequest request,
                                                        @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                        ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        PolicyEvaluationCommand command = new PolicyEvaluationCommand(request.getPolicyId(),
                request.getAction(),
                request.getResource(),
                request.getInput());
        PolicyEvaluationResult result = policyEngine.evaluate(command, tenantContext);
        if (result != null && "DENY".equalsIgnoreCase(result.getDecision())) {
            log.warn("策略拒绝, tenantId={}, userId={}, traceId={}, requestId={}, policyId={}, evaluationId={}",
                    tenantContext.getTenantId(),
                    tenantContext.getUserId(),
                    tenantContext.getTraceId(),
                    tenantContext.getRequestId(),
                    result.getPolicyId(),
                    result.getEvaluationId());
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "POLICY_DENIED",
                    result.getReason() == null ? "策略拒绝" : result.getReason());
        }
        PolicyEvaluateResponse response = new PolicyEvaluateResponse(result != null ? result.getPolicyId() : null,
                result != null ? result.getDecision() : null,
                result != null ? result.getReason() : null,
                result != null ? result.getEvaluationId() : null,
                result != null ? result.getMatchedRules() : null);
        log.info("策略评估完成, tenantId={}, policyId={}, decision={}",
                tenantContext.getTenantId(), response.getPolicyId(), response.getDecision());
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
