package com.example.agent.capabilities.context.builder;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.model.AuditMetadata;
import com.example.agent.capabilities.context.model.BudgetState;
import com.example.agent.capabilities.context.model.BuildMetrics;
import com.example.agent.capabilities.context.model.Citation;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.DomainKnowledge;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.RoleBoundary;
import com.example.agent.capabilities.context.model.RuntimeMeta;
import com.example.agent.capabilities.context.model.TaskIntent;
import com.example.agent.capabilities.context.model.ToolState;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.context.research.ResearchCitation;
import com.example.agent.capabilities.context.runtime.ContextRuntimeKeys;
import com.example.agent.capabilities.context.runtime.ContextRuntimeReadContext;
import com.example.agent.capabilities.context.runtime.ContextRuntimeView;
import com.example.agent.capabilities.context.runtime.ContextRuntimeViews;
import com.example.agent.capabilities.memory.model.ConversationSummary;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.model.WorkingMemorySummary;
import com.example.agent.capabilities.memory.recall.MemoryRecallResult;
import com.example.agent.capabilities.tools.ToolCatalogService;
import com.example.agent.capabilities.tools.ToolQuery;
import com.example.agent.capabilities.tools.ToolSummary;
import com.example.agent.runtime.react.ReactObservation;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;

/**
 * 上下文快照工厂。
 *
 * <p>用途：封装快照子对象构建逻辑，降低 Builder 主流程的职责密度。
 */
public class ContextSnapshotFactory {

    private final ToolCatalogService toolCatalog;
    private final Logger log;
    private final MetricsPublisher metricsPublisher;
    private final int maxWorkingSummaryChars;

    /**
     * 构造快照工厂。
     *
     * @param toolCatalog 工具目录
     * @param log 日志记录器
     * @param metricsPublisher 指标发布器
     * @param maxWorkingSummaryChars 工作记忆摘要最大字符数
     */
    public ContextSnapshotFactory(ToolCatalogService toolCatalog,
                                  Logger log,
                                  MetricsPublisher metricsPublisher,
                                  int maxWorkingSummaryChars) {
        this.toolCatalog = toolCatalog;
        this.log = log;
        this.metricsPublisher = metricsPublisher;
        this.maxWorkingSummaryChars = maxWorkingSummaryChars;
    }

    /**
     * 创建空快照并生成快照标识。
     *
     * @return 上下文快照
     */
    public ContextSnapshot createSnapshot() {
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId(UUID.randomUUID().toString());
        return snapshot;
    }

    /**
     * 构建运行时元信息。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param context 运行时上下文
     * @param taskId 任务标识
     * @return 运行时元信息
     */
    public RuntimeMeta buildRuntimeMeta(TaskRequest request,
                                        TenantContext tenantContext,
                                        String workflowId,
                                        Map<String, Object> context,
                                        String taskId) {
        ContextRuntimeView runtimeView = newRuntimeView(context, tenantContext, workflowId, taskId,
                "BUILD_RUNTIME_META");
        RuntimeMeta meta = new RuntimeMeta();
        if (tenantContext != null) {
            meta.setTenantId(tenantContext.getTenantId());
            meta.setUserId(tenantContext.getUserId());
            meta.setTraceId(tenantContext.getTraceId());
            meta.setRequestId(tenantContext.getRequestId());
        }
        if (request != null) {
            meta.setSessionId(request.getSessionId());
        }
        meta.setWorkflowId(workflowId);
        meta.setLocale(runtimeView.getString(ContextRuntimeKeys.LOCALE));
        meta.setOutputFormat(runtimeView.getString(ContextRuntimeKeys.OUTPUT_FORMAT));
        meta.setAllowedTools(runtimeView.getStringList(ContextRuntimeKeys.ALLOWED_TOOLS));
        meta.setRequestTime(Instant.now());
        return meta;
    }

