package com.example.agent.api.http.controller;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.orchestration.multiagent.dag.diagnosis.DagDiagnosisReport;
import com.example.agent.orchestration.multiagent.dag.diagnosis.DagDiagnosisService;
import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
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
 * DAG 诊断查询控制器。
 *
 * <p>用途：按 dagRunId 输出结构化诊断报告。</p>
 */
@RestController
@Validated
public class DagDiagnosisController {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagDiagnosisController.class);

    /**
     * DAG 诊断服务。
     */
    private final DagDiagnosisService dagDiagnosisService;

    /**
     * 鉴权服务。
     */
    private final AuthService authService;

    public DagDiagnosisController(DagDiagnosisService dagDiagnosisService,
                                  AuthService authService) {
        this.dagDiagnosisService = dagDiagnosisService;
        this.authService = authService;
    }

    /**
     * 查询 DAG 诊断报告。
     *
     * @param workflowId 工作流标识
     * @param dagRunId DAG运行标识
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 诊断报告
     */
    @GetMapping("/api/v1/workflows/dag/diagnosis")
    public ApiResponse<DagDiagnosisReport> diagnosis(@RequestParam("workflowId") String workflowId,
                                                     @RequestParam("dagRunId") String dagRunId,
                                                     @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                     ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        // 关键逻辑：按工作流与运行ID生成诊断报告。
        DagDiagnosisReport report = dagDiagnosisService.diagnose(workflowId, dagRunId);
        log.info("DAG诊断查询完成, tenantId={}, workflowId={}, dagRunId={}, status={}",
                tenantContext.getTenantId(),
                workflowId,
                dagRunId,
                report.getStatus());
        return ApiResponse.success(report, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 统一鉴权并注入租户上下文。
     */
    private TenantContext authenticate(ServerWebExchange exchange, String apiKey) {
        // 关键逻辑：先鉴权获取用户身份，再补齐租户上下文中的用户信息。
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        return tenantContext;
    }

    /**
     * 获取租户上下文。
     */
    private TenantContext getTenantContext(ServerWebExchange exchange) {
        // 关键逻辑：未注入租户上下文时直接拒绝请求，防止越权查询。
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}

