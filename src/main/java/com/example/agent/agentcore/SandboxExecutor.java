package com.example.agent.agentcore;

import com.example.agent.common.TaskRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 沙箱执行器，预留隔离执行扩展点。
 */
@Component
public class SandboxExecutor {

    /**
     * 执行工具调用。
     *
     * @param toolName 工具名称
     * @param request 任务请求
     * @return 执行结果
     */
    public Map<String, Object> execute(String toolName, TaskRequest request) {
        return Map.of("tool", toolName, "result", "ok");
    }
}