    /**
     * 构建角色边界。
     *
     * @param context 运行时上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @return 角色边界
     */
    public RoleBoundary buildRoleBoundary(Map<String, Object> context,
                                          TenantContext tenantContext,
                                          String workflowId,
                                          String taskId) {
        ContextRuntimeView runtimeView = newRuntimeView(context, tenantContext, workflowId, taskId,
                "BUILD_ROLE_BOUNDARY");
        RoleBoundary boundary = new RoleBoundary();
        boundary.setSystemPolicyId(runtimeView.getString(ContextRuntimeKeys.SYSTEM_POLICY_ID));
        boundary.setDeveloperPolicyId(runtimeView.getString(ContextRuntimeKeys.DEVELOPER_POLICY_ID));
        boundary.setForbiddenActions(runtimeView.getStringList(ContextRuntimeKeys.FORBIDDEN_ACTIONS));
        boundary.setDataScopes(runtimeView.getStringList(ContextRuntimeKeys.DATA_SCOPES));
        boundary.setRiskLevel(runtimeView.getString(ContextRuntimeKeys.RISK_LEVEL));
        boundary.setApprovalRequired(runtimeView.getBoolean(ContextRuntimeKeys.REQUIRES_APPROVAL));
        return boundary;
    }

    /**
     * 构建任务意图。
     *
     * @param request 任务请求
     * @param context 运行时上下文
     * @param taskId 任务标识
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @return 任务意图
     */
    public TaskIntent buildTaskIntent(TaskRequest request,
                                      Map<String, Object> context,
                                      String taskId,
                                      TenantContext tenantContext,
                                      String workflowId) {
        ContextRuntimeView runtimeView = newRuntimeView(context, tenantContext, workflowId, taskId,
                "BUILD_TASK_INTENT");
        TaskIntent intent = new TaskIntent();
        intent.setTaskId(taskId);
        if (request != null) {
            intent.setInputText(request.getQuery());
        }
        intent.setSuccessCriteria(runtimeView.getString(ContextRuntimeKeys.SUCCESS_CRITERIA));
        intent.setFailurePolicy(runtimeView.getString(ContextRuntimeKeys.FAILURE_POLICY));
        intent.setRequiredOutput(runtimeView.getString(ContextRuntimeKeys.REQUIRED_OUTPUT));
        intent.setConstraints(runtimeView.getStringList(ContextRuntimeKeys.CONSTRAINTS));
        return intent;
    }

    /**
     * 构建工作记忆。
     *
     * @param recallResult 召回结果
     * @param observations 观察记录
     * @param context 运行时上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @return 工作记忆
     */
    public WorkingMemory buildWorkingMemory(MemoryRecallResult recallResult,
                                            List<ReactObservation> observations,
                                            Map<String, Object> context,
                                            TenantContext tenantContext,
                                            String workflowId,
                                            String taskId) {
        ContextRuntimeView runtimeView = newRuntimeView(context, tenantContext, workflowId, taskId,
                "BUILD_WORKING_MEMORY");
        WorkingMemory memory = new WorkingMemory();
        boolean usedStructuredSummary = false;
        if (recallResult != null && recallResult.isUsed()) {
            StructuredSummary structuredSummary = resolveStructuredSummary(recallResult.getRecords());
            if (structuredSummary != null && structuredSummary.hasAny()) {
                usedStructuredSummary = true;
                applyStructuredSummary(memory, structuredSummary);
            }
            if (!StringUtils.hasText(memory.getSummary())) {
                memory.setSummary(trimText(recallResult.getSummary(), maxWorkingSummaryChars));
            }
            if (memory.getKeyFacts() == null || memory.getKeyFacts().isEmpty()) {
                memory.setKeyFacts(extractKeyFacts(recallResult.getRecords()));
            }
        }
        if (!StringUtils.hasText(memory.getSummary()) && observations != null && !observations.isEmpty()) {
            memory.setSummary(trimText(buildObservationSummary(observations), maxWorkingSummaryChars));
        }
        memory.setPlanSteps(runtimeView.getStringList(ContextRuntimeKeys.PLAN_STEPS));
        memory.setNextStep(runtimeView.getString(ContextRuntimeKeys.NEXT_STEP));
        memory.setEvidencePack(runtimeView.getEvidencePack());
        if (recallResult != null && recallResult.isUsed()) {
            memory.setUsedStructuredSummary(usedStructuredSummary);
        }
        if (recallResult != null && recallResult.getRedactionsAppliedCount() > 0) {
            memory.setRedactionsAppliedCount(recallResult.getRedactionsAppliedCount());
        }
        if (memory.getSummaryChars() == null) {
            memory.setSummaryChars(memory.getSummary() != null ? memory.getSummary().length() : 0);
        }
        if (memory.getWorkingMemoryItems() == null) {
            memory.setWorkingMemoryItems(memory.getKeyFacts() != null ? memory.getKeyFacts().size() : 0);
        }
        if (usedStructuredSummary
                && (memory.getSummaryVersion() == null || memory.getSummaryVersion().isBlank())) {
            memory.setSummaryVersion("v1");
        }
        return memory;
    }

