package com.example.agent.api.http.controller;

import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.api.http.dto.governance.ToolApprovalDecisionRequest;
import com.example.agent.api.http.dto.governance.ToolApprovalDecisionResponse;
import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.observability.TracingPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 工具审批决策控制器。
 */
@RestController
@Validated
public class ToolApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ToolApprovalController.class);

    private final ApprovalService approvalService;
    private final AuthService authService;
    private final TracingPublisher tracingPublisher;

    public ToolApprovalController(ApprovalService approvalService,
                                  AuthService authService,
                                  TracingPublisher tracingPublisher) {
        this.approvalService = approvalService;
        this.authService = authService;
        this.tracingPublisher = tracingPublisher;
    }

    /**
     * 写入工具审批决策。
     *
     * @param decisionRequest 决策请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 决策结果
     */
    @PostMapping("/api/approvals/decide")
    public ApiResponse<ToolApprovalDecisionResponse> decide(@RequestBody ToolApprovalDecisionRequest decisionRequest,
                                                            @RequestHeader(value = "X-API-Key", required = false)
                                                            String apiKey,
                                                            ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);

        if (decisionRequest == null || !StringUtils.hasText(decisionRequest.getRequestId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "APPROVAL_REQUEST_MISSING", "requestId 不能为空");
        }
        if (decisionRequest.getApproved() == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "APPROVAL_DECISION_MISSING", "approved 不能为空");
        }
        if (!StringUtils.hasText(decisionRequest.getTenantId())
                || !decisionRequest.getTenantId().equals(tenantContext.getTenantId())) {
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "APPROVAL_TENANT_MISMATCH", "租户不匹配");
        }

        approvalService.decide(decisionRequest.getTenantId(),
                decisionRequest.getWorkflowId(),
                decisionRequest.getRequestId(),
                decisionRequest.getApproved(),
                decisionRequest.getReason());

        ToolApprovalDecisionResponse response = new ToolApprovalDecisionResponse(
                decisionRequest.getRequestId(),
                decisionRequest.getApproved(),
                decisionRequest.getReason());
        log.info("工具审批决策已提交, tenantId={}, workflowId={}, requestId={}, approved={}",
                decisionRequest.getTenantId(),
                decisionRequest.getWorkflowId(),
                decisionRequest.getRequestId(),
                decisionRequest.getApproved());
        return ApiResponse.success(response, resolveTraceId(tenantContext), tenantContext.getRequestId());
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }

    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTraceId())) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }
}
