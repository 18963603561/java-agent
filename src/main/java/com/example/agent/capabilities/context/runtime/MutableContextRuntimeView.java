package com.example.agent.capabilities.context.runtime;

import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.Map;
import org.slf4j.Logger;

/**
 * 上下文运行时可写视图。
 *
 * <p>用途：在类型化读取基础上提供受控写入能力。
 */
public class MutableContextRuntimeView extends ContextRuntimeView {

    /**
     * 构造可写视图。
     *
     * @param values 运行时上下文
     * @param log 日志记录器
     * @param metricsPublisher 指标发布器
     */
    public MutableContextRuntimeView(Map<String, Object> values,
                                     Logger log,
                                     MetricsPublisher metricsPublisher) {
        this(values, log, metricsPublisher, null);
    }

    /**
     * 构造可写视图（带读取诊断上下文）。
     *
     * @param values 运行时上下文
     * @param log 日志记录器
     * @param metricsPublisher 指标发布器
     * @param readContext 读取诊断上下文
     */
    public MutableContextRuntimeView(Map<String, Object> values,
                                     Logger log,
                                     MetricsPublisher metricsPublisher,
                                     ContextRuntimeReadContext readContext) {
        super(values, log, metricsPublisher, readContext);
    }

    /**
     * 写入工作流标识。
     */
    public void putWorkflowIdIfAbsent(String workflowId) {
        if (values == null || workflowId == null || workflowId.isBlank()) {
            return;
        }
        values.putIfAbsent(ContextRuntimeKeys.WORKFLOW_ID, workflowId);
    }

    /**
     * 写入工具选择。
     */
    public void putToolChoice(Object toolChoice) {
        if (values == null || toolChoice == null) {
            return;
        }
        values.put(ContextRuntimeKeys.TOOL_CHOICE, toolChoice);
    }

    /**
     * 写入记忆上下文。
     */
    public void putMemory(Map<String, Object> memoryContext) {
        if (values == null || memoryContext == null || memoryContext.isEmpty()) {
            return;
        }
        values.put(ContextRuntimeKeys.MEMORY, memoryContext);
    }

    /**
     * 写入证据包。
     */
    public void putEvidencePack(EvidencePack evidencePack) {
        if (values == null || evidencePack == null) {
            return;
        }
        values.put(ContextRuntimeKeys.EVIDENCE_PACK, evidencePack);
    }

    /**
     * 写入快照结果。
     */
    public void putContextSnapshot(ContextSnapshot snapshot) {
        if (values == null || snapshot == null) {
            return;
        }
        values.put(ContextRuntimeKeys.CONTEXT_SNAPSHOT, snapshot);
    }

    /**
     * 写入快照标识。
     */
    public void putSnapshotIdIfAbsent(String snapshotId) {
        if (values == null || snapshotId == null || snapshotId.isBlank()) {
            return;
        }
        values.putIfAbsent(ContextRuntimeKeys.SNAPSHOT_ID, snapshotId);
    }

    /**
     * 写入预算结果。
     */
    public void putContextBudget(ContextBudgetAllocation budgetAllocation) {
        if (values == null || budgetAllocation == null) {
            return;
        }
        values.put(ContextRuntimeKeys.CONTEXT_BUDGET, budgetAllocation);
    }

    /**
     * 写入剪枝结果。
     */
    public void putContextPrune(ContextPruneResult pruneResult) {
        if (values == null || pruneResult == null) {
            return;
        }
        values.put(ContextRuntimeKeys.CONTEXT_PRUNE, pruneResult);
    }

    /**
     * 写入提示词装配输入。
     */
    public void putPromptAssemblyInput(Object promptAssemblyInput) {
        if (values == null || promptAssemblyInput == null) {
            return;
        }
        values.put(ContextRuntimeKeys.PROMPT_ASSEMBLY_INPUT, promptAssemblyInput);
    }
}
