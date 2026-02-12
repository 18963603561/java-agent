package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextSection;
import java.util.Map;

/**
 * 压缩触发策略接口，负责预算超限判定与触发原因编码。
 */
public interface CompressionTriggerPolicy {

    /**
     * 解析压缩触发原因。
     *
     * @param allocation 预算分配
     * @param sectionTokens 分段令牌统计
     * @param totalTokens 总令牌统计
     * @return 触发原因，未触发返回 null
     */
    String resolveTriggerReason(ContextBudgetAllocation allocation,
                                Map<ContextSection, Integer> sectionTokens,
                                Integer totalTokens);

    /**
     * 判断是否仍超预算。
     *
     * @param allocation 预算分配
     * @param sectionTokens 分段令牌统计
     * @param totalTokens 总令牌统计
     * @return 是否超预算
     */
    boolean isOverBudget(ContextBudgetAllocation allocation,
                         Map<ContextSection, Integer> sectionTokens,
                         Integer totalTokens);
}

