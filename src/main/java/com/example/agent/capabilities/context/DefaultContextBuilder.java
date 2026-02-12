package com.example.agent.capabilities.context;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.budget.token.application.ContextBudgetAllocator;
import com.example.agent.budget.core.ContextBudgetPolicy;
import com.example.agent.budget.config.ContextBudgetProperties;
import com.example.agent.budget.token.application.ContextBudgetRequest;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import com.example.agent.budget.trim.model.ContextPruneRequest;
import com.example.agent.budget.trim.model.ContextPruneResult;
import com.example.agent.budget.trim.application.ContextPruner;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.budget.trim.model.ContextTrimRequest;
import com.example.agent.budget.trim.model.ContextTrimResult;
import com.example.agent.budget.trim.application.ContextTrimmer;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.recall.MemoryRecallResult;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.context.builder.ContextSnapshotFactory;
import com.example.agent.capabilities.context.builder.budget.ContextBudgetRequestFactory;
import com.example.agent.capabilities.context.builder.exception.ContextBuildException;
import com.example.agent.capabilities.context.builder.policy.ContextPolicyResolver;
import com.example.agent.capabilities.context.compression.ContextCompressionFacade;
import com.example.agent.capabilities.context.model.BudgetState;
import com.example.agent.capabilities.context.model.BuildMetrics;
import com.example.agent.capabilities.context.model.ContextPolicy;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.RuntimeMeta;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.example.agent.capabilities.tools.ToolCatalogService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认上下文构建器，实现上下文快照生成与裁剪。
 */
@Service
public class DefaultContextBuilder implements ContextBuilder {

    /**
     * 构建过程日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultContextBuilder.class);

    /**
     * 工具目录，用于构建可用工具列表。
     */
    private final ToolCatalogService toolCatalog;
    /**
     * 预算分配器，用于生成分段预算。
     */
    private final ContextBudgetAllocator budgetAllocator;
    /**
     * 剪枝器，用于在预算前置裁剪上下文。
     */
    private final ContextPruner contextPruner;
    /**
     * 裁剪器，用于按预算缩减上下文。
     */
    private final ContextTrimmer contextTrimmer;
    /**
     * 压缩控制器，用于触发摘要压缩。
     */
    private final ContextCompressionFacade compressionFacade;
    /**
     * 预算配置属性。
     */
    private final ContextBudgetProperties budgetProperties;
    /**
     * 上下文事件发布器。
     */
    private final ContextEventPublisher contextEventPublisher;
    /**
     * 指标发布器，用于记录策略应用情况。
     */
    private final MetricsPublisher metricsPublisher;
    /**
     * 策略解析器，用于解析上下文策略。
     */
    private final ContextPolicyResolver contextPolicyResolver;
    /**
     * 预算请求工厂，用于构造预算请求。
     */
    private final ContextBudgetRequestFactory contextBudgetRequestFactory;

    /**
     * 默认上下文预算，作为兜底值。
     */
    @Value("${agent.context.default-token-budget:4096}")
    private int defaultTokenBudget;

    /**
     * 工作记忆摘要最大字符数。
     */
    @Value("${agent.context.max-working-summary-chars:512}")
    private int maxWorkingSummaryChars;

    /**
     * 构造上下文构建器。
     *
     * @param toolCatalog 工具目录
     * @param budgetAllocator 预算分配器
     * @param contextPruner 剪枝器
     * @param contextTrimmer 裁剪器
     * @param compressionFacade 压缩门面
     * @param budgetProperties 预算配置
     * @param contextEventPublisher 事件发布器
     * @param metricsPublisher 指标发布器
     */
    @Autowired
    public DefaultContextBuilder(ToolCatalogService toolCatalog,
                                 ContextBudgetAllocator budgetAllocator,
                                 ContextPruner contextPruner,
                                 ContextTrimmer contextTrimmer,
                                 ContextCompressionFacade compressionFacade,
                                 ContextBudgetProperties budgetProperties,
                                 ContextEventPublisher contextEventPublisher,
                                 MetricsPublisher metricsPublisher,
                                 ContextPolicyResolver contextPolicyResolver,
                                 ContextBudgetRequestFactory contextBudgetRequestFactory) {
        this.toolCatalog = toolCatalog;
        this.budgetAllocator = budgetAllocator;
        this.contextPruner = contextPruner;
        this.contextTrimmer = contextTrimmer;
        this.compressionFacade = compressionFacade;
        this.budgetProperties = budgetProperties;
        this.contextEventPublisher = contextEventPublisher;
        this.metricsPublisher = metricsPublisher;
        this.contextPolicyResolver = contextPolicyResolver;
        this.contextBudgetRequestFactory = contextBudgetRequestFactory;
    }

