package com.example.agent.budget.trim.application;

import com.example.agent.budget.trim.model.CompressionSummaryApplyResult;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.memory.model.MemoryRecord;

/**
 * 压缩摘要回填器接口，负责将压缩结果映射回上下文快照。
 */
public interface CompressionSummaryApplier {

    /**
     * 将压缩摘要回填到上下文快照。
     *
     * @param snapshot 上下文快照
     * @param compressed 压缩结果
     * @return 回填结果
     */
    CompressionSummaryApplyResult apply(ContextSnapshot snapshot, MemoryRecord compressed);
}
