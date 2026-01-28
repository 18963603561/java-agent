package com.example.agent.context;

import com.example.agent.auth.TenantContext;
import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextBudgetAllocator;
import com.example.agent.budget.ContextBudgetPolicy;
import com.example.agent.budget.ContextBudgetProperties;
import com.example.agent.budget.ContextBudgetRequest;
import com.example.agent.budget.ContextPruneRequest;
import com.example.agent.budget.ContextPruneResult;
import com.example.agent.budget.ContextPruner;
import com.example.agent.common.TaskRequest;
import com.example.agent.memory.ConversationSummary;
import com.example.agent.memory.MemoryRecallResult;
import com.example.agent.memory.MemoryRecord;
import com.example.agent.memory.WorkingMemorySummary;
import com.example.agent.research.ResearchCitation;
import com.example.agent.runtime.ReactObservation;
import com.example.agent.tools.ToolCatalog;
import com.example.agent.tools.ToolQuery;
import com.example.agent.tools.ToolSummary;
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

    private static final Logger log = LoggerFactory.getLogger(DefaultContextBuilder.class);

    private final ToolCatalog toolCatalog;
    private final ContextBudgetAllocator budgetAllocator;
    private final ContextPruner contextPruner;
    private final ContextBudgetProperties budgetProperties;

    @Value("${agent.context.default-token-budget:4096}")
    private int defaultTokenBudget;

    @Value("${agent.context.max-working-summary-chars:512}")
    private int maxWorkingSummaryChars;

    public DefaultContextBuilder(ToolCatalog toolCatalog,
                                 ContextBudgetAllocator budgetAllocator,
                                 ContextPruner contextPruner,
                                 ContextBudgetProperties budgetProperties) {
        this.toolCatalog = toolCatalog;
        this.budgetAllocator = budgetAllocator;
        this.contextPruner = contextPruner;
        this.budgetProperties = budgetProperties;
    }

    @Override
    public ContextBuildResult build(ContextBuildRequest request) {
        long startNs = System.nanoTime();
        ContextBuildResult result = new ContextBuildResult();
        if (request == null) {
            log.warn("上下文构建请求为空");
            return result;
        }
        TenantContext tenantContext = request.getTenantContext();
        TaskRequest taskRequest = request.getTaskRequest();
        Map<String, Object> runtimeContext = request.getRuntimeContext();
        log.debug("上下文构建开始, tenantId={}, workflowId={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                request.getWorkflowId());
        try {
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

            ContextBudgetRequest budgetRequest = resolveBudgetRequest(request, runtimeContext);
            if (runtimeMeta != null && budgetRequest != null && budgetRequest.isEnabled()) {
                runtimeMeta.setTokenBudget(budgetRequest.getTotalTokens());
            }
            snapshot.setRuntimeMeta(runtimeMeta);
            ContextBudgetAllocation allocation = budgetAllocator != null && budgetRequest != null
                    ? budgetAllocator.allocate(budgetRequest)
                    : null;
            snapshot.setBudgetState(buildBudgetState(allocation));

            ContextPruneResult pruneResult = null;
            if (contextPruner != null && allocation != null) {
                pruneResult = contextPruner.prune(new ContextPruneRequest(snapshot, allocation, request.getPolicy()));
                if (pruneResult != null && pruneResult.getPrunedSnapshot() != null) {
                    snapshot = pruneResult.getPrunedSnapshot();
                }
            }

            BuildMetrics metrics = buildMetrics(snapshot, request.getRecallResult(), System.nanoTime() - startNs);
            result.setSnapshot(snapshot);
            result.setBudgetAllocation(allocation);
            result.setPruneResult(pruneResult);
            result.setMetrics(metrics);

            log.info("上下文构建完成, tenantId={}, snapshotId={}, buildMillis={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    snapshot.getSnapshotId(),
                    metrics != null ? metrics.getBuildMillis() : null);
            return result;
        } catch (Exception ex) {
            log.error("上下文构建失败, tenantId={}", tenantContext != null ? tenantContext.getTenantId() : null, ex);
            return result;
        }
    }

    private RuntimeMeta buildRuntimeMeta(TaskRequest request,
                                         TenantContext tenantContext,
                                         String workflowId,
                                         Map<String, Object> context) {
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
        if (context != null) {
            meta.setLocale(readString(context, "locale"));
            meta.setOutputFormat(readString(context, "outputFormat"));
            meta.setAllowedTools(readStringList(context.get("allowedTools")));
        }
        meta.setRequestTime(Instant.now());
        return meta;
    }

    private RoleBoundary buildRoleBoundary(Map<String, Object> context) {
        RoleBoundary boundary = new RoleBoundary();
        if (context == null) {
            return boundary;
        }
        boundary.setSystemPolicyId(readString(context, "systemPolicyId"));
        boundary.setDeveloperPolicyId(readString(context, "developerPolicyId"));
        boundary.setForbiddenActions(readStringList(context.get("forbiddenActions")));
        boundary.setDataScopes(readStringList(context.get("dataScopes")));
        boundary.setRiskLevel(readString(context, "riskLevel"));
        boundary.setApprovalRequired(readBoolean(context.get("requiresApproval")));
        return boundary;
    }

    private TaskIntent buildTaskIntent(TaskRequest request, Map<String, Object> context, String taskId) {
        TaskIntent intent = new TaskIntent();
        intent.setTaskId(taskId);
        if (request != null) {
            intent.setInputText(request.getQuery());
        }
        if (context != null) {
            intent.setSuccessCriteria(readString(context, "successCriteria"));
            intent.setFailurePolicy(readString(context, "failurePolicy"));
            intent.setRequiredOutput(readString(context, "requiredOutput"));
            intent.setConstraints(readStringList(context.get("constraints")));
        }
        return intent;
    }

    private WorkingMemory buildWorkingMemory(MemoryRecallResult recallResult,
                                             List<ReactObservation> observations,
                                             Map<String, Object> context) {
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
        if (context != null) {
            memory.setPlanSteps(readStringList(context.get("planSteps")));
            memory.setNextStep(readString(context, "nextStep"));
            Object evidenceObj = context.get(EvidencePackService.CONTEXT_EVIDENCE_PACK);
            if (evidenceObj instanceof EvidencePack evidencePack) {
                memory.setEvidencePack(evidencePack);
            }
        }
        if (recallResult != null && recallResult.isUsed()) {
            memory.setUsedStructuredSummary(usedStructuredSummary);
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

    private DomainKnowledge buildDomainKnowledge(Map<String, Object> context) {
        DomainKnowledge knowledge = new DomainKnowledge();
        if (context == null) {
            return knowledge;
        }
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
                    Citation citation = new Citation();
                    citation.setSource(research.getSource());
                    citation.setSnippet(research.getSnippet());
                    citation.setFetchedAt(research.getFetchedAt());
                    citations.add(citation);
                } else if (item instanceof Map<?, ?> map) {
                    Citation citation = new Citation();
                    citation.setSource(readString(map, "source"));
                    citation.setTitle(readString(map, "title"));
                    citation.setUri(readString(map, "uri"));
                    citation.setSnippet(readString(map, "snippet"));
                    citations.add(citation);
                }
            }
        }
        knowledge.setCitations(citations.isEmpty() ? null : citations);
        return knowledge;
    }

    private LongTermMemory buildLongTermMemory(MemoryRecallResult recallResult, Map<String, Object> context) {
        LongTermMemory memory = new LongTermMemory();
        List<MemoryRef> refs = new ArrayList<>();
        if (context != null && context.get("longTermMemoryRefs") instanceof List<?> list) {
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

    private ToolState buildToolState(ToolQuery query, Map<String, Object> context) {
        ToolState state = new ToolState();
        if (toolCatalog != null) {
            List<ToolSummary> summaries = toolCatalog.listSummaries(query != null ? query : new ToolQuery());
            state.setAvailableTools(summaries == null || summaries.isEmpty() ? null : summaries);
        }
        if (context != null) {
            state.setSelectedTools(readStringList(context.get("selectedTools")));
            state.setLastError(readString(context, "lastToolError"));
        }
        return state;
    }

    private BudgetState buildBudgetState(ContextBudgetAllocation allocation) {
        BudgetState state = new BudgetState();
        if (allocation == null) {
            return state;
        }
        state.setAllocatedTokens(allocation.getTotalTokens());
        state.setRemainingTokens(allocation.getTotalTokens() != null ? allocation.getTotalTokens() : null);
        state.setUsedTokens(0);
        return state;
    }

    private AuditMetadata buildAuditMetadata() {
        AuditMetadata metadata = new AuditMetadata();
        metadata.setVersion("v1");
        metadata.setSource("context_builder");
        metadata.setCreatedAt(Instant.now());
        return metadata;
    }

    private BuildMetrics buildMetrics(ContextSnapshot snapshot, MemoryRecallResult recallResult, long elapsedNs) {
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

    private void fillBudgetRequest(ContextBudgetRequest budgetRequest,
                                   ContextBuildRequest request,
                                   Map<String, Object> context) {
        if (budgetRequest == null || request == null) {
            return;
        }
        Integer totalTokens = budgetRequest.getTotalTokens();
        if (totalTokens == null || totalTokens <= 0) {
            totalTokens = readInteger(context, "tokenBudget");
        }
        if (totalTokens == null || totalTokens <= 0) {
            totalTokens = budgetProperties != null ? budgetProperties.getTotalBudgetTokens() : null;
        }
        if (totalTokens == null || totalTokens <= 0) {
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
        if (tenantContext != null && (budgetRequest.getTenantId() == null || budgetRequest.getTenantId().isBlank())) {
            budgetRequest.setTenantId(tenantContext.getTenantId());
        }
        if (budgetRequest.getWorkflowId() == null || budgetRequest.getWorkflowId().isBlank()) {
            budgetRequest.setWorkflowId(request.getWorkflowId());
        }
        if (budgetRequest.getTaskId() == null || budgetRequest.getTaskId().isBlank()) {
            budgetRequest.setTaskId(request.getTaskId());
        }
        if (budgetRequest.getBudgetPolicy() == null && budgetProperties != null) {
            ContextBudgetPolicy policy = budgetProperties.toPolicy();
            budgetRequest.setBudgetPolicy(policy);
        }
    }

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

    private Integer firstNonNull(Integer first, Integer second) {
        if (first != null) {
            return first;
        }
        return second;
    }

    private String readString(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

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
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return null;
    }

    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private Integer readInteger(Map<String, Object> map, String key) {
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
                return null;
            }
        }
        return null;
    }

    /**
     * 结构化摘要容器，用于聚合召回结果中的摘要信息。
     */
    private static class StructuredSummary {

        private ConversationSummary conversationSummary;
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