    /**
     * 兼容测试场景的构造函数。
     */
    public DefaultContextBuilder(ToolCatalogService toolCatalog,
                                 ContextBudgetAllocator budgetAllocator,
                                 ContextPruner contextPruner,
                                 ContextTrimmer contextTrimmer,
                                 ContextCompressionFacade compressionFacade,
                                 ContextBudgetProperties budgetProperties,
                                 ContextEventPublisher contextEventPublisher,
                                 MetricsPublisher metricsPublisher) {
        this(toolCatalog,
                budgetAllocator,
                contextPruner,
                contextTrimmer,
                compressionFacade,
                budgetProperties,
                contextEventPublisher,
                metricsPublisher,
                new ContextPolicyResolver(),
                new ContextBudgetRequestFactory());
    }

    /**
     * 构建上下文快照并返回预算、裁剪、指标等结果。
     *
     * @param request 构建请求
     * @return 构建结果
     */
    @Override
    public ContextBuildResult build(ContextBuildRequest request) {
        long startNs = System.nanoTime();
        if (request == null) {
            throw new ContextBuildException(
                    "context build request must not be null",
                    "INVALID_REQUEST",
                    "REQUEST_VALIDATE",
                    null,
                    null,
                    null);
        }
        TenantContext tenantContext = request.getTenantContext();
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        String workflowId = request.getWorkflowId();
        String taskId = request.getTaskId();
        TaskRequest taskRequest = request.getTaskRequest();
        Map<String, Object> runtimeContext = request.getRuntimeContext();
        log.debug("上下文构建开始, tenantId={}, workflowId={}",
                tenantId,
                workflowId);
        ContextPolicy policy = contextPolicyResolver.resolve(request, runtimeContext);
        recordPolicyApplied(tenantContext, workflowId, policy);
        try {
            ContextSnapshotFactory snapshotFactory = new ContextSnapshotFactory(
                    toolCatalog,
                    log,
                    metricsPublisher,
                    maxWorkingSummaryChars);
            ContextSnapshot snapshot = snapshotFactory.createSnapshot();
            RuntimeMeta runtimeMeta = snapshotFactory.buildRuntimeMeta(
                    taskRequest,
                    tenantContext,
                    workflowId,
                    runtimeContext,
                    taskId);
            snapshot.setRoleBoundary(snapshotFactory.buildRoleBoundary(runtimeContext, tenantContext, workflowId, taskId));
            snapshot.setTaskIntent(snapshotFactory.buildTaskIntent(
                    taskRequest,
                    runtimeContext,
                    taskId,
                    tenantContext,
                    workflowId));
            snapshot.setWorkingMemory(snapshotFactory.buildWorkingMemory(
                    request.getRecallResult(),
                    request.getObservations(),
                    runtimeContext,
                    tenantContext,
                    workflowId,
                    taskId));
            snapshot.setDomainKnowledge(snapshotFactory.buildDomainKnowledge(runtimeContext, tenantContext, workflowId, taskId));
            snapshot.setLongTermMemory(snapshotFactory.buildLongTermMemory(
                    request.getRecallResult(),
                    runtimeContext,
                    tenantContext,
                    workflowId,
                    taskId));
            snapshot.setToolState(snapshotFactory.buildToolState(
                    request.getToolQuery(),
                    runtimeContext,
                    tenantContext,
                    workflowId,
                    taskId));
            snapshot.setAuditMetadata(snapshotFactory.buildAuditMetadata());

            ContextBudgetRequest budgetRequest = contextBudgetRequestFactory.create(
                    request,
                    runtimeContext,
                    policy,
                    budgetProperties,
                    defaultTokenBudget);
            if (runtimeMeta != null && budgetRequest != null && budgetRequest.isEnabled()) {
                runtimeMeta.setTokenBudget(budgetRequest.getTotalTokens());
            }
            snapshot.setRuntimeMeta(runtimeMeta);

            ContextBudgetAllocation allocation = budgetAllocator != null && budgetRequest != null
                    ? budgetAllocator.allocate(budgetRequest)
                    : ContextBudgetAllocation.disabled(ContextBudgetAllocationState.DISABLED_BY_DEPENDENCY,
                    "allocator_unavailable");
            snapshot.setBudgetState(buildBudgetState(allocation));
            log.info("上下文预算状态, tenantId={}, workflowId={}, taskId={}, allocationState={}, allocationReason={}, totalTokens={}",
                    tenantId,
                    workflowId,
                    taskId,
                    allocation.getAllocationState(),
                    allocation.getAllocationReason(),
                    allocation.getTotalTokens());

            ContextPruneResult pruneResult = null;
            if (contextPruner != null && allocation.isAllocationEnabled()) {
                pruneResult = contextPruner.prune(new ContextPruneRequest(snapshot, allocation, policy));
                if (pruneResult != null && pruneResult.getPrunedSnapshot() != null) {
                    snapshot = pruneResult.getPrunedSnapshot();
                }
            }

            ContextTrimResult trimResult = null;
            if (contextTrimmer != null && allocation.isAllocationEnabled()) {
                ContextBudgetPolicy budgetPolicy = budgetRequest != null ? budgetRequest.getBudgetPolicy() : null;
                trimResult = contextTrimmer.trim(new ContextTrimRequest(snapshot, allocation, budgetPolicy));
                if (trimResult != null && trimResult.getTrimmedSnapshot() != null) {
                    snapshot = trimResult.getTrimmedSnapshot();
                }
            }
            publishTrimStage(tenantContext, workflowId, snapshot, allocation,
                    trimResult != null ? trimResult.getReport() : null);

            ContextCompressionResult compressionResult = null;
            if (compressionFacade != null && allocation.isAllocationEnabled()) {
                String sessionId = taskRequest != null ? taskRequest.getSessionId()
                        : runtimeMeta != null ? runtimeMeta.getSessionId() : null;
                compressionResult = compressionFacade.compressIfNeeded(new ContextCompressionRequest(
                        snapshot,
                        allocation,
                        trimResult != null ? trimResult.getReport() : null,
                        tenantContext,
                        workflowId,
                        sessionId));
                if (compressionResult != null && compressionResult.getSnapshot() != null) {
                    snapshot = compressionResult.getSnapshot();
                }
            }
            publishCompressionStage(tenantContext, workflowId, snapshot, allocation, compressionResult);

            BuildMetrics metrics = snapshotFactory.buildMetrics(
                    snapshot,
                    request.getRecallResult(),
                    System.nanoTime() - startNs);
            ContextBuildResult result = new ContextBuildResult(
                    snapshot,
                    allocation,
                    pruneResult,
                    trimResult != null ? trimResult.getReport() : null,
                    metrics);

            boolean trimmed = trimResult != null
                    && trimResult.getReport() != null
                    && trimResult.getReport().getTotalBeforeTokens() != null
                    && trimResult.getReport().getTotalAfterTokens() != null
                    && trimResult.getReport().getTotalAfterTokens() < trimResult.getReport().getTotalBeforeTokens();
            boolean compressed = compressionResult != null && compressionResult.isTriggered();
            if (metricsPublisher != null) {
                metricsPublisher.incrementWithTags(
                        "context_build_success_total",
                        "hasPolicy", String.valueOf(policy != null),
                        "trimmed", String.valueOf(trimmed),
                        "compressed", String.valueOf(compressed));
            }

            log.info("上下文构建完成, tenantId={}, workflowId={}, taskId={}, snapshotId={}, buildMillis={}, hasPolicy={}, trimmed={}, compressed={}",
                    tenantId,
                    workflowId,
                    taskId,
                    snapshot.getSnapshotId(),
                    metrics.getBuildMillis(),
                    policy != null,
                    trimmed,
                    compressed);
            return result;
        } catch (ContextBuildException ex) {
            if (metricsPublisher != null) {
                metricsPublisher.incrementWithTags(
                        "context_build_failed_total",
                        "stage", sanitizeTag(ex.getStage()),
                        "errorCode", sanitizeTag(ex.getErrorCode()));
            }
            log.error("上下文构建失败, tenantId={}, workflowId={}, taskId={}, stage={}, errorCode={}",
                    tenantId,
                    workflowId,
                    taskId,
                    ex.getStage(),
                    ex.getErrorCode(),
                    ex);
            throw ex;
        } catch (Exception ex) {
            ContextBuildException wrapped = new ContextBuildException(
                    "context build failed",
                    "BUILD_EXECUTION_FAILED",
                    "BUILD_PIPELINE",
                    tenantId,
                    workflowId,
                    ex);
            if (metricsPublisher != null) {
                metricsPublisher.incrementWithTags(
                        "context_build_failed_total",
                        "stage", sanitizeTag(wrapped.getStage()),
                        "errorCode", sanitizeTag(wrapped.getErrorCode()));
            }
            log.error("上下文构建失败, tenantId={}, workflowId={}, taskId={}, stage={}, errorCode={}",
                    tenantId,
                    workflowId,
                    taskId,
                    wrapped.getStage(),
                    wrapped.getErrorCode(),
                    wrapped);
            throw wrapped;
        }
    }

