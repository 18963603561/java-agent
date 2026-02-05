package com.example.agent.capabilities.context;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.token.ContextBudgetAllocator;
import com.example.agent.budget.token.ContextBudgetPolicy;
import com.example.agent.budget.token.ContextBudgetProperties;
import com.example.agent.budget.token.ContextBudgetRequest;
import com.example.agent.budget.trim.ContextCompressionController;
import com.example.agent.budget.trim.ContextCompressionRequest;
import com.example.agent.budget.trim.ContextCompressionResult;
import com.example.agent.budget.trim.ContextPruneRequest;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.budget.trim.ContextPruner;
import com.example.agent.budget.trim.ContextTrimRequest;
import com.example.agent.budget.trim.ContextTrimResult;
import com.example.agent.budget.trim.ContextTrimmer;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.ConversationSummary;
import com.example.agent.capabilities.memory.MemoryRecallResult;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.WorkingMemorySummary;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.context.research.ResearchCitation;
import com.example.agent.runtime.engine.ReactObservation;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.example.agent.capabilities.tools.ToolCatalog;
import com.example.agent.capabilities.tools.ToolQuery;
import com.example.agent.capabilities.tools.ToolSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ToolCatalog toolCatalog;
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
    private final ContextCompressionController compressionController;
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
     * @param compressionController 压缩控制器
     * @param budgetProperties 预算配置
     * @param contextEventPublisher 事件发布器
     * @param metricsPublisher 指标发布器
     */
    public DefaultContextBuilder(ToolCatalog toolCatalog,
                                 ContextBudgetAllocator budgetAllocator,
                                 ContextPruner contextPruner,
                                 ContextTrimmer contextTrimmer,
                                 ContextCompressionController compressionController,
                                 ContextBudgetProperties budgetProperties,
                                 ContextEventPublisher contextEventPublisher,
                                 MetricsPublisher metricsPublisher) {
        this.toolCatalog = toolCatalog;
        this.budgetAllocator = budgetAllocator;
        this.contextPruner = contextPruner;
        this.contextTrimmer = contextTrimmer;
        this.compressionController = compressionController;
        this.budgetProperties = budgetProperties;
        this.contextEventPublisher = contextEventPublisher;
        this.metricsPublisher = metricsPublisher;
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
        ContextBuildResult result = new ContextBuildResult();
        if (request == null) {
            // 空请求直接返回空结果
            log.warn("上下文构建请求为空");
            return result;
        }
        TenantContext tenantContext = request.getTenantContext();
        TaskRequest taskRequest = request.getTaskRequest();
        Map<String, Object> runtimeContext = request.getRuntimeContext();
        // 记录构建开始日志，便于追踪
        log.debug("上下文构建开始, tenantId={}, workflowId={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                request.getWorkflowId());
        // 构建流程分为：基础快照、预算分配、剪枝、裁剪、压缩与指标
        // 各阶段可能替换快照对象，需按顺序执行
        // 事件发布用于审计回放与可观测追踪
        // 解析并记录上下文策略
        ContextPolicy policy = resolvePolicy(request, runtimeContext);
        recordPolicyApplied(tenantContext, request.getWorkflowId(), policy);
        try {
            // 初始化快照并填充基础结构
            ContextSnapshot snapshot = new ContextSnapshot();
            snapshot.setSnapshotId(UUID.randomUUID().toString());
            RuntimeMeta runtimeMeta = buildRuntimeMeta(taskRequest, tenantContext, request.getWorkflowId(), runtimeContext);
            snapshot.setRoleBoundary(buildRoleBoundary(runtimeContext));
            snapshot.setTaskIntent(buildTaskIntent(taskRequest, runtimeContext, request.getTaskId()));
            snapshot.setWorkingMemory(buildWorkingMemory(request.getRecallResult(), request.getObservations(), runtimeContext));
            snapshot.setDomainKnowledge(buildDomainKnowledge(runtimeContext));
            snapshot.setLongTermMemory(buildLongTermMemory(request.getRecallResult(), runtimeContext));
            snapshot.setToolState(buildToolState(request.getToolQuery(), runtimeContext));
            snapshot.setAuditMetadata(buildAuditMetadata());

            // 预算请求用于填充运行时预算信息
            ContextBudgetRequest budgetRequest = resolveBudgetRequest(request, runtimeContext);
            if (runtimeMeta != null && budgetRequest != null && budgetRequest.isEnabled()) {
                runtimeMeta.setTokenBudget(budgetRequest.getTotalTokens());
            }
            snapshot.setRuntimeMeta(runtimeMeta);
            // 分配预算并写入快照
            ContextBudgetAllocation allocation = budgetAllocator != null && budgetRequest != null
                    ? budgetAllocator.allocate(budgetRequest)
                    : null;
            snapshot.setBudgetState(buildBudgetState(allocation));

            // 剪枝阶段：优先移除低优先级内容
            ContextPruneResult pruneResult = null;
            if (contextPruner != null && allocation != null) {
                pruneResult = contextPruner.prune(new ContextPruneRequest(snapshot, allocation, policy));
                if (pruneResult != null && pruneResult.getPrunedSnapshot() != null) {
                    snapshot = pruneResult.getPrunedSnapshot();
                }
            }

            // 裁剪阶段：按预算压缩上下文
            ContextTrimResult trimResult = null;
            if (contextTrimmer != null && allocation != null) {
                ContextBudgetPolicy budgetPolicy = budgetRequest != null ? budgetRequest.getBudgetPolicy() : null;
                trimResult = contextTrimmer.trim(new ContextTrimRequest(snapshot, allocation, budgetPolicy));
                if (trimResult != null && trimResult.getTrimmedSnapshot() != null) {
                    snapshot = trimResult.getTrimmedSnapshot();
                }
            }
            // 发布裁剪阶段事件
            publishTrimStage(tenantContext, request.getWorkflowId(), snapshot, allocation,
                    trimResult != null ? trimResult.getReport() : null);

            // 压缩阶段：触发摘要压缩
            ContextCompressionResult compressionResult = null;
            if (compressionController != null && allocation != null) {
                String sessionId = taskRequest != null ? taskRequest.getSessionId()
                        : runtimeMeta != null ? runtimeMeta.getSessionId() : null;
                compressionResult = compressionController.compressIfNeeded(new ContextCompressionRequest(
                        snapshot,
                        allocation,
                        trimResult != null ? trimResult.getReport() : null,
                        tenantContext,
                        request.getWorkflowId(),
                        sessionId));
                if (compressionResult != null && compressionResult.getSnapshot() != null) {
                    snapshot = compressionResult.getSnapshot();
                }
            }
            // 发布压缩阶段事件
            publishCompressionStage(tenantContext, request.getWorkflowId(), snapshot, allocation, compressionResult);

            // 计算构建指标
            BuildMetrics metrics = buildMetrics(snapshot, request.getRecallResult(), System.nanoTime() - startNs);
            result.setSnapshot(snapshot);
            result.setBudgetAllocation(allocation);
            result.setPruneResult(pruneResult);
            result.setTrimReport(trimResult != null ? trimResult.getReport() : null);
            result.setMetrics(metrics);

            // 构建成功日志
            log.info("上下文构建完成, tenantId={}, snapshotId={}, buildMillis={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    snapshot.getSnapshotId(),
                    metrics != null ? metrics.getBuildMillis() : null);
            return result;
        } catch (Exception ex) {
            // 异常捕获必须记录上下文与堆栈
            log.error("上下文构建失败, tenantId={}", tenantContext != null ? tenantContext.getTenantId() : null, ex);
            return result;
        }
    }

    /**
     * 发布裁剪阶段事件，便于审计与回放。
     */
    private void publishTrimStage(TenantContext tenantContext,
                                  String workflowId,
                                  ContextSnapshot snapshot,
                                  ContextBudgetAllocation allocation,
                                  com.example.agent.budget.trim.ContextTrimReport trimReport) {
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
     * 构建运行时元信息，包含租户、会话、格式与工具白名单等。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param context 运行时上下文
     * @return 运行时元信息
     */
    private RuntimeMeta buildRuntimeMeta(TaskRequest request,
                                         TenantContext tenantContext,
                                         String workflowId,
                                         Map<String, Object> context) {
        RuntimeMeta meta = new RuntimeMeta();
        // 租户上下文优先提供链路信息
        if (tenantContext != null) {
            meta.setTenantId(tenantContext.getTenantId());
            meta.setUserId(tenantContext.getUserId());
            meta.setTraceId(tenantContext.getTraceId());
            meta.setRequestId(tenantContext.getRequestId());
        }
        // 请求体可能携带会话标识
        if (request != null) {
            meta.setSessionId(request.getSessionId());
        }
        // 工作流标识用于全链路关联
        meta.setWorkflowId(workflowId);
        if (context != null) {
            // 运行时上下文补充格式与工具约束
            meta.setLocale(readString(context, "locale"));
            meta.setOutputFormat(readString(context, "outputFormat"));
            meta.setAllowedTools(readStringList(context.get("allowedTools")));
        }
        // 记录请求时间
        meta.setRequestTime(Instant.now());
        return meta;
    }

    /**
     * 构建角色边界信息，包含策略、风险与审批需求。
     *
     * @param context 运行时上下文
     * @return 角色边界
     */
    private RoleBoundary buildRoleBoundary(Map<String, Object> context) {
        RoleBoundary boundary = new RoleBoundary();
        if (context == null) {
            return boundary;
        }
        // 读取策略标识与约束信息
        boundary.setSystemPolicyId(readString(context, "systemPolicyId"));
        boundary.setDeveloperPolicyId(readString(context, "developerPolicyId"));
        boundary.setForbiddenActions(readStringList(context.get("forbiddenActions")));
        boundary.setDataScopes(readStringList(context.get("dataScopes")));
        // 风险等级用于决定后续策略路径
        // 审批开关用于高风险操作控制
        boundary.setRiskLevel(readString(context, "riskLevel"));
        boundary.setApprovalRequired(readBoolean(context.get("requiresApproval")));
        return boundary;
    }

    /**
     * 构建任务意图，包含输入、成功标准与输出要求。
     *
     * @param request 任务请求
     * @param context 运行时上下文
     * @param taskId 任务标识
     * @return 任务意图
     */
    private TaskIntent buildTaskIntent(TaskRequest request, Map<String, Object> context, String taskId) {
        TaskIntent intent = new TaskIntent();
        // 任务标识可能用于回放与追踪
        intent.setTaskId(taskId);
        // 用户输入优先来自请求体
        if (request != null) {
            // 输入为空时由后续装配逻辑兜底
            intent.setInputText(request.getQuery());
        }
        if (context != null) {
            // 约束用于限定输出与行为边界
            // 约束与标准可来自运行时上下文
            intent.setSuccessCriteria(readString(context, "successCriteria"));
            intent.setFailurePolicy(readString(context, "failurePolicy"));
            intent.setRequiredOutput(readString(context, "requiredOutput"));
            intent.setConstraints(readStringList(context.get("constraints")));
        }
        return intent;
    }

    /**
     * 构建工作记忆，包括摘要、关键事实、计划步骤与证据包。
     *
     * @param recallResult 记忆召回结果
     * @param observations 观察记录
     * @param context 运行时上下文
     * @return 工作记忆
     */
    private WorkingMemory buildWorkingMemory(MemoryRecallResult recallResult,
                                             List<ReactObservation> observations,
                                             Map<String, Object> context) {
        WorkingMemory memory = new WorkingMemory();
        boolean usedStructuredSummary = false;
        // 召回结果优先用于生成结构化摘要
        if (recallResult != null && recallResult.isUsed()) {
            StructuredSummary structuredSummary = resolveStructuredSummary(recallResult.getRecords());
            if (structuredSummary != null && structuredSummary.hasAny()) {
                usedStructuredSummary = true;
                applyStructuredSummary(memory, structuredSummary);
            }
            // 结构化摘要缺失时使用原始摘要
            if (!StringUtils.hasText(memory.getSummary())) {
                memory.setSummary(trimText(recallResult.getSummary(), maxWorkingSummaryChars));
            }
            // 关键事实为空时从召回记录提取
            if (memory.getKeyFacts() == null || memory.getKeyFacts().isEmpty()) {
                memory.setKeyFacts(extractKeyFacts(recallResult.getRecords()));
            }
        }
        // 未命中召回时，尝试从观察结果生成摘要
        if (!StringUtils.hasText(memory.getSummary()) && observations != null && !observations.isEmpty()) {
            memory.setSummary(trimText(buildObservationSummary(observations), maxWorkingSummaryChars));
        }
        if (context != null) {
            // 计划步骤与下一步来自运行时上下文
            memory.setPlanSteps(readStringList(context.get("planSteps")));
            memory.setNextStep(readString(context, "nextStep"));
            // 证据包由上游构建并挂载到上下文中
            // 这里仅做引用挂接，不做内容加工
            Object evidenceObj = context.get(EvidencePackService.CONTEXT_EVIDENCE_PACK);
            if (evidenceObj instanceof EvidencePack evidencePack) {
                memory.setEvidencePack(evidencePack);
            }
        }
        // 记录结构化摘要使用情况
        if (recallResult != null && recallResult.isUsed()) {
            memory.setUsedStructuredSummary(usedStructuredSummary);
        }
        // 记录脱敏次数，便于审计
        if (recallResult != null && recallResult.getRedactionsAppliedCount() > 0) {
            memory.setRedactionsAppliedCount(recallResult.getRedactionsAppliedCount());
        }
        // 补齐摘要字符数
        if (memory.getSummaryChars() == null) {
            memory.setSummaryChars(memory.getSummary() != null ? memory.getSummary().length() : 0);
        }
        // 补齐工作记忆项数量
        if (memory.getWorkingMemoryItems() == null) {
            memory.setWorkingMemoryItems(memory.getKeyFacts() != null ? memory.getKeyFacts().size() : 0);
        }
        // 结构化摘要存在时补齐版本
        if (usedStructuredSummary
                && (memory.getSummaryVersion() == null || memory.getSummaryVersion().isBlank())) {
            memory.setSummaryVersion("v1");
        }
        return memory;
    }

    /**
     * 从召回记录中提取结构化摘要信息。
     *
     * @param records 召回记录
     * @return 结构化摘要容器
     */
    private StructuredSummary resolveStructuredSummary(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        StructuredSummary summary = new StructuredSummary();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            // 优先选取第一条可用的结构化摘要
            if (summary.getConversationSummary() == null && record.getConversationSummary() != null) {
                summary.setConversationSummary(record.getConversationSummary());
            }
            if (summary.getWorkingMemorySummary() == null && record.getWorkingMemorySummary() != null) {
                summary.setWorkingMemorySummary(record.getWorkingMemorySummary());
            }
            // 两类摘要均已获取时提前结束
            if (summary.getConversationSummary() != null && summary.getWorkingMemorySummary() != null) {
                break;
            }
        }
        return summary.hasAny() ? summary : null;
    }

    /**
     * 将结构化摘要注入到工作记忆中。
     *
     * @param memory 工作记忆
     * @param structuredSummary 结构化摘要
     */
    private void applyStructuredSummary(WorkingMemory memory, StructuredSummary structuredSummary) {
        if (memory == null || structuredSummary == null || !structuredSummary.hasAny()) {
            return;
        }
        ConversationSummary conversationSummary = structuredSummary.getConversationSummary();
        if (conversationSummary != null) {
            // 优先使用结构化摘要文本
            String summaryText = firstNonBlank(conversationSummary.getSummary(), conversationSummary.toLegacyText());
            if (StringUtils.hasText(summaryText)) {
                memory.setSummary(trimText(summaryText, maxWorkingSummaryChars));
            }
            // 仅在关键事实为空时注入摘要要点
            if (conversationSummary.getBullets() != null && !conversationSummary.getBullets().isEmpty()
                    && (memory.getKeyFacts() == null || memory.getKeyFacts().isEmpty())) {
                memory.setKeyFacts(new ArrayList<>(conversationSummary.getBullets()));
            }
        }
        WorkingMemorySummary workingSummary = structuredSummary.getWorkingMemorySummary();
        if (workingSummary != null) {
            // 工作记忆条目直接覆盖为最新结构化内容
            if (workingSummary.getItems() != null && !workingSummary.getItems().isEmpty()) {
                memory.setKeyFacts(new ArrayList<>(workingSummary.getItems()));
            }
            // 未设置摘要时填充工作记忆摘要
            if (!StringUtils.hasText(memory.getSummary())) {
                String summaryText = firstNonBlank(workingSummary.getSummary(), workingSummary.toLegacyText());
                if (StringUtils.hasText(summaryText)) {
                    memory.setSummary(trimText(summaryText, maxWorkingSummaryChars));
                }
            }
        }
        // 版本、字符数与条目数量统一由结构化摘要补齐
        String version = firstNonBlank(
                conversationSummary != null ? conversationSummary.getVersion() : null,
                workingSummary != null ? workingSummary.getVersion() : null);
        if (StringUtils.hasText(version)) {
            memory.setSummaryVersion(version);
        }
        Integer summaryChars = firstNonNull(
                conversationSummary != null ? conversationSummary.getSummaryChars() : null,
                workingSummary != null ? workingSummary.getSummaryChars() : null);
        if (summaryChars != null) {
            memory.setSummaryChars(summaryChars);
        }
        Integer items = firstNonNull(
                workingSummary != null ? workingSummary.getItemCount() : null,
                conversationSummary != null ? conversationSummary.getBulletCount() : null);
        if (items != null) {
            memory.setWorkingMemoryItems(items);
        }
    }

    /**
     * 构建领域知识，主要包含引用信息。
     *
     * @param context 运行时上下文
     * @return 领域知识
     */
    private DomainKnowledge buildDomainKnowledge(Map<String, Object> context) {
        DomainKnowledge knowledge = new DomainKnowledge();
        if (context == null) {
            return knowledge;
        }
        // 引用来源可能来自研究或外部注入
        // 统一映射为内部引用结构
        // 支持两种键名的引用输入
        Object citationsObj = context.get("citations");
        if (citationsObj == null) {
            citationsObj = context.get("researchCitations");
        }
        List<Citation> citations = new ArrayList<>();
        if (citationsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Citation citation) {
                    citations.add(citation);
                } else if (item instanceof ResearchCitation research) {
                    // 研究引用映射为统一证据格式
                    Citation citation = new Citation();
                    citation.setSource(research.getSource());
                    citation.setSnippet(research.getSnippet());
                    citation.setFetchedAt(research.getFetchedAt());
                    citations.add(citation);
                } else if (item instanceof Map<?, ?> map) {
                    // 兼容 Map 结构输入
                    Citation citation = new Citation();
                    citation.setSource(readString(map, "source"));
                    citation.setTitle(readString(map, "title"));
                    citation.setUri(readString(map, "uri"));
                    citation.setSnippet(readString(map, "snippet"));
                    citations.add(citation);
                }
            }
        }
        // 空列表转为 null，减少序列化噪声
        knowledge.setCitations(citations.isEmpty() ? null : citations);
        return knowledge;
    }

    /**
     * 构建长期记忆引用列表。
     *
     * @param recallResult 召回结果
     * @param context 运行时上下文
     * @return 长期记忆
     */
    private LongTermMemory buildLongTermMemory(MemoryRecallResult recallResult, Map<String, Object> context) {
        LongTermMemory memory = new LongTermMemory();
        List<MemoryRef> refs = new ArrayList<>();
        // 上游可以直接透传长期记忆引用
        // 此处不做去重，保持上游语义
        // 运行时上下文可能提供外部引用
        if (context != null && context.get("longTermMemoryRefs") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof MemoryRef ref) {
                    refs.add(ref);
                }
            }
        }
        // 召回结果补充引用信息
        if (recallResult != null && recallResult.isUsed()) {
            for (MemoryRecord record : recallResult.getRecords()) {
                if (record == null) {
                    continue;
                }
                MemoryRef ref = new MemoryRef();
                ref.setMemoryId(record.getMemoryId());
                ref.setMemoryType(record.getLayer());
                ref.setSnippet(firstNonBlank(record.getSummary(), record.getContent()));
                ref.setExpiresAt(record.getExpiresAt());
                ref.setSource("memory_recall");
                refs.add(ref);
            }
        }
        // 空列表转为 null，减少序列化噪声
        memory.setMemoryRefs(refs.isEmpty() ? null : refs);
        return memory;
    }

    /**
     * 构建工具状态，包含可用工具与选择结果。
     *
     * @param query 工具查询
     * @param context 运行时上下文
     * @return 工具状态
     */
    private ToolState buildToolState(ToolQuery query, Map<String, Object> context) {
        ToolState state = new ToolState();
        if (toolCatalog != null) {
            // 工具目录可能较大，仅汇总摘要信息
            // 以查询条件控制返回范围
            // 未提供查询时使用默认查询
            List<ToolSummary> summaries = toolCatalog.listSummaries(query != null ? query : new ToolQuery());
            state.setAvailableTools(summaries == null || summaries.isEmpty() ? null : summaries);
        }
        if (context != null) {
            // 读取上游选择与错误信息
            state.setSelectedTools(readStringList(context.get("selectedTools")));
            state.setLastError(readString(context, "lastToolError"));
        }
        return state;
    }

    /**
     * 构建预算状态，记录分配与使用信息。
     *
     * @param allocation 预算分配
     * @return 预算状态
     */
    private BudgetState buildBudgetState(ContextBudgetAllocation allocation) {
        BudgetState state = new BudgetState();
        if (allocation == null) {
            return state;
        }
        // 预算初始化时使用分配值作为剩余值
        // 使用量初始为 0
        // 初始状态下已分配即为剩余额度
        state.setAllocatedTokens(allocation.getTotalTokens());
        state.setRemainingTokens(allocation.getTotalTokens() != null ? allocation.getTotalTokens() : null);
        state.setUsedTokens(0);
        return state;
    }

    /**
     * 构建审计元数据，标注来源与版本。
     *
     * @return 审计元数据
     */
    private AuditMetadata buildAuditMetadata() {
        AuditMetadata metadata = new AuditMetadata();
        metadata.setVersion("v1");
        metadata.setSource("context_builder");
        metadata.setCreatedAt(Instant.now());
        return metadata;
    }

    /**
     * 构建构建指标，统计耗时、召回与工具信息。
     *
     * @param snapshot 上下文快照
     * @param recallResult 召回结果
     * @param elapsedNs 耗时纳秒
     * @return 构建指标
     */
    private BuildMetrics buildMetrics(ContextSnapshot snapshot, MemoryRecallResult recallResult, long elapsedNs) {
        BuildMetrics metrics = new BuildMetrics();
        metrics.setBuildMillis(Math.max(0, elapsedNs / 1_000_000));
        // 检索数量由记忆召回与引用共同组成
        // 该指标用于衡量外部知识使用强度
        // 仅统计数量，不包含正文内容
        int retrievalCount = 0;
        if (recallResult != null && recallResult.isUsed()) {
            // 召回数量计入检索统计
            retrievalCount += recallResult.getCount();
        }
        if (snapshot != null && snapshot.getDomainKnowledge() != null
                && snapshot.getDomainKnowledge().getCitations() != null) {
            // 引用数量计入检索统计
            retrievalCount += snapshot.getDomainKnowledge().getCitations().size();
        }
        metrics.setRetrievalCount(retrievalCount);
        int toolCount = 0;
        if (snapshot != null && snapshot.getToolState() != null
                && snapshot.getToolState().getAvailableTools() != null) {
            // 可用工具数量用于衡量工具面
            toolCount = snapshot.getToolState().getAvailableTools().size();
        }
        metrics.setToolCount(toolCount);
        // 其余指标由上游补充
        return metrics;
    }

    /**
     * 解析并补齐上下文策略，优先使用请求中显式配置。
     */
    private ContextPolicy resolvePolicy(ContextBuildRequest request, Map<String, Object> runtimeContext) {
        if (request == null) {
            return null;
        }
        ContextPolicy policy = request.getPolicy();
        if (policy != null) {
            return policy;
        }
        ContextPolicy resolved = resolvePolicyFromContext(runtimeContext);
        if (resolved == null && request.getTaskRequest() != null) {
            resolved = resolvePolicyFromContext(request.getTaskRequest().getContext());
        }
        if (resolved != null) {
            request.setPolicy(resolved);
        }
        return resolved;
    }

    /**
     * 从上下文中解析策略对象，兼容不同键名与结构。
     *
     * @param context 上下文数据
     * @return 策略对象
     */
    private ContextPolicy resolvePolicyFromContext(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        // 兼容不同字段名
        Object raw = context.get("contextPolicy");
        if (raw == null) {
            raw = context.get("policy");
        }
        if (raw instanceof ContextPolicy policy) {
            return policy;
        }
        // Map 结构需要转为策略对象
        if (raw instanceof Map<?, ?> map) {
            ContextPolicy policy = buildPolicyFromMap(map);
            return hasPolicyContent(policy) ? policy : null;
        }
        return null;
    }

    /**
     * 将 Map 结构解析为策略对象。
     *
     * @param map 策略参数
     * @return 策略对象
     */
    private ContextPolicy buildPolicyFromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        ContextPolicy policy = new ContextPolicy();
        // 按字段映射策略属性
        policy.setPolicyId(readString(map, "policyId"));
        policy.setRetrievalPriority(readStringList(map.get("retrievalPriority")));
        policy.setPruneOrder(readStringList(map.get("pruneOrder")));
        policy.setMaxEvidenceCount(readInteger(map, "maxEvidenceCount"));
        policy.setMaxMemoryCount(readInteger(map, "maxMemoryCount"));
        policy.setEnableSensitiveMask(readBoolean(map.get("enableSensitiveMask")));
        return policy;
    }

    /**
     * 判断策略对象是否包含有效内容。
     *
     * @param policy 策略对象
     * @return 是否有效
     */
    private boolean hasPolicyContent(ContextPolicy policy) {
        if (policy == null) {
            return false;
        }
        return StringUtils.hasText(policy.getPolicyId())
                || (policy.getRetrievalPriority() != null && !policy.getRetrievalPriority().isEmpty())
                || (policy.getPruneOrder() != null && !policy.getPruneOrder().isEmpty())
                || policy.getMaxEvidenceCount() != null
                || policy.getMaxMemoryCount() != null
                || policy.getEnableSensitiveMask() != null;
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
     * 解析预算请求，优先使用请求中的预算配置。
     *
     * @param request 构建请求
     * @param context 运行时上下文
     * @return 预算请求
     */
    private ContextBudgetRequest resolveBudgetRequest(ContextBuildRequest request, Map<String, Object> context) {
        if (budgetProperties != null && !budgetProperties.isEnabled()) {
            return null;
        }
        if (request.getBudgetRequest() != null) {
            ContextBudgetRequest provided = request.getBudgetRequest();
            fillBudgetRequest(provided, request, context);
            return provided;
        }
        ContextBudgetRequest budgetRequest = new ContextBudgetRequest();
        fillBudgetRequest(budgetRequest, request, context);
        return budgetRequest;
    }

    /**
     * 补齐预算请求的必要字段。
     *
     * @param budgetRequest 预算请求
     * @param request 构建请求
     * @param context 运行时上下文
     */
    private void fillBudgetRequest(ContextBudgetRequest budgetRequest,
                                   ContextBuildRequest request,
                                   Map<String, Object> context) {
        if (budgetRequest == null || request == null) {
            return;
        }
        Integer totalTokens = budgetRequest.getTotalTokens();
        if (totalTokens == null || totalTokens <= 0) {
            // 先从上下文读取预算
            totalTokens = readInteger(context, "tokenBudget");
        }
        if (totalTokens == null || totalTokens <= 0) {
            // 其次使用配置中的预算
            totalTokens = budgetProperties != null ? budgetProperties.getTotalBudgetTokens() : null;
        }
        if (totalTokens == null || totalTokens <= 0) {
            // 最后回退到默认预算
            totalTokens = defaultTokenBudget;
        }
        budgetRequest.setTotalTokens(totalTokens);
        if (budgetRequest.getReservedTokens() == null) {
            budgetRequest.setReservedTokens(0);
        }
        if (budgetRequest.getPolicy() == null) {
            budgetRequest.setPolicy(request.getPolicy());
        }
        TenantContext tenantContext = request.getTenantContext();
        // 补齐租户与工作流信息
        if (tenantContext != null && (budgetRequest.getTenantId() == null || budgetRequest.getTenantId().isBlank())) {
            budgetRequest.setTenantId(tenantContext.getTenantId());
        }
        if (budgetRequest.getWorkflowId() == null || budgetRequest.getWorkflowId().isBlank()) {
            budgetRequest.setWorkflowId(request.getWorkflowId());
        }
        if (budgetRequest.getTaskId() == null || budgetRequest.getTaskId().isBlank()) {
            budgetRequest.setTaskId(request.getTaskId());
        }
        // 未指定策略时使用配置默认值
        if (budgetRequest.getBudgetPolicy() == null && budgetProperties != null) {
            ContextBudgetPolicy policy = budgetProperties.toPolicy();
            budgetRequest.setBudgetPolicy(policy);
        }
    }

    /**
     * 从召回记录中提取关键事实。
     *
     * @param records 召回记录
     * @return 关键事实列表
     */
    private List<String> extractKeyFacts(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        List<String> facts = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            // 以摘要优先，缺失时使用原始内容
            String text = firstNonBlank(record.getSummary(), record.getContent());
            if (StringUtils.hasText(text)) {
                facts.add(trimText(text, 200));
            }
        }
        return facts.isEmpty() ? null : facts;
    }

    /**
     * 将观察记录拼接为摘要文本。
     *
     * @param observations 观察列表
     * @return 摘要文本
     */
    private String buildObservationSummary(List<ReactObservation> observations) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (ReactObservation observation : observations) {
            if (observation == null || !StringUtils.hasText(observation.getContent())) {
                continue;
            }
            // 以“序号:内容”形式追加
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(index).append(":").append(trimText(observation.getContent(), 200));
            index++;
            // 超过最大长度时提前结束
            if (builder.length() > maxWorkingSummaryChars) {
                break;
            }
        }
        return builder.toString();
    }

    /**
     * 截断文本到指定长度。
     *
     * @param text 原文本
     * @param maxChars 最大长度
     * @return 截断后的文本
     */
    private String trimText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }

    /**
     * 返回第一个非空字符串。
     *
     * @param values 候选值
     * @return 非空字符串
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 返回第一个非空整数。
     *
     * @param first 第一值
     * @param second 第二值
     * @return 非空整数
     */
    private Integer firstNonNull(Integer first, Integer second) {
        if (first != null) {
            return first;
        }
        return second;
    }

    /**
     * 从 Map 读取字符串。
     *
     * @param map 数据源
     * @param key 键名
     * @return 字符串值
     */
    private String readString(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 将对象解析为字符串列表。
     *
     * @param value 输入值
     * @return 字符串列表
     */
    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    result.add(item.toString());
                }
            }
            return result.isEmpty() ? null : result;
        }
        // 单字符串时包装为列表
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return null;
    }

    /**
     * 将对象解析为布尔值。
     *
     * @param value 输入值
     * @return 布尔值
     */
    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        // 字符串需要转换
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /**
     * 从 Map 读取整数。
     *
     * @param map 数据源
     * @param key 键名
     * @return 整数值
     */
    private Integer readInteger(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // 非法数字直接返回空
                return null;
            }
        }
        return null;
    }

    /**
     * 结构化摘要容器，用于聚合召回结果中的摘要信息。
     */
    private static class StructuredSummary {

        /**
         * 会话级结构化摘要。
         */
        private ConversationSummary conversationSummary;
        /**
         * 工作记忆结构化摘要。
         */
        private WorkingMemorySummary workingMemorySummary;

        public ConversationSummary getConversationSummary() {
            return conversationSummary;
        }

        public void setConversationSummary(ConversationSummary conversationSummary) {
            this.conversationSummary = conversationSummary;
        }

        public WorkingMemorySummary getWorkingMemorySummary() {
            return workingMemorySummary;
        }

        public void setWorkingMemorySummary(WorkingMemorySummary workingMemorySummary) {
            this.workingMemorySummary = workingMemorySummary;
        }

        public boolean hasAny() {
            return conversationSummary != null || workingMemorySummary != null;
        }
    }
}