    /**
     * 构建领域知识。
     *
     * @param context 运行时上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @return 领域知识
     */
    public DomainKnowledge buildDomainKnowledge(Map<String, Object> context,
                                                TenantContext tenantContext,
                                                String workflowId,
                                                String taskId) {
        ContextRuntimeView runtimeView = newRuntimeView(context, tenantContext, workflowId, taskId,
                "BUILD_DOMAIN_KNOWLEDGE");
        DomainKnowledge knowledge = new DomainKnowledge();
        Object citationsObj = runtimeView.getCitationItems();
        List<Citation> citations = new ArrayList<>();
        if (citationsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Citation citation) {
                    citations.add(citation);
                } else if (item instanceof ResearchCitation research) {
                    Citation citation = new Citation();
                    citation.setSource(research.getSource());
                    citation.setSnippet(research.getSnippet());
                    citation.setFetchedAt(research.getFetchedAt());
                    citations.add(citation);
                } else if (item instanceof Map<?, ?> map) {
                    Citation citation = new Citation();
                    citation.setSource(readMapString(map, "source"));
                    citation.setTitle(readMapString(map, "title"));
                    citation.setUri(readMapString(map, "uri"));
                    citation.setSnippet(readMapString(map, "snippet"));
                    citations.add(citation);
                }
            }
        }
        knowledge.setCitations(citations.isEmpty() ? null : citations);
        return knowledge;
    }

    /**
     * 构建长期记忆。
     *
     * @param recallResult 召回结果
     * @param context 运行时上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @return 长期记忆
     */
    public LongTermMemory buildLongTermMemory(MemoryRecallResult recallResult,
                                              Map<String, Object> context,
                                              TenantContext tenantContext,
                                              String workflowId,
                                              String taskId) {
        ContextRuntimeView runtimeView = newRuntimeView(context, tenantContext, workflowId, taskId,
                "BUILD_LONG_TERM_MEMORY");
        LongTermMemory memory = new LongTermMemory();
        List<MemoryRef> refs = new ArrayList<>();
        List<?> list = runtimeView.getLongTermMemoryRefs();
        if (list != null) {
            for (Object item : list) {
                if (item instanceof MemoryRef ref) {
                    refs.add(ref);
                }
            }
        }
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
        memory.setMemoryRefs(refs.isEmpty() ? null : refs);
        return memory;
    }

    /**
     * 构建工具状态。
     *
     * @param query 工具查询
     * @param context 运行时上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @return 工具状态
     */
    public ToolState buildToolState(ToolQuery query,
                                    Map<String, Object> context,
                                    TenantContext tenantContext,
                                    String workflowId,
                                    String taskId) {
        ContextRuntimeView runtimeView = newRuntimeView(context, tenantContext, workflowId, taskId,
                "BUILD_TOOL_STATE");
        ToolState state = new ToolState();
        if (toolCatalog != null) {
            List<ToolSummary> summaries = toolCatalog.listSummaries(query != null ? query : new ToolQuery());
            state.setAvailableTools(summaries == null || summaries.isEmpty() ? null : summaries);
        }
        state.setSelectedTools(runtimeView.getStringList(ContextRuntimeKeys.SELECTED_TOOLS));
        state.setLastError(runtimeView.getString(ContextRuntimeKeys.LAST_TOOL_ERROR));
        return state;
    }

    /**
     * 构建预算状态。
     *
     * @param allocation 预算分配
     * @return 预算状态
     */
    public BudgetState buildBudgetState(com.example.agent.budget.token.ContextBudgetAllocation allocation) {
        BudgetState state = new BudgetState();
        if (allocation == null) {
            return state;
        }
        state.setAllocatedTokens(allocation.getTotalTokens());
        state.setRemainingTokens(allocation.getTotalTokens() != null ? allocation.getTotalTokens() : null);
        state.setUsedTokens(0);
        return state;
    }

    /**
     * 构建审计元数据。
     *
     * @return 审计元数据
     */
    public AuditMetadata buildAuditMetadata() {
        AuditMetadata metadata = new AuditMetadata();
        metadata.setVersion("v1");
        metadata.setSource("context_builder");
        metadata.setCreatedAt(Instant.now());
        return metadata;
    }

    /**
     * 构建构建指标。
     *
     * @param snapshot 上下文快照
     * @param recallResult 召回结果
     * @param elapsedNs 构建耗时纳秒
     * @return 构建指标
     */
    public BuildMetrics buildMetrics(ContextSnapshot snapshot,
                                     MemoryRecallResult recallResult,
                                     long elapsedNs) {
        BuildMetrics metrics = new BuildMetrics();
        metrics.setBuildMillis(Math.max(0, elapsedNs / 1_000_000));
        int retrievalCount = 0;
        if (recallResult != null && recallResult.isUsed()) {
            retrievalCount += recallResult.getCount();
        }
        if (snapshot != null && snapshot.getDomainKnowledge() != null
                && snapshot.getDomainKnowledge().getCitations() != null) {
            retrievalCount += snapshot.getDomainKnowledge().getCitations().size();
        }
        metrics.setRetrievalCount(retrievalCount);
        int toolCount = 0;
        if (snapshot != null && snapshot.getToolState() != null
                && snapshot.getToolState().getAvailableTools() != null) {
            toolCount = snapshot.getToolState().getAvailableTools().size();
        }
        metrics.setToolCount(toolCount);
        return metrics;
    }

    /**
     * 解析结构化摘要。
     *
     * @param records 召回记录
     * @return 结构化摘要
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
            if (summary.getConversationSummary() == null && record.getConversationSummary() != null) {
                summary.setConversationSummary(record.getConversationSummary());
            }
            if (summary.getWorkingMemorySummary() == null && record.getWorkingMemorySummary() != null) {
                summary.setWorkingMemorySummary(record.getWorkingMemorySummary());
            }
            if (summary.getConversationSummary() != null && summary.getWorkingMemorySummary() != null) {
                break;
            }
        }
        return summary.hasAny() ? summary : null;
    }

    /**
     * 应用结构化摘要。
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
            String summaryText = firstNonBlank(conversationSummary.getSummary(), conversationSummary.toLegacyText());
            if (StringUtils.hasText(summaryText)) {
                memory.setSummary(trimText(summaryText, maxWorkingSummaryChars));
            }
            if (conversationSummary.getBullets() != null && !conversationSummary.getBullets().isEmpty()
                    && (memory.getKeyFacts() == null || memory.getKeyFacts().isEmpty())) {
                memory.setKeyFacts(new ArrayList<>(conversationSummary.getBullets()));
            }
        }
        WorkingMemorySummary workingSummary = structuredSummary.getWorkingMemorySummary();
        if (workingSummary != null) {
            if (workingSummary.getItems() != null && !workingSummary.getItems().isEmpty()) {
                memory.setKeyFacts(new ArrayList<>(workingSummary.getItems()));
            }
            if (!StringUtils.hasText(memory.getSummary())) {
                String summaryText = firstNonBlank(workingSummary.getSummary(), workingSummary.toLegacyText());
                if (StringUtils.hasText(summaryText)) {
                    memory.setSummary(trimText(summaryText, maxWorkingSummaryChars));
                }
            }
        }
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
     * 从召回记录提取关键事实。
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
            String text = firstNonBlank(record.getSummary(), record.getContent());
            if (StringUtils.hasText(text)) {
                facts.add(trimText(text, 200));
            }
        }
        return facts.isEmpty() ? null : facts;
    }

    /**
     * 构建观察摘要。
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
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(index).append(":").append(trimText(observation.getContent(), 200));
            index++;
            if (builder.length() > maxWorkingSummaryChars) {
                break;
            }
        }
        return builder.toString();
    }

    /**
     * 创建运行时读取视图。
     *
     * @param context 运行时上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param stage 当前阶段
     * @return 运行时读取视图
     */
    private ContextRuntimeView newRuntimeView(Map<String, Object> context,
                                              TenantContext tenantContext,
                                              String workflowId,
                                              String taskId,
                                              String stage) {
        ContextRuntimeReadContext readContext = new ContextRuntimeReadContext(
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                taskId,
                stage);
        return ContextRuntimeViews.readOnly(context, log, metricsPublisher, readContext);
    }

    /**
     * 截断文本。
     *
     * @param text 原文本
     * @param maxChars 最大长度
     * @return 截断后文本
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
     * 获取首个非空字符串。
     *
     * @param values 候选值
     * @return 首个非空字符串
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
     * 获取首个非空整数。
     *
     * @param first 第一值
     * @param second 第二值
     * @return 首个非空整数
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
    private String readMapString(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 结构化摘要容器。
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

