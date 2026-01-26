package com.example.agent.agentcore;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具执行器，负责工具调用与结果汇总。
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final ToolCache toolCache;
    private final SandboxExecutor sandboxExecutor;

    public ToolExecutor(ToolRegistry toolRegistry, ToolCache toolCache, SandboxExecutor sandboxExecutor) {
        this.toolRegistry = toolRegistry;
        this.toolCache = toolCache;
        this.sandboxExecutor = sandboxExecutor;
    }

    /**
     * 执行工具调用并返回结果。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param usageId 计量幂等键
     * @return 执行结果
     */
    public Map<String, Object> execute(TaskRequest request, TenantContext tenantContext, String usageId) {
        String toolName = toolRegistry.resolve("default-tool");
        log.info("ToolExecutor invoking tool, tenantId={}, tool={}, usageId={}",
                tenantContext.getTenantId(), toolName, usageId);
        toolCache.put(toolName, "cached");
        // 预算计量链路在 Phase 3 接入，此处仅保留执行结果。
        Map<String, Object> output = sandboxExecutor.execute(toolName, request);
        Map<String, Object> tokenUsage = buildTokenUsage(tenantContext, usageId);
        Map<String, Object> result = new HashMap<>();
        result.put("tool", toolName);
        result.put("result", output);
        result.put("tokenUsage", tokenUsage);
        return result;
    }

    private Map<String, Object> buildTokenUsage(TenantContext tenantContext, String usageId) {
        Map<String, Object> tokenUsage = new HashMap<>();
        tokenUsage.put("usageId", usageId);
        tokenUsage.put("tenantId", tenantContext.getTenantId());
        tokenUsage.put("inputTokens", 0);
        tokenUsage.put("outputTokens", 0);
        tokenUsage.put("totalTokens", 0);
        tokenUsage.put("costUsd", 0);
        return tokenUsage;
    }
}
