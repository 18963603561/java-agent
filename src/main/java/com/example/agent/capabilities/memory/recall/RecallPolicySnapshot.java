package com.example.agent.capabilities.memory.recall;

import com.example.agent.capabilities.context.ContextPolicy;
import com.example.agent.capabilities.memory.RetrievalPriority;
import java.util.List;

/**
 * 召回策略快照，封装策略对象与生效后的执行参数。
 */
public class RecallPolicySnapshot {

    /**
     * 原始上下文策略对象，可能为空。
     */
    private final ContextPolicy contextPolicy;

    /**
     * 生效检索优先级。
     */
    private final List<RetrievalPriority> retrievalPriority;

    /**
     * 是否启用敏感信息遮罩。
     */
    private final boolean enableSensitiveMask;

    public RecallPolicySnapshot(ContextPolicy contextPolicy,
                                List<RetrievalPriority> retrievalPriority,
                                boolean enableSensitiveMask) {
        this.contextPolicy = contextPolicy;
        this.retrievalPriority = retrievalPriority;
        this.enableSensitiveMask = enableSensitiveMask;
    }

    public ContextPolicy getContextPolicy() {
        return contextPolicy;
    }

    public List<RetrievalPriority> getRetrievalPriority() {
        return retrievalPriority;
    }

    public boolean isEnableSensitiveMask() {
        return enableSensitiveMask;
    }
}

