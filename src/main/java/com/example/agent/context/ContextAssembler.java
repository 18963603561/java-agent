package com.example.agent.context;

import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextCompressionResult;
import com.example.agent.budget.ContextPruneResult;
import com.example.agent.budget.ContextTrimReport;

/**
 * 上下文装配器，用于生成提示词装配输入。
 */
public interface ContextAssembler {

    /**
     * 生成提示词装配输入。
     *
     * @param snapshot 上下文快照
     * @param allocation 预算分配结果
     * @param trimReport 裁剪报告，可为空
     * @param pruneResult 裁剪结果，可为空
     * @param compressionResult 压缩结果，可为空
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param userText 用户提示词内容，可为空
     * @return 装配输入
     */
    PromptAssemblyInput assemble(ContextSnapshot snapshot,
                                 ContextBudgetAllocation allocation,
                                 ContextTrimReport trimReport,
                                 ContextPruneResult pruneResult,
                                 ContextCompressionResult compressionResult,
                                 String tenantId,
                                 String workflowId,
                                 String userText);
}
