package com.example.agent.capabilities.tools.execution.service;

import com.example.agent.capabilities.tools.execution.mapping.ToolExecutionMapper;
import com.example.agent.capabilities.tools.execution.model.ToolExecutionResult;
import com.example.agent.capabilities.tools.execution.model.ToolInvocationPayload;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工具结果组装服务。
 *
 * <p>用途：统一组装执行结果对象，避免执行器散落拼装字段。</p>
 */
@Component
public class ToolResultAssembler {

    private final ToolExecutionMapper executionMapper;

    public ToolResultAssembler(ToolExecutionMapper executionMapper) {
        this.executionMapper = executionMapper;
    }

    /**
     * 组装执行结果。
     *
     * @param toolName 工具名称
     * @param result 执行结果内容
     * @param tokenUsage 计量信息
     * @param cacheHit 缓存命中
     * @param rawRef 原始引用
     * @param digest 结果摘要
     * @return 强类型执行结果
     */
    public ToolExecutionResult assemble(String toolName,
                                        Map<String, Object> result,
                                        Map<String, Object> tokenUsage,
                                        boolean cacheHit,
                                        String rawRef,
                                        String digest) {
        ToolInvocationPayload payload = executionMapper.toInvocationPayload(result);
        return executionMapper.toExecutionResult(toolName, payload, tokenUsage, cacheHit, rawRef, digest);
    }
}

