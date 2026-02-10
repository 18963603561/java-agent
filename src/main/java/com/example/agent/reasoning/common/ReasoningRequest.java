package com.example.agent.reasoning.common;

import com.example.agent.security.auth.TenantContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 推理统一请求对象。
 *
 * <p>用途：收敛策略执行公共输入，避免不同推理实现重复定义参数。
 */
public class ReasoningRequest {

    private final String strategyType;
    private final String prompt;
    private final ReasoningInput input;
    private final TenantContext tenantContext;
    private final String workflowId;
    private final AtomicLong seqCounter;

    /**
     * 构造推理请求。
     *
     * @param strategyType 推理策略类型
     * @param prompt 主题或问题
     * @param input 输入上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 序列计数器
     */
    public ReasoningRequest(String strategyType,
                            String prompt,
                            Map<String, Object> input,
                            TenantContext tenantContext,
                            String workflowId,
                            AtomicLong seqCounter) {
        this.strategyType = strategyType;
        this.prompt = prompt;
        Map<String, Object> safeInput = input == null ? Collections.emptyMap() : new HashMap<>(input);
        this.input = new ReasoningInput(safeInput);
        this.tenantContext = tenantContext;
        this.workflowId = workflowId;
        this.seqCounter = seqCounter;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public String getPrompt() {
        return prompt;
    }

    public ReasoningInput getInput() {
        return input;
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public AtomicLong getSeqCounter() {
        return seqCounter;
    }
}