    /**
     * 发布裁剪阶段事件，便于审计与回放。
     */
    private void publishTrimStage(TenantContext tenantContext,
                                  String workflowId,
                                  ContextSnapshot snapshot,
                                  ContextBudgetAllocation allocation,
                                  ContextTrimReport trimReport) {
        if (contextEventPublisher == null || tenantContext == null || workflowId == null || trimReport == null) {
            return;
        }
        contextEventPublisher.publishSnapshotStage(
                tenantContext,
                workflowId,
                null,
                snapshot,
                null,
                allocation,
                trimReport,
                null,
                null,
                ContextSnapshotStage.CONTEXT_TRIMMED,
                trimReport.getTotalBeforeTokens(),
                trimReport.getTotalAfterTokens());
    }

    /**
     * 发布压缩阶段事件，便于审计与回放。
     */
    private void publishCompressionStage(TenantContext tenantContext,
                                         String workflowId,
                                         ContextSnapshot snapshot,
                                         ContextBudgetAllocation allocation,
                                         ContextCompressionResult compressionResult) {
        if (contextEventPublisher == null || tenantContext == null || workflowId == null
                || compressionResult == null || !compressionResult.isTriggered()) {
            return;
        }
        Integer beforeTokens = compressionResult.getAfterTrimTokens() != null
                ? compressionResult.getAfterTrimTokens()
                : compressionResult.getBeforeTokens();
        contextEventPublisher.publishSnapshotStage(
                tenantContext,
                workflowId,
                null,
                snapshot,
                null,
                allocation,
                null,
                compressionResult,
                null,
                ContextSnapshotStage.CONTEXT_COMPRESSED,
                beforeTokens,
                compressionResult.getAfterCompressTokens());
    }

