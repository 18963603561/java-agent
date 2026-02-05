package com.example.agent.capabilities.tools.sandbox;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.sandbox.SandboxRequest;
import com.example.agent.capabilities.tools.sandbox.SandboxResult;
import com.example.agent.capabilities.tools.sandbox.WasiSandboxExecutor;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 沙箱执行器适配器，用于统一沙箱校验与执行结果返回。
 */
@Component
public class SandboxExecutor {

    private final WasiSandboxExecutor wasiSandboxExecutor;

    public SandboxExecutor(WasiSandboxExecutor wasiSandboxExecutor) {
        this.wasiSandboxExecutor = wasiSandboxExecutor;
    }

    /**
     * 执行工具调用并经过沙箱校验。
     *
     * @param toolName 工具名称
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param arguments 工具参数
     * @return 沙箱执行结果
     */
    public SandboxResult execute(String toolName,
                                 TaskRequest request,
                                 TenantContext tenantContext,
                                 Map<String, Object> arguments) {
        SandboxRequest sandboxRequest = new SandboxRequest();
        sandboxRequest.setToolName(toolName);
        sandboxRequest.setInput(arguments);
        return wasiSandboxExecutor.execute(sandboxRequest, tenantContext);
    }
}
