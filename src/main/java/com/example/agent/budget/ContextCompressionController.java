package com.example.agent.budget;

import com.example.agent.auth.TenantContext;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.DomainKnowledge;
import com.example.agent.context.EvidenceItem;
import com.example.agent.context.EvidencePack;
import com.example.agent.context.LongTermMemory;
import com.example.agent.context.MemoryEvidence;
import com.example.agent.context.MemoryRef;
import com.example.agent.context.RoleBoundary;
import com.example.agent.context.TaskIntent;
import com.example.agent.context.ToolCallEvidence;
import com.example.agent.context.ToolCallState;
import com.example.agent.context.ToolState;
import com.example.agent.context.WorkingMemory;
import com.example.agent.memory.CompressionRequest;
import com.example.agent.memory.ConversationSummary;
import com.example.agent.memory.MemoryRecord;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.TokenEstimator;
import com.example.agent.memory.WorkingMemorySummary;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.tools.ToolSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 上下文压缩控制器，用于在预算超限时触发压缩并回填摘要。
 */
@Service
public class ContextCompressionController {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionController.class);

    private final MemoryStore memoryStore;
    private final TokenEstimator tokenEstimator;
    private final MetricsPublisher metricsPublisher;
    private final ContextCompressionProperties properties;
    private final Map<String, Instant> lastCompressedAt = new ConcurrentHashMap<>();

    public ContextCompressionController(MemoryStore memoryStore,
                                        TokenEstimator tokenEstimator,
                                        MetricsPublisher metricsPublisher,
                                        ContextCompressionProperties properties) {
        this.memoryStore = memoryStore;
        this.tokenEstimator = tokenEstimator;
        this.metricsPublisher = metricsPublisher;
        this.properties = properties;
    }

    /**
     * 在预算超限时触发压缩并回填摘要。
     *
     * @param request 压缩请求
     * @return 压缩结果
     */
    public ContextCompressionResult compressIfNeeded(ContextCompressionRequest request) {
        ContextCompressionResult result = new ContextCompressionResult();
        if (request == null || request.getSnapshot() == null) {
            return result;
        }
        ContextSnapshot snapshot = request.getSnapshot();
        result.setSnapshot(snapshot);
        if (properties != null && !properties.isEnabled()) {
            return result;
        }
        ContextBudgetAllocation allocation = request.getAllocation();
        if (allocation == null) {
            return result;
        }
        ContextTrimReport trimReport = request.getTrimReport();
        Map<ContextSection, Integer> sectionTokens = trimReport != null ? trimReport.getSectionTokensAfter() : null;
        Integer afterTrimTokens = trimReport != null ? trimReport.getTotalAfterTokens() : null;
        Integer beforeTokens = trimReport != null ? trimReport.getTotalBeforeTokens() : null;
        if (sectionTokens == null || afterTrimTokens == null) {
            sectionTokens = estimateSectionTokens(snapshot);
            afterTrimTokens = sumTokens(sectionTokens);
        }
        result.setBeforeTokens(beforeTokens);
        result.setAfterTrimTokens(afterTrimTokens);

        String reason = resolveTriggerReason(allocation, sectionTokens, afterTrimTokens);
        if (reason == null) {
            return result;
        }

        String workflowId = request.getWorkflowId();
        String sessionId = request.getSessionId();
        String cooldownKey = resolveCooldownKey(workflowId, sessionId);
        if (cooldownKey != null && isInCooldown(cooldownKey)) {
            metricsPublisher.incrementWithTags("context_compression_skipped_total", "cooldown", "true");
            result.setSkippedCooldown(true);
            return result;
        }

        if (!StringUtils.hasText(sessionId)) {
            log.warn("上下文压缩跳过，缺少会话标识, tenantId={}, workflowId={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId);
            return result;
        }
        if (memoryStore == null) {
            log.warn("上下文压缩跳过，记忆存储不可用, tenantId={}, workflowId={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId);
            return result;
        }

        long startNs = System.nanoTime();
        MemoryRecord compressed = memoryStore.compress(buildCompressionRequest(sessionId, workflowId),
                request.getTenantContext());
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        metricsPublisher.recordSummary("context_compression_duration_ms", durationMs);
        result.setDurationMs(durationMs);

        if (compressed == null) {
            log.warn("上下文压缩失败, tenantId={}, workflowId={}, durationMs={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId, durationMs);
            return result;
        }

        if (cooldownKey != null) {
            lastCompressedAt.put(cooldownKey, Instant.now());
        }

        String summaryVersion = applyCompressedSummary(snapshot, compressed);
        result.setSummaryVersion(summaryVersion);

        Map<ContextSection, Integer> sectionAfterCompress = estimateSectionTokens(snapshot);
        int afterCompressTokens = sumTokens(sectionAfterCompress);
        result.setAfterCompressTokens(afterCompressTokens);

        boolean stillOver = isOverBudget(allocation, sectionAfterCompress, afterCompressTokens);
        if (stillOver) {
            metricsPublisher.increment("context_compression_still_over_budget_total");
            log.warn("上下文压缩后仍超预算, tenantId={}, workflowId={}, afterCompressTokens={}, totalBudgetTokens={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId, afterCompressTokens,
                    allocation.getTotalTokens());
        }

        result.setTriggered(true);
        result.setTriggerReason(reason);
        result.setStillOverBudget(stillOver);
        recordTriggerMetrics(reason);

        log.info("上下文压缩完成, tenantId={}, workflowId={}, beforeTokens={}, afterTrimTokens={}, afterCompressTokens={}, "
                        + "triggerReason={}, compressionDurationMs={}, summaryVersion={}",
                resolveTenantId(request.getTenantContext(), snapshot),
                workflowId,
                beforeTokens,
                afterTrimTokens,
                afterCompressTokens,
                reason,
                durationMs,
                summaryVersion);
        return result;
    }

    private boolean isOverBudget(ContextBudgetAllocation allocation,
                                 Map<ContextSection, Integer> sectionTokens,
                                 Integer totalTokens) {
        if (allocation == null) {
            return false;
        }
        Integer totalBudget = allocation.getTotalTokens();
        boolean overTotal = totalBudget != null && totalBudget > 0
                && totalTokens != null && totalTokens > totalBudget;
        boolean overSection = isOverSectionBudget(sectionTokens, allocation.getSectionTokens());
        return overTotal || overSection;
    }

    private String resolveTriggerReason(ContextBudgetAllocation allocation,
                                        Map<ContextSection, Integer> sectionTokens,
                                        Integer totalTokens) {
        if (allocation == null) {
            return null;
        }
        Integer totalBudget = allocation.getTotalTokens();
        boolean overTotal = properties == null || !properties.isTriggerOverTotalBudget()
                ? false
                : totalBudget != null && totalBudget > 0 && totalTokens != null && totalTokens > totalBudget;
        boolean overSection = properties == null || !properties.isTriggerOverSectionBudget()
                ? false
                : isOverSectionBudget(sectionTokens, allocation.getSectionTokens());
        if (!overTotal && !overSection) {
            return null;
        }
        if (overTotal && overSection) {
            return "OVER_TOTAL|OVER_SECTION";
        }
        return overTotal ? "OVER_TOTAL" : "OVER_SECTION";
    }

    private boolean isOverSectionBudget(Map<ContextSection, Integer> sectionTokens,
                                        Map<ContextSection, Integer> budgets) {
        if (sectionTokens == null || budgets == null || budgets.isEmpty()) {
            return false;
        }
        for (Map.Entry<ContextSection, Integer> entry : sectionTokens.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Integer budget = budgets.get(entry.getKey());
            Integer value = entry.getValue();
            if (budget != null && value != null && value > budget) {
                return true;
            }
        }
        return false;
    }

    private boolean isInCooldown(String key) {
        int intervalSeconds = properties != null ? properties.getMinIntervalSeconds() : 0;
        if (intervalSeconds <= 0) {
            return false;
        }
        Instant last = lastCompressedAt.get(key);
        if (last == null) {
            return false;
        }
        // 使用内存时间戳进行防抖，避免频繁压缩造成抖动
        return Duration.between(last, Instant.now()).getSeconds() < intervalSeconds;
    }

    private String resolveCooldownKey(String workflowId, String sessionId) {
        if (StringUtils.hasText(workflowId)) {
            return workflowId;
        }
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return null;
    }

    private CompressionRequest buildCompressionRequest(String sessionId, String workflowId) {
        CompressionRequest request = new CompressionRequest();
        request.setSessionId(sessionId);
        request.setWorkflowId(workflowId);
        return request;
    }

    private void recordTriggerMetrics(String reason) {
        if (reason == null) {
            return;
        }
        if (reason.contains("OVER_TOTAL")) {
            metricsPublisher.incrementWithTags("context_compression_trigger_total", "reason", "OVER_TOTAL");
        }
        if (reason.contains("OVER_SECTION")) {
            metricsPublisher.incrementWithTags("context_compression_trigger_total", "reason", "OVER_SECTION");
        }
    }

    private String applyCompressedSummary(ContextSnapshot snapshot, MemoryRecord compressed) {
        if (snapshot == null || compressed == null) {
            return null;
        }
        WorkingMemory memory = snapshot.getWorkingMemory();
        if (memory == null) {
            memory = new WorkingMemory();
            snapshot.setWorkingMemory(memory);
        }
        ConversationSummary conversationSummary = compressed.getConversationSummary();
        WorkingMemorySummary workingSummary = compressed.getWorkingMemorySummary();
        String summaryText = firstNonBlank(
                conversationSummary != null ? conversationSummary.getSummary() : null,
                workingSummary != null ? workingSummary.getSummary() : null,
                conversationSummary != null ? conversationSummary.toLegacyText() : null,
                workingSummary != null ? workingSummary.toLegacyText() : null,
                compressed.getSummary()
        );
        if (StringUtils.hasText(summaryText)) {
            memory.setSummary(summaryText);
            memory.setSummaryChars(summaryText.length());
        }
        List<String> keyFacts = null;
        if (workingSummary != null && workingSummary.getItems() != null && !workingSummary.getItems().isEmpty()) {
            keyFacts = new ArrayList<>(workingSummary.getItems());
        } else if (conversationSummary != null && conversationSummary.getBullets() != null
                && !conversationSummary.getBullets().isEmpty()) {
            keyFacts = new ArrayList<>(conversationSummary.getBullets());
        }
        if (keyFacts != null) {
            memory.setKeyFacts(keyFacts);
            memory.setWorkingMemoryItems(keyFacts.size());
        }
        if (conversationSummary != null || workingSummary != null) {
            memory.setUsedStructuredSummary(true);
        }
        String summaryVersion = firstNonBlank(
                conversationSummary != null ? conversationSummary.getVersion() : null,
                workingSummary != null ? workingSummary.getVersion() : null
        );
        if (StringUtils.hasText(summaryVersion)) {
            memory.setSummaryVersion(summaryVersion);
        }
        Integer summaryChars = firstNonNull(
                conversationSummary != null ? conversationSummary.getSummaryChars() : null,
                workingSummary != null ? workingSummary.getSummaryChars() : null
        );
        if (summaryChars != null) {
            memory.setSummaryChars(summaryChars);
        }
        Integer itemsCount = firstNonNull(
                workingSummary != null ? workingSummary.getItemCount() : null,
                conversationSummary != null ? conversationSummary.getBulletCount() : null
        );
        if (itemsCount != null) {
            memory.setWorkingMemoryItems(itemsCount);
        }

        LongTermMemory longTermMemory = snapshot.getLongTermMemory();
        if (longTermMemory == null) {
            longTermMemory = new LongTermMemory();
            snapshot.setLongTermMemory(longTermMemory);
        }
        MemoryRef ref = new MemoryRef();
        ref.setMemoryId(compressed.getMemoryId());
        ref.setMemoryType("compressed");
        ref.setSnippet(StringUtils.hasText(summaryText) ? summaryText : compressed.getSummary());
        ref.setExpiresAt(compressed.getExpiresAt());
        ref.setSource("budget_compress");
        longTermMemory.setMemoryRefs(List.of(ref));

        return summaryVersion;
    }

    private String resolveTenantId(TenantContext tenantContext, ContextSnapshot snapshot) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTenantId())) {
            return tenantContext.getTenantId();
        }
        if (snapshot != null && snapshot.getRuntimeMeta() != null) {
            return snapshot.getRuntimeMeta().getTenantId();
        }
        return null;
    }

    private Map<ContextSection, Integer> estimateSectionTokens(ContextSnapshot snapshot) {
        EnumMap<ContextSection, Integer> tokens = new EnumMap<>(ContextSection.class);
        if (snapshot == null) {
            return tokens;
        }
        tokens.put(ContextSection.SYSTEM_POLICY, estimateRoleBoundarySystemTokens(snapshot.getRoleBoundary()));
        tokens.put(ContextSection.DEVELOPER_POLICY, estimateRoleBoundaryDeveloperTokens(snapshot.getRoleBoundary()));
        tokens.put(ContextSection.USER_INPUT, estimateTaskIntentTokens(snapshot.getTaskIntent()));
        tokens.put(ContextSection.WORKING_MEMORY, estimateWorkingMemoryTokens(snapshot.getWorkingMemory()));
        tokens.put(ContextSection.DOMAIN_KNOWLEDGE, estimateDomainKnowledgeTokens(snapshot.getDomainKnowledge()));
        tokens.put(ContextSection.LONG_TERM_MEMORY, estimateLongTermMemoryTokens(snapshot.getLongTermMemory()));
        tokens.put(ContextSection.EVIDENCE_PACK, estimateEvidencePackTokens(
                snapshot.getWorkingMemory() != null ? snapshot.getWorkingMemory().getEvidencePack() : null));
        tokens.put(ContextSection.TOOL_SUMMARY, estimateToolSummariesTokens(snapshot.getToolState()));
        tokens.put(ContextSection.TOOL_SCHEMA, 0);
        tokens.put(ContextSection.SLACK, 0);
        return tokens;
    }

    private int estimateTaskIntentTokens(TaskIntent intent) {
        if (intent == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(intent.getInputText());
        total += estimateTokens(intent.getSuccessCriteria());
        total += estimateTokens(intent.getFailurePolicy());
        total += estimateTokens(intent.getRequiredOutput());
        total += estimateTokens(intent.getConstraints());
        return total;
    }

    private int estimateRoleBoundarySystemTokens(RoleBoundary boundary) {
        if (boundary == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(boundary.getSystemPolicyId());
        total += estimateTokens(boundary.getRiskLevel());
        total += estimateTokens(boundary.getForbiddenActions());
        total += estimateTokens(boundary.getDataScopes());
        return total;
    }

    private int estimateRoleBoundaryDeveloperTokens(RoleBoundary boundary) {
        if (boundary == null) {
            return 0;
        }
        return estimateTokens(boundary.getDeveloperPolicyId());
    }

    private int estimateWorkingMemoryTokens(WorkingMemory memory) {
        if (memory == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(memory.getSummary());
        total += estimateTokens(memory.getKeyFacts());
        total += estimateTokens(memory.getPlanSteps());
        total += estimateTokens(memory.getNextStep());
        total += estimateToolCallStateTokens(memory.getRecentToolCalls());
        return total;
    }

    private int estimateDomainKnowledgeTokens(DomainKnowledge knowledge) {
        if (knowledge == null || knowledge.getCitations() == null) {
            return 0;
        }
        int total = 0;
        for (com.example.agent.context.Citation citation : knowledge.getCitations()) {
            total += estimateCitationTokens(citation);
        }
        return total;
    }

    private int estimateLongTermMemoryTokens(LongTermMemory memory) {
        if (memory == null || memory.getMemoryRefs() == null) {
            return 0;
        }
        int total = 0;
        for (MemoryRef ref : memory.getMemoryRefs()) {
            total += estimateMemoryRefTokens(ref);
        }
        return total;
    }

    private int estimateEvidencePackTokens(EvidencePack pack) {
        if (pack == null) {
            return 0;
        }
        int total = 0;
        if (pack.getToolCalls() != null) {
            for (ToolCallEvidence call : pack.getToolCalls()) {
                total += estimateToolCallTokens(call);
            }
        }
        if (pack.getMemoriesUsed() != null) {
            for (MemoryEvidence memory : pack.getMemoriesUsed()) {
                total += estimateMemoryEvidenceTokens(memory);
            }
        }
        if (pack.getCitations() != null) {
            for (com.example.agent.context.Citation citation : pack.getCitations()) {
                total += estimateCitationTokens(citation);
            }
        }
        if (pack.getItems() != null) {
            for (EvidenceItem item : pack.getItems()) {
                total += estimateEvidenceItemTokens(item);
            }
        }
        return total;
    }

    private int estimateToolSummariesTokens(ToolState toolState) {
        if (toolState == null || toolState.getAvailableTools() == null) {
            return 0;
        }
        int total = 0;
        for (ToolSummary tool : toolState.getAvailableTools()) {
            total += estimateToolSummaryTokens(tool);
        }
        return total;
    }

    private int estimateToolCallStateTokens(List<ToolCallState> calls) {
        if (calls == null) {
            return 0;
        }
        int total = 0;
        for (ToolCallState call : calls) {
            total += estimateToolCallStateTokens(call);
        }
        return total;
    }

    private int estimateToolCallStateTokens(ToolCallState call) {
        if (call == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(call.getToolName());
        total += estimateTokens(call.getRequestId());
        total += estimateTokens(call.getErrorCode());
        return total;
    }

    private int estimateToolCallTokens(ToolCallEvidence evidence) {
        if (evidence == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(evidence.getToolName());
        total += estimateTokens(evidence.getArgsDigest());
        total += estimateTokens(evidence.getResultDigest());
        total += estimateTokens(evidence.getStatus());
        total += estimateTokens(evidence.getErrorCode());
        total += estimateTokens(evidence.getToolCallId());
        return total;
    }

    private int estimateToolSummaryTokens(ToolSummary tool) {
        if (tool == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(tool.getToolName());
        total += estimateTokens(tool.getDescription());
        total += estimateTokens(tool.getTags());
        total += estimateTokens(tool.getCostLevel());
        total += estimateTokens(tool.getLatencyLevel());
        total += estimateTokens(tool.getAuthScope());
        return total;
    }

    private int estimateMemoryEvidenceTokens(MemoryEvidence evidence) {
        if (evidence == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(evidence.getMemoryId());
        total += estimateTokens(evidence.getSummaryVersion());
        return total;
    }

    private int estimateMemoryRefTokens(MemoryRef ref) {
        if (ref == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(ref.getMemoryId());
        total += estimateTokens(ref.getMemoryType());
        total += estimateTokens(ref.getSnippet());
        total += estimateTokens(ref.getSource());
        return total;
    }

    private int estimateCitationTokens(com.example.agent.context.Citation citation) {
        if (citation == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(citation.getType());
        total += estimateTokens(citation.getRefId());
        total += estimateTokens(citation.getLabel());
        total += estimateTokens(citation.getSource());
        total += estimateTokens(citation.getTitle());
        total += estimateTokens(citation.getUri());
        total += estimateTokens(citation.getSnippet());
        return total;
    }

    private int estimateEvidenceItemTokens(EvidenceItem item) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(item.getSourceType());
        total += estimateTokens(item.getSourceId());
        total += estimateTokens(item.getUri());
        total += estimateTokens(item.getTitle());
        total += estimateTokens(item.getSnippet());
        total += estimateTokens(item.getHash());
        return total;
    }

    private int estimateTokens(String text) {
        return tokenEstimator != null ? tokenEstimator.estimateTokens(text) : 0;
    }

    private int estimateTokens(List<String> values) {
        if (values == null) {
            return 0;
        }
        int total = 0;
        for (String value : values) {
            total += estimateTokens(value);
        }
        return total;
    }

    private int sumTokens(Map<ContextSection, Integer> tokens) {
        int total = 0;
        if (tokens == null) {
            return total;
        }
        for (Integer value : tokens.values()) {
            total += value == null ? 0 : value;
        }
        return total;
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
}
