package com.example.agent.capabilities.context.assembly;

import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 上下文摘要注入策略。
 *
 * <p>用途：统一控制压缩摘要向提示词装配输入的注入行为，隔离装配流程与注入细节。</p>
 */
public interface ContextSummaryInjectionPolicy {

    /**
     * 执行摘要注入。
     *
     * @param input 装配输入
     * @param snapshot 上下文快照
     * @param compressionResult 压缩结果
     */
    void inject(PromptAssemblyInput input,
                ContextSnapshot snapshot,
                ContextCompressionResult compressionResult);
}


