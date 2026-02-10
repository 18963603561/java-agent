package com.example.agent.planning.telemetry;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.context.assembly.ContextAssembler;
import com.example.agent.capabilities.context.assembly.ContextAssemblyCommand;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.assembly.PromptAssemblyInput;
import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.trim.model.ContextPruneResult;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.contract.LlmTaskContextMapper;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.example.agent.capabilities.context.runtime.ContextRuntimeViews;
import com.example.agent.capabilities.context.runtime.MutableContextRuntimeView;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规划遥测组件。
 *
 * <p>用途：统一封装规划阶段提示词追踪与上下文事件发布，避免遥测逻辑散落。
 */
@Component
public class PlanTelemetry {

    private static final Logger log = LoggerFactory.getLogger(PlanTelemetry.class);

    private final PromptAssembler promptAssembler;
    private final ContextAssembler contextAssembler;
    private final ContextEventPublisher contextEventPublisher;
    private final PlanningPromptTraceService planningPromptTraceService;

    /**
     * 构造规划遥测组件。
     *
     * @param promptAssembler 提示词组装器
     * @param contextAssembler 上下文组装器
     * @param contextEventPublisher 上下文事件发布器
     * @param planningPromptTraceService 提示词追踪服务
     */
    public PlanTelemetry(PromptAssembler promptAssembler,
                         ContextAssembler contextAssembler,
                         ContextEventPublisher contextEventPublisher,
                         PlanningPromptTraceService planningPromptTraceService) {
        this.promptAssembler = promptAssembler;
        this.contextAssembler = contextAssembler;
        this.contextEventPublisher = contextEventPublisher;
        this.planningPromptTraceService = planningPromptTraceService;
    }

    /**
     * 应用提示词组装并发布上下文阶段事件。
     *
     * @param modelRequest 模型请求
     * @param prompt 提示词文本
     * @param contextSnapshot 上下文快照
     * @param contextBudget 上下文预算
     * @param contextPrune 上下文裁剪
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param tenantContext 租户上下文
     * @param seqCounter 序列号
     */
    public void applyPromptBundle(ModelRequest modelRequest,
                                  String prompt,
                                  TaskRequest request,
                                  Map<String, Object> context,
                                  ContextSnapshot contextSnapshot,
                                  ContextBudgetAllocation contextBudget,
                                  ContextPruneResult contextPrune,
                                  String tenantId,
                                  String workflowId,
                                  TenantContext tenantContext,
                                  AtomicLong seqCounter) {
        if (promptAssembler == null || modelRequest == null || prompt == null) {
            return;
        }
        Map<String, Object> assemblyContext = context != null ? new HashMap<>(context) : new HashMap<>();
        PromptAssemblyInput input = null;
        if (contextAssembler != null) {
            ContextAssemblyCommand command = ContextAssemblyCommand.builder()
                    .snapshot(contextSnapshot)
                    .allocation(contextBudget)
                    .pruneResult(contextPrune)
                    .tenantId(tenantId)
                    .workflowId(workflowId)
                    .userText(prompt)
                    .build();
            input = contextAssembler.assemble(command);
        }
        if (input != null) {
            MutableContextRuntimeView runtimeView = ContextRuntimeViews.mutable(assemblyContext, log, null);
            runtimeView.putPromptAssemblyInput(input);
        }
        PromptBundle bundle = promptAssembler.build(
                prompt,
                LlmTaskContextMapper.fromTaskRequest(request),
                assemblyContext);
        if (bundle != null) {
            modelRequest.setMessages(bundle.getMessages());
            publishPlanStage(tenantContext,
                    workflowId,
                    seqCounter,
                    contextSnapshot,
                    contextBudget,
                    input,
                    bundle,
                    resolveTokenTotal(input != null ? input.getBudgetUsedTokens() : null));
        }
    }

    /**
     * 记录提示词追踪。
     *
     * @param metadata 元数据
     * @param promptText 提示词文本
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号
     * @param modelId 模型标识
     * @param parseSuccess 是否解析成功
     * @param parseErrorType 解析错误类型
     * @param repairAttempted 是否尝试修复
     * @param repairSuccess 是否修复成功
     */
    public void recordPromptTrace(Map<String, Object> metadata,
                                  String promptText,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  String modelId,
                                  boolean parseSuccess,
                                  String parseErrorType,
                                  boolean repairAttempted,
                                  boolean repairSuccess) {
        if (planningPromptTraceService == null) {
            return;
        }
        planningPromptTraceService.recordPromptTrace(metadata,
                promptText,
                tenantContext,
                workflowId,
                seqCounter,
                modelId,
                parseSuccess,
                parseErrorType,
                repairAttempted,
                repairSuccess);
    }

    /**
     * 记录模型调用开始日志。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param planId 规划标识
     */
    public void logModelInvokeStart(String tenantId, String workflowId, String planId) {
        log.info("开始调用规划模型, tenantId={}, workflowId={}, planId={}", tenantId, workflowId, planId);
    }

    /**
     * 记录模型调用结束日志。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param planId 规划标识
     * @param costMs 耗时
     * @param hasContent 是否有内容
     */
    public void logModelInvokeEnd(String tenantId,
                                  String workflowId,
                                  String planId,
                                  long costMs,
                                  boolean hasContent) {
        log.info("规划模型调用结束, tenantId={}, workflowId={}, planId={}, costMs={}, hasContent={}",
                tenantId,
                workflowId,
                planId,
                costMs,
                hasContent);
    }

    /**
     * 记录修复调用结束日志。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param planId 规划标识
     * @param costMs 耗时
     * @param success 是否成功
     */
    public void logRepairEnd(String tenantId,
                             String workflowId,
                             String planId,
                             long costMs,
                             boolean success) {
        log.info("规划修复调用结束, tenantId={}, workflowId={}, planId={}, costMs={}, success={}",
                tenantId,
                workflowId,
                planId,
                costMs,
                success);
    }

    private void publishPlanStage(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  ContextSnapshot snapshot,
                                  ContextBudgetAllocation allocation,
                                  PromptAssemblyInput assemblyInput,
                                  PromptBundle bundle,
                                  Integer beforeTokens) {
        if (contextEventPublisher == null || tenantContext == null || workflowId == null || bundle == null) {
            return;
        }
        Integer afterTokens = resolveTokenTotal(assemblyInput != null ? assemblyInput.getBudgetUsedTokens() : null);
        if (afterTokens == null) {
            afterTokens = bundle.getEstimatedTokens();
        }
        List<String> truncatedSections = bundle.getTruncatedSections() != null
                ? bundle.getTruncatedSections()
                : List.of();
        contextEventPublisher.publishSnapshotStage(
                tenantContext,
                workflowId,
                seqCounter,
                snapshot,
                null,
                allocation,
                null,
                null,
                truncatedSections,
                ContextSnapshotStage.PLAN_ASSEMBLED,
                beforeTokens,
                afterTokens);
    }

    private Integer resolveTokenTotal(Map<String, Integer> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        Integer total = tokens.get(PlanningFieldKeys.TOTAL);
        if (total != null) {
            return total;
        }
        int sum = 0;
        for (Integer value : tokens.values()) {
            sum += value == null ? 0 : value;
        }
        return sum;
    }
}