    /**
     * 构建预算状态，记录分配与使用信息。
     *
     * @param allocation 预算分配
     * @return 预算状态
     */
    private BudgetState buildBudgetState(ContextBudgetAllocation allocation) {
        BudgetState state = new BudgetState();
        state.setAllocatedTokens(allocation.getTotalTokens());
        state.setRemainingTokens(allocation.getTotalTokens() != null ? allocation.getTotalTokens() : null);
        state.setUsedTokens(0);
        state.setAllocationState(allocation.getAllocationState());
        state.setAllocationReason(allocation.getAllocationReason());
        return state;
    }

    /**
     * 记录策略应用的日志与指标，便于审计与观测。
     */
    private void recordPolicyApplied(TenantContext tenantContext, String workflowId, ContextPolicy policy) {
        boolean hasPolicy = policy != null;
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags("context_policy_applied_total",
                    "hasPolicy", String.valueOf(hasPolicy));
        }
        log.info("上下文策略应用, tenantId={}, workflowId={}, hasPolicy={}, retrievalPriority={}, pruneOrder={}, enableSensitiveMask={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                hasPolicy,
                formatList(policy != null ? policy.getRetrievalPriority() : null),
                formatList(policy != null ? policy.getPruneOrder() : null),
                policy != null ? policy.getEnableSensitiveMask() : null);
    }

    /**
     * 将列表格式化为逗号分隔字符串，自动清理空值。
     *
     * @param values 字符串列表
     * @return 格式化字符串
     */
    private String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                cleaned.add(value.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    /**
     * 清洗标签值，避免空值标签导致指标聚合异常。
     *
     * @param value 原始标签值
     * @return 清洗后的标签值
     */
    private String sanitizeTag(String value) {
        return StringUtils.hasText(value) ? value : "unknown";
    }

}





