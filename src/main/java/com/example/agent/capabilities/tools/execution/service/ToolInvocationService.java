package com.example.agent.capabilities.tools.execution.service;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.execution.mapping.ToolFieldKeys;
import com.example.agent.capabilities.tools.mcp.McpToolCallRequest;
import com.example.agent.capabilities.tools.mcp.McpToolCallResponse;
import com.example.agent.capabilities.tools.mcp.McpToolClient;
import com.example.agent.capabilities.tools.sandbox.SandboxExecutor;
import com.example.agent.capabilities.tools.sandbox.SandboxResult;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工具调用服务。
 *
 * <p>用途：封装沙箱执行、MCP 调用与重试逻辑。</p>
 */
@Component
public class ToolInvocationService {

    /**
     * 执行单次工具调用并返回合并结果。
     *
     * @param sandboxExecutor 沙箱执行器
     * @param mcpToolClient MCP 客户端
     * @param taskRequest 任务请求
     * @param tenantContext 租户上下文
     * @param resolvedTool 解析后的工具名
     * @param callRequest 调用请求
     * @param arguments 调用参数
     * @return 合并后的工具结果
     */
    public Map<String, Object> invoke(SandboxExecutor sandboxExecutor,
                                      McpToolClient mcpToolClient,
                                      TaskRequest taskRequest,
                                      TenantContext tenantContext,
                                      String resolvedTool,
                                      McpToolCallRequest callRequest,
                                      Map<String, Object> arguments) {
        SandboxResult sandboxResult = sandboxExecutor.execute(resolvedTool, taskRequest, tenantContext, arguments);
        McpToolCallResponse callResponse = mcpToolClient.callTool(callRequest, tenantContext);
        return mergeResult(callResponse, sandboxResult);
    }

    private Map<String, Object> mergeResult(McpToolCallResponse callResponse, SandboxResult sandboxResult) {
        Map<String, Object> toolResult = callResponse != null ? callResponse.getResult() : null;
        Map<String, Object> merged = new HashMap<>();
        if (toolResult != null) {
            merged.putAll(toolResult);
        }
        if (sandboxResult != null && sandboxResult.getOutput() != null) {
            merged.put(ToolFieldKeys.SANDBOX, sandboxResult.getOutput());
        }
        if (sandboxResult != null && sandboxResult.getStatus() != null) {
            merged.put(ToolFieldKeys.SANDBOX_STATUS, sandboxResult.getStatus());
        }
        return merged;
    }
}
