package com.example.agent.api.http.controller;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.orchestration.multiagent.dag.replay.DagReplayResponse;
import com.example.agent.orchestration.multiagent.dag.replay.DagReplayService;
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
 * DAG 回放查询控制器。
 *
 * <p>用途：按 dagRunId、节点与尝试次数查询 DAG 语义回放帧。</p>
 */
@RestController
@Validated
public class DagReplayController {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagReplayController.class);

    /**
     * DAG 回放服务。
     */
    private final DagReplayService dagReplayService;

    /**
     * 鉴权服务。
     */
    private final AuthService authService;

    public DagReplayController(DagReplayService dagReplayService,
                               AuthService authService) {
        this.dagReplayService = dagReplayService;
        this.authService = authService;
    }

    /**
     * 查询 DAG 回放。
     *
     * @param workflowId 工作流标识
     * @param dagRunId DAG运行标识
     * @param cursor 游标
     * @param size 分页大小
     * @param fromSeq 起始序号
     * @param toSeq 结束序号
     * @param nodeId 节点标识
     * @param attempt 尝试次数
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 回放响应
     */
    @GetMapping("/api/v1/workflows/dag/replay")
    public ApiResponse<DagReplayResponse> replay(@RequestParam("workflowId") String workflowId,
                                                 @RequestParam(value = "dagRunId", required = false) String dagRunId,
                                                 @RequestParam(value = "cursor", required = false) String cursor,
                                                 @RequestParam(value = "size", required = false) Integer size,
                                                 @RequestParam(value = "fromSeq", required = false) Long fromSeq,
                                                 @RequestParam(value = "toSeq", required = false) Long toSeq,
                                                 @RequestParam(value = "nodeId", required = false) String nodeId,
                                                 @RequestParam(value = "attempt", required = false) Integer attempt,
                                                 @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                 ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        // 关键逻辑：调用回放服务聚合审计与事件日志，返回结构化回放帧。
        DagReplayResponse response = dagReplayService.replay(workflowId,
                tenantContext.getTenantId(),
                dagRunId,
                cursor,
                size,
                fromSeq,
                toSeq,
                nodeId,
                attempt);
        log.info("DAG回放查询完成, tenantId={}, workflowId={}, dagRunId={}, frameSize={}",
                tenantContext.getTenantId(),
                workflowId,
                response.getDagRunId(),
                response.getFrames() == null ? 0 : response.getFrames().size());
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
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

