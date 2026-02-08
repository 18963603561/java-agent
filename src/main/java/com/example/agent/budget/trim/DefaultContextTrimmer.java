package com.example.agent.budget.trim;

import com.example.agent.capabilities.context.Citation;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.DomainKnowledge;
import com.example.agent.capabilities.context.EvidenceItem;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.EvidenceType;
import com.example.agent.capabilities.context.LongTermMemory;
import com.example.agent.capabilities.context.MemoryRef;
import com.example.agent.capabilities.context.RoleBoundary;
import com.example.agent.capabilities.context.TaskIntent;
import com.example.agent.capabilities.context.ToolCallState;
import com.example.agent.capabilities.context.ToolState;
import com.example.agent.capabilities.context.WorkingMemory;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.tools.ToolSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.token.ContextBudgetProperties;

/**
 * 上下文裁剪器默认实现，用于按预算策略裁剪上下文内容。
 */
@Service
public class DefaultContextTrimmer implements ContextTrimmer {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextTrimmer.class);

    private static final int MAX_RESULT_DIGEST_CHARS = 200;
    private static final int MAX_ARGS_DIGEST_CHARS = 200;
    private static final int MAX_TOOL_DESC_CHARS = 160;

    private final TokenEstimator tokenEstimator;
    private final MetricsPublisher metricsPublisher;
    private final ContextBudgetProperties properties;

    public DefaultContextTrimmer(TokenEstimator tokenEstimator,
                                 MetricsPublisher metricsPublisher,
                                 ContextBudgetProperties properties) {
        this.tokenEstimator = tokenEstimator;
        this.metricsPublisher = metricsPublisher;
        this.properties = properties;
    }

    @Override
    public ContextTrimResult trim(ContextTrimRequest request) {
        ContextTrimResult result = new ContextTrimResult();
        if (request == null || request.getSnapshot() == null || request.getAllocation() == null) {
            return result;
        }
        ContextSnapshot snapshot = request.getSnapshot();
        result.setTrimmedSnapshot(snapshot);
        if (properties != null && !properties.isEnabled()) {
            return result;
        }
        ContextBudgetAllocation allocation = request.getAllocation();
        Map<ContextSection, Integer> budgets = allocation.getSectionTokens();
        if (budgets == null || budgets.isEmpty()) {
            return result;
        }

        Map<ContextSection, Integer> beforeTokens = estimateSectionTokens(snapshot);
        int totalBefore = sumTokens(beforeTokens);

        ContextTrimReport report = new ContextTrimReport();
        report.setTotalBeforeTokens(totalBefore);
        report.setSectionTokensBefore(beforeTokens);

        boolean overSection = isOverSectionBudget(beforeTokens, budgets);
        Integer totalBudget = allocation.getTotalTokens();
        boolean overTotal = totalBudget != null && totalBudget > 0 && totalBefore > totalBudget;
        List<String> reasons = new ArrayList<>();
        if (overSection) {
            reasons.add("OVER_SECTION_BUDGET");
        }
        if (overTotal) {
            reasons.add("OVER_TOTAL_BUDGET");
        }
        report.setReasons(reasons.isEmpty() ? null : reasons);

        if (!overSection && !overTotal) {
            report.setTotalAfterTokens(totalBefore);
            report.setSectionTokensAfter(beforeTokens);
            result.setReport(report);
            return result;
        }

        Map<ContextSection, ContextTrimStats> removedBySection = new EnumMap<>(ContextSection.class);
        List<ContextTrimSection> trimOrder = resolveTrimOrder();
        Map<ContextSection, Integer> currentTokens = new EnumMap<>(beforeTokens);
        int totalTokens = totalBefore;
        // 按固定顺序裁剪，先处理分区超限，再处理总预算超限。
        if (overSection) {
            for (ContextTrimSection section : trimOrder) {
                trimBySectionBudget(section, snapshot, budgets, currentTokens, removedBySection);
                currentTokens = estimateSectionTokens(snapshot);
                totalTokens = sumTokens(currentTokens);
            }
        }

        if (overTotal && totalBudget != null && totalTokens > totalBudget) {
            for (ContextTrimSection section : trimOrder) {
                if (totalTokens <= totalBudget) {
                    break;
                }
                int excess = totalTokens - totalBudget;
                trimByTotal(section, snapshot, currentTokens, excess, removedBySection);
                currentTokens = estimateSectionTokens(snapshot);
                totalTokens = sumTokens(currentTokens);
            }
        }

        report.setSectionTokensAfter(currentTokens);
        report.setTotalAfterTokens(totalTokens);
        report.setRemovedItemsBySection(removedBySection.isEmpty() ? null : removedBySection);
        result.setReport(report);

        recordMetrics(totalBefore, totalTokens, removedBySection, reasons);
        log.info("上下文裁剪完成，租户={}, 工作流={}, 快照={}, 裁剪前令牌={}, 裁剪后令牌={}, "
                        + "移除统计={}, 原因={}, 策略版本={}, 令牌估算=true",
                snapshot.getRuntimeMeta() != null ? snapshot.getRuntimeMeta().getTenantId() : null,
                snapshot.getRuntimeMeta() != null ? snapshot.getRuntimeMeta().getWorkflowId() : null,
                snapshot.getSnapshotId(),
                totalBefore,
                totalTokens,
                removedBySection,
                reasons,
                request.getPolicy() != null ? request.getPolicy().getVersion() : allocation.getVersion());
        return result;
    }

    private List<ContextTrimSection> resolveTrimOrder() {
        if (properties == null) {
            return ContextTrimSection.defaultOrder();
        }
        return properties.resolveTrimOrder();
    }

    private void trimBySectionBudget(ContextTrimSection trimSection,
                                     ContextSnapshot snapshot,
                                     Map<ContextSection, Integer> budgets,
                                     Map<ContextSection, Integer> currentTokens,
                                     Map<ContextSection, ContextTrimStats> removedBySection) {
        switch (trimSection) {
            case EVIDENCE_PACK -> {
                Integer budget = resolveBudget(budgets, ContextSection.EVIDENCE_PACK);
                int current = currentTokens.getOrDefault(ContextSection.EVIDENCE_PACK, 0);
                if (budget != null && current > budget) {
                    trimEvidencePack(snapshot.getWorkingMemory(), budget, removedBySection);
                }
            }
            case RECALLED_MEMORIES -> {
                Integer longBudget = resolveBudget(budgets, ContextSection.LONG_TERM_MEMORY);
                int longTokens = currentTokens.getOrDefault(ContextSection.LONG_TERM_MEMORY, 0);
                if (longBudget != null && longTokens > longBudget) {
                    trimLongTermMemory(snapshot.getLongTermMemory(), longBudget, removedBySection);
                }
                Integer domainBudget = resolveBudget(budgets, ContextSection.DOMAIN_KNOWLEDGE);
                int domainTokens = currentTokens.getOrDefault(ContextSection.DOMAIN_KNOWLEDGE, 0);
                if (domainBudget != null && domainTokens > domainBudget) {
                    trimDomainKnowledge(snapshot.getDomainKnowledge(), domainBudget, removedBySection);
                }
            }
            case WORKING_MEMORY -> {
                Integer budget = resolveBudget(budgets, ContextSection.WORKING_MEMORY);
                int current = currentTokens.getOrDefault(ContextSection.WORKING_MEMORY, 0);
                if (budget != null && current > budget) {
                    trimWorkingMemory(snapshot.getWorkingMemory(), budget, removedBySection);
                }
            }
            case TOOL_SUMMARY -> {
                Integer budget = resolveBudget(budgets, ContextSection.TOOL_SUMMARY);
                int current = currentTokens.getOrDefault(ContextSection.TOOL_SUMMARY, 0);
                if (budget != null && current > budget) {
                    trimToolSummaries(snapshot.getToolState(), budget, removedBySection);
                }
            }
            case TASK_AND_SYSTEM -> {
                Integer systemBudget = resolveBudget(budgets, ContextSection.SYSTEM_POLICY);
                int systemTokens = currentTokens.getOrDefault(ContextSection.SYSTEM_POLICY, 0);
                if (systemBudget != null && systemTokens > systemBudget) {
                    trimRoleBoundarySystem(snapshot.getRoleBoundary(), systemBudget, removedBySection);
                }
                Integer developerBudget = resolveBudget(budgets, ContextSection.DEVELOPER_POLICY);
                int developerTokens = currentTokens.getOrDefault(ContextSection.DEVELOPER_POLICY, 0);
                if (developerBudget != null && developerTokens > developerBudget) {
                    trimRoleBoundaryDeveloper(snapshot.getRoleBoundary(), developerBudget, removedBySection);
                }
                Integer taskBudget = resolveBudget(budgets, ContextSection.USER_INPUT);
                int taskTokens = currentTokens.getOrDefault(ContextSection.USER_INPUT, 0);
                if (taskBudget != null && taskTokens > taskBudget) {
                    trimTaskIntent(snapshot.getTaskIntent(), taskBudget, removedBySection);
                }
            }
            default -> {
            }
        }
    }
    private void trimByTotal(ContextTrimSection trimSection,
                             ContextSnapshot snapshot,
                             Map<ContextSection, Integer> currentTokens,
                             int excess,
                             Map<ContextSection, ContextTrimStats> removedBySection) {
        if (excess <= 0) {
            return;
        }
        switch (trimSection) {
            case EVIDENCE_PACK -> {
                int current = currentTokens.getOrDefault(ContextSection.EVIDENCE_PACK, 0);
                if (current > 0 && excess > 0) {
                    int target = Math.max(0, current - excess);
                    int after = trimEvidencePack(snapshot.getWorkingMemory(), target, removedBySection);
                    excess -= Math.max(0, current - after);
                }
            }
            case RECALLED_MEMORIES -> {
                int longTokens = currentTokens.getOrDefault(ContextSection.LONG_TERM_MEMORY, 0);
                if (longTokens > 0 && excess > 0) {
                    int target = Math.max(0, longTokens - excess);
                    int after = trimLongTermMemory(snapshot.getLongTermMemory(), target, removedBySection);
                    excess -= Math.max(0, longTokens - after);
                }
                int domainTokens = currentTokens.getOrDefault(ContextSection.DOMAIN_KNOWLEDGE, 0);
                if (domainTokens > 0 && excess > 0) {
                    int target = Math.max(0, domainTokens - excess);
                    int after = trimDomainKnowledge(snapshot.getDomainKnowledge(), target, removedBySection);
                    excess -= Math.max(0, domainTokens - after);
                }
            }
            case WORKING_MEMORY -> {
                int current = currentTokens.getOrDefault(ContextSection.WORKING_MEMORY, 0);
                if (current > 0 && excess > 0) {
                    int target = Math.max(0, current - excess);
                    int after = trimWorkingMemory(snapshot.getWorkingMemory(), target, removedBySection);
                    excess -= Math.max(0, current - after);
                }
            }
            case TOOL_SUMMARY -> {
                int current = currentTokens.getOrDefault(ContextSection.TOOL_SUMMARY, 0);
                if (current > 0 && excess > 0) {
                    int target = Math.max(0, current - excess);
                    int after = trimToolSummaries(snapshot.getToolState(), target, removedBySection);
                    excess -= Math.max(0, current - after);
                }
            }
            case TASK_AND_SYSTEM -> {
                int systemTokens = currentTokens.getOrDefault(ContextSection.SYSTEM_POLICY, 0);
                if (systemTokens > 0 && excess > 0) {
                    int target = Math.max(0, systemTokens - excess);
                    int after = trimRoleBoundarySystem(snapshot.getRoleBoundary(), target, removedBySection);
                    excess -= Math.max(0, systemTokens - after);
                }
                int developerTokens = currentTokens.getOrDefault(ContextSection.DEVELOPER_POLICY, 0);
                if (developerTokens > 0 && excess > 0) {
                    int target = Math.max(0, developerTokens - excess);
                    int after = trimRoleBoundaryDeveloper(snapshot.getRoleBoundary(), target, removedBySection);
                    excess -= Math.max(0, developerTokens - after);
                }
                int taskTokens = currentTokens.getOrDefault(ContextSection.USER_INPUT, 0);
                if (taskTokens > 0 && excess > 0) {
                    int target = Math.max(0, taskTokens - excess);
                    trimTaskIntent(snapshot.getTaskIntent(), target, removedBySection);
                }
            }
            default -> {
            }
        }
    }

    private Integer resolveBudget(Map<ContextSection, Integer> budgets, ContextSection section) {
        if (budgets == null || section == null) {
            return null;
        }
        Integer value = budgets.get(section);
        if (value == null) {
            return null;
        }
        return Math.max(0, value);
    }
    private int trimEvidencePack(WorkingMemory memory,
                                 int targetTokens,
                                 Map<ContextSection, ContextTrimStats> removedBySection) {
        if (memory == null || memory.getEvidencePack() == null) {
            return 0;
        }
        EvidencePack pack = memory.getEvidencePack();
        int currentTokens = estimateEvidencePackTokens(pack);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<EvidenceItem> evidences = mutableCopy(pack.getEvidences());
        if (evidences == null || evidences.isEmpty()) {
            return currentTokens;
        }
        for (EvidenceItem item : evidences) {
            if (item == null) {
                continue;
            }
            int maxDigestChars = item.getType() == EvidenceType.TOOL_RESULT
                    ? MAX_RESULT_DIGEST_CHARS
                    : MAX_ARGS_DIGEST_CHARS;
            String digest = item.getDigest();
            String trimmedDigest = trimText(digest, maxDigestChars);
            if (digest != null && trimmedDigest != null && digest.length() > trimmedDigest.length()) {
                int removedChars = digest.length() - trimmedDigest.length();
                recordRemoved(removedBySection, ContextSection.EVIDENCE_PACK, 1,
                        removedChars, estimateTokensByChars(removedChars));
                item.setDigest(trimmedDigest);
            }
            String source = item.getSource();
            String trimmedSource = trimText(source, MAX_TOOL_DESC_CHARS);
            if (source != null && trimmedSource != null && source.length() > trimmedSource.length()) {
                int removedChars = source.length() - trimmedSource.length();
                recordRemoved(removedBySection, ContextSection.EVIDENCE_PACK, 1,
                        removedChars, estimateTokensByChars(removedChars));
                item.setSource(trimmedSource);
            }
        }
        pack.setEvidences(evidences);
        currentTokens = estimateEvidencePackTokens(pack);
        if (currentTokens <= targetTokens) {
            pack.recomputeStats();
            return currentTokens;
        }
        while (!evidences.isEmpty() && currentTokens > targetTokens) {
            EvidenceItem removed = evidences.remove(0);
            int removedChars = estimateEvidenceItemChars(removed);
            int removedTokens = estimateEvidenceItemTokens(removed);
            recordRemoved(removedBySection, ContextSection.EVIDENCE_PACK, 1, removedChars, removedTokens);
            pack.setEvidences(evidences.isEmpty() ? null : evidences);
            currentTokens = estimateEvidencePackTokens(pack);
        }
        pack.setEvidences(evidences.isEmpty() ? null : evidences);
        pack.recomputeStats();
        return estimateEvidencePackTokens(pack);
    }

    private int trimLongTermMemory(LongTermMemory memory,
                                   int targetTokens,
                                   Map<ContextSection, ContextTrimStats> removedBySection) {
        if (memory == null || memory.getMemoryRefs() == null) {
            return 0;
        }
        List<MemoryRef> refs = mutableCopy(memory.getMemoryRefs());
        int currentTokens = estimateLongTermMemoryTokens(refs);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<MemoryRef> ordered = new ArrayList<>(refs);
        ordered.sort(Comparator
                .comparing((MemoryRef ref) -> ref != null && ref.getScore() != null ? ref.getScore() : 0.0)
                .thenComparing(ref -> ref != null && ref.getExpiresAt() != null ? ref.getExpiresAt() : Instant.MAX)
                .thenComparingInt(ref -> ref != null ? safeLength(ref.getSnippet()) : 0));
        for (MemoryRef ref : ordered) {
            if (currentTokens <= targetTokens) {
                break;
            }
            if (!refs.remove(ref)) {
                continue;
            }
            int removedChars = estimateMemoryRefChars(ref);
            int removedTokens = estimateMemoryRefTokens(ref);
            recordRemoved(removedBySection, ContextSection.LONG_TERM_MEMORY, 1, removedChars, removedTokens);
            currentTokens = estimateLongTermMemoryTokens(refs);
        }
        memory.setMemoryRefs(refs.isEmpty() ? null : refs);
        return estimateLongTermMemoryTokens(refs);
    }

    private int trimDomainKnowledge(DomainKnowledge knowledge,
                                    int targetTokens,
                                    Map<ContextSection, ContextTrimStats> removedBySection) {
        if (knowledge == null || knowledge.getCitations() == null) {
            return 0;
        }
        List<Citation> citations = mutableCopy(knowledge.getCitations());
        int currentTokens = estimateDomainKnowledgeTokens(citations);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<Citation> ordered = new ArrayList<>(citations);
        ordered.sort(Comparator
                .comparing((Citation citation) -> citation != null && citation.getFetchedAt() != null
                        ? citation.getFetchedAt() : Instant.MAX)
                .thenComparingInt(citation -> citation != null ? safeLength(citation.getSnippet()) : 0)
                .thenComparingInt(citation -> citation != null ? safeLength(citation.getTitle()) : 0));
        for (Citation citation : ordered) {
            if (currentTokens <= targetTokens) {
                break;
            }
            if (!citations.remove(citation)) {
                continue;
            }
            int removedChars = estimateCitationChars(citation);
            int removedTokens = estimateCitationTokens(citation);
            recordRemoved(removedBySection, ContextSection.DOMAIN_KNOWLEDGE, 1, removedChars, removedTokens);
            currentTokens = estimateDomainKnowledgeTokens(citations);
        }
        knowledge.setCitations(citations.isEmpty() ? null : citations);
        return estimateDomainKnowledgeTokens(citations);
    }
    private int trimWorkingMemory(WorkingMemory memory,
                                  int targetTokens,
                                  Map<ContextSection, ContextTrimStats> removedBySection) {
        if (memory == null) {
            return 0;
        }
        int currentTokens = estimateWorkingMemoryTokens(memory);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<String> keyFacts = mutableCopy(memory.getKeyFacts());
        if (keyFacts != null) {
            while (!keyFacts.isEmpty() && currentTokens > targetTokens) {
                String removed = keyFacts.remove(0);
                recordRemoved(removedBySection, ContextSection.WORKING_MEMORY, 1,
                        safeLength(removed), estimateTokens(removed));
                currentTokens = estimateWorkingMemoryTokens(memoryWith(memory, keyFacts, memory.getPlanSteps(),
                        memory.getNextStep(), memory.getRecentToolCalls()));
            }
            memory.setKeyFacts(keyFacts.isEmpty() ? null : keyFacts);
        }
        List<String> planSteps = mutableCopy(memory.getPlanSteps());
        if (planSteps != null && currentTokens > targetTokens) {
            while (!planSteps.isEmpty() && currentTokens > targetTokens) {
                String removed = planSteps.remove(0);
                recordRemoved(removedBySection, ContextSection.WORKING_MEMORY, 1,
                        safeLength(removed), estimateTokens(removed));
                currentTokens = estimateWorkingMemoryTokens(memoryWith(memory, memory.getKeyFacts(), planSteps,
                        memory.getNextStep(), memory.getRecentToolCalls()));
            }
            memory.setPlanSteps(planSteps.isEmpty() ? null : planSteps);
        }
        List<ToolCallState> recentToolCalls = mutableCopy(memory.getRecentToolCalls());
        if (recentToolCalls != null && currentTokens > targetTokens) {
            while (!recentToolCalls.isEmpty() && currentTokens > targetTokens) {
                ToolCallState removed = recentToolCalls.remove(0);
                int removedChars = estimateToolCallStateChars(removed);
                int removedTokens = estimateToolCallStateTokens(removed);
                recordRemoved(removedBySection, ContextSection.WORKING_MEMORY, 1, removedChars, removedTokens);
                currentTokens = estimateWorkingMemoryTokens(memoryWith(memory, memory.getKeyFacts(),
                        memory.getPlanSteps(), memory.getNextStep(), recentToolCalls));
            }
            memory.setRecentToolCalls(recentToolCalls.isEmpty() ? null : recentToolCalls);
        }
        if (currentTokens > targetTokens) {
            String summary = memory.getSummary();
            if (summary != null) {
                int summaryTokens = estimateTokens(summary);
                int targetSummaryTokens = Math.max(0, summaryTokens - (currentTokens - targetTokens));
                String trimmed = trimTextByTokens(summary, targetSummaryTokens);
                if (trimmed != null && trimmed.length() < summary.length()) {
                    recordRemoved(removedBySection, ContextSection.WORKING_MEMORY, 1,
                            summary.length() - trimmed.length(),
                            estimateTokensByChars(summary.length() - trimmed.length()));
                    memory.setSummary(trimmed.isBlank() ? null : trimmed);
                    currentTokens = estimateWorkingMemoryTokens(memory);
                }
            }
        }
        if (currentTokens > targetTokens) {
            String nextStep = memory.getNextStep();
            if (nextStep != null) {
                int nextTokens = estimateTokens(nextStep);
                int targetNextTokens = Math.max(0, nextTokens - (currentTokens - targetTokens));
                String trimmed = trimTextByTokens(nextStep, targetNextTokens);
                if (trimmed != null && trimmed.length() < nextStep.length()) {
                    recordRemoved(removedBySection, ContextSection.WORKING_MEMORY, 1,
                            nextStep.length() - trimmed.length(),
                            estimateTokensByChars(nextStep.length() - trimmed.length()));
                    memory.setNextStep(trimmed.isBlank() ? null : trimmed);
                    currentTokens = estimateWorkingMemoryTokens(memory);
                }
            }
        }
        memory.setSummaryChars(memory.getSummary() != null ? memory.getSummary().length() : 0);
        memory.setWorkingMemoryItems(memory.getKeyFacts() != null ? memory.getKeyFacts().size() : 0);
        return estimateWorkingMemoryTokens(memory);
    }

    private WorkingMemory memoryWith(WorkingMemory memory,
                                     List<String> keyFacts,
                                     List<String> planSteps,
                                     String nextStep,
                                     List<ToolCallState> toolCalls) {
        WorkingMemory temp = new WorkingMemory();
        temp.setSummary(memory.getSummary());
        temp.setKeyFacts(keyFacts);
        temp.setPlanSteps(planSteps);
        temp.setNextStep(nextStep);
        temp.setRecentToolCalls(toolCalls);
        return temp;
    }

    private int trimToolSummaries(ToolState toolState,
                                  int targetTokens,
                                  Map<ContextSection, ContextTrimStats> removedBySection) {
        if (toolState == null || toolState.getAvailableTools() == null) {
            return 0;
        }
        List<ToolSummary> tools = mutableCopy(toolState.getAvailableTools());
        int currentTokens = estimateToolSummariesTokens(tools);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        for (ToolSummary tool : tools) {
            if (tool == null || tool.getDescription() == null) {
                continue;
            }
            String desc = tool.getDescription();
            String trimmed = trimText(desc, MAX_TOOL_DESC_CHARS);
            if (trimmed != null && trimmed.length() < desc.length()) {
                recordRemoved(removedBySection, ContextSection.TOOL_SUMMARY, 1,
                        desc.length() - trimmed.length(),
                        estimateTokensByChars(desc.length() - trimmed.length()));
                tool.setDescription(trimmed);
            }
        }
        currentTokens = estimateToolSummariesTokens(tools);
        if (currentTokens > targetTokens) {
            Set<String> selected = new HashSet<>();
            if (toolState.getSelectedTools() != null) {
                selected.addAll(toolState.getSelectedTools());
            }
            List<ToolSummary> remaining = new ArrayList<>(tools);
            for (int i = remaining.size() - 1; i >= 0 && currentTokens > targetTokens; i--) {
                ToolSummary tool = remaining.get(i);
                String name = tool != null ? tool.getToolName() : null;
                if (name != null && selected.contains(name)) {
                    continue;
                }
                remaining.remove(i);
                int removedChars = estimateToolSummaryChars(tool);
                int removedTokens = estimateToolSummaryTokens(tool);
                recordRemoved(removedBySection, ContextSection.TOOL_SUMMARY, 1, removedChars, removedTokens);
                currentTokens = estimateToolSummariesTokens(remaining);
            }
            for (int i = remaining.size() - 1; i >= 0 && currentTokens > targetTokens; i--) {
                ToolSummary tool = remaining.get(i);
                remaining.remove(i);
                int removedChars = estimateToolSummaryChars(tool);
                int removedTokens = estimateToolSummaryTokens(tool);
                recordRemoved(removedBySection, ContextSection.TOOL_SUMMARY, 1, removedChars, removedTokens);
                currentTokens = estimateToolSummariesTokens(remaining);
            }
            tools = remaining;
        }
        toolState.setAvailableTools(tools.isEmpty() ? null : tools);
        return estimateToolSummariesTokens(tools);
    }
    private int trimTaskIntent(TaskIntent intent,
                               int targetTokens,
                               Map<ContextSection, ContextTrimStats> removedBySection) {
        if (intent == null) {
            return 0;
        }
        int currentTokens = estimateTaskIntentTokens(intent);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<String> constraints = mutableCopy(intent.getConstraints());
        if (constraints != null) {
            while (!constraints.isEmpty() && currentTokens > targetTokens) {
                String removed = constraints.remove(constraints.size() - 1);
                recordRemoved(removedBySection, ContextSection.USER_INPUT, 1,
                        safeLength(removed), estimateTokens(removed));
                currentTokens = estimateTaskIntentTokens(intentWith(intent, constraints));
            }
            intent.setConstraints(constraints.isEmpty() ? null : constraints);
        }
        currentTokens = trimTextField(intent.getRequiredOutput(), targetTokens, currentTokens,
                value -> intent.setRequiredOutput(value), ContextSection.USER_INPUT, removedBySection);
        currentTokens = trimTextField(intent.getFailurePolicy(), targetTokens, currentTokens,
                value -> intent.setFailurePolicy(value), ContextSection.USER_INPUT, removedBySection);
        currentTokens = trimTextField(intent.getSuccessCriteria(), targetTokens, currentTokens,
                value -> intent.setSuccessCriteria(value), ContextSection.USER_INPUT, removedBySection);
        currentTokens = trimTextField(intent.getInputText(), targetTokens, currentTokens,
                value -> intent.setInputText(value), ContextSection.USER_INPUT, removedBySection);
        return estimateTaskIntentTokens(intent);
    }

    private TaskIntent intentWith(TaskIntent intent, List<String> constraints) {
        TaskIntent temp = new TaskIntent();
        temp.setInputText(intent.getInputText());
        temp.setSuccessCriteria(intent.getSuccessCriteria());
        temp.setFailurePolicy(intent.getFailurePolicy());
        temp.setRequiredOutput(intent.getRequiredOutput());
        temp.setConstraints(constraints);
        return temp;
    }

    private int trimRoleBoundarySystem(RoleBoundary boundary,
                                       int targetTokens,
                                       Map<ContextSection, ContextTrimStats> removedBySection) {
        if (boundary == null) {
            return 0;
        }
        int currentTokens = estimateRoleBoundarySystemTokens(boundary);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<String> forbiddenActions = mutableCopy(boundary.getForbiddenActions());
        if (forbiddenActions != null) {
            while (!forbiddenActions.isEmpty() && currentTokens > targetTokens) {
                String removed = forbiddenActions.remove(forbiddenActions.size() - 1);
                recordRemoved(removedBySection, ContextSection.SYSTEM_POLICY, 1,
                        safeLength(removed), estimateTokens(removed));
                boundary.setForbiddenActions(forbiddenActions.isEmpty() ? null : forbiddenActions);
                currentTokens = estimateRoleBoundarySystemTokens(boundary);
            }
        }
        List<String> dataScopes = mutableCopy(boundary.getDataScopes());
        if (dataScopes != null && currentTokens > targetTokens) {
            while (!dataScopes.isEmpty() && currentTokens > targetTokens) {
                String removed = dataScopes.remove(dataScopes.size() - 1);
                recordRemoved(removedBySection, ContextSection.SYSTEM_POLICY, 1,
                        safeLength(removed), estimateTokens(removed));
                boundary.setDataScopes(dataScopes.isEmpty() ? null : dataScopes);
                currentTokens = estimateRoleBoundarySystemTokens(boundary);
            }
        }
        currentTokens = trimTextField(boundary.getRiskLevel(), targetTokens, currentTokens,
                value -> boundary.setRiskLevel(value), ContextSection.SYSTEM_POLICY, removedBySection);
        currentTokens = trimTextField(boundary.getSystemPolicyId(), targetTokens, currentTokens,
                value -> boundary.setSystemPolicyId(value), ContextSection.SYSTEM_POLICY, removedBySection);
        return estimateRoleBoundarySystemTokens(boundary);
    }

    private int trimRoleBoundaryDeveloper(RoleBoundary boundary,
                                          int targetTokens,
                                          Map<ContextSection, ContextTrimStats> removedBySection) {
        if (boundary == null) {
            return 0;
        }
        int currentTokens = estimateRoleBoundaryDeveloperTokens(boundary);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        currentTokens = trimTextField(boundary.getDeveloperPolicyId(), targetTokens, currentTokens,
                value -> boundary.setDeveloperPolicyId(value), ContextSection.DEVELOPER_POLICY, removedBySection);
        return estimateRoleBoundaryDeveloperTokens(boundary);
    }

    private int trimTextField(String value,
                              int targetTokens,
                              int currentTokens,
                              java.util.function.Consumer<String> setter,
                              ContextSection section,
                              Map<ContextSection, ContextTrimStats> removedBySection) {
        if (value == null || currentTokens <= targetTokens) {
            return currentTokens;
        }
        int textTokens = estimateTokens(value);
        int targetTextTokens = Math.max(0, textTokens - (currentTokens - targetTokens));
        String trimmed = trimTextByTokens(value, targetTextTokens);
        if (trimmed != null && trimmed.length() < value.length()) {
            recordRemoved(removedBySection, section, 1,
                    value.length() - trimmed.length(),
                    estimateTokensByChars(value.length() - trimmed.length()));
            setter.accept(trimmed.isBlank() ? null : trimmed);
            currentTokens = currentTokens - textTokens + estimateTokens(trimmed);
        }
        return currentTokens;
    }
    private void recordMetrics(int totalBefore,
                               int totalAfter,
                               Map<ContextSection, ContextTrimStats> removedBySection,
                               List<String> reasons) {
        metricsPublisher.increment("context_trim_runs_total");
        metricsPublisher.recordSummary("context_trim_before_tokens", totalBefore);
        metricsPublisher.recordSummary("context_trim_after_tokens", totalAfter);
        for (Map.Entry<ContextSection, ContextTrimStats> entry : removedBySection.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int removedCount = entry.getValue().getRemovedCount();
            if (removedCount <= 0) {
                continue;
            }
            String section = entry.getKey().name().toLowerCase(Locale.ROOT);
            metricsPublisher.incrementWithTags("context_trim_removed_items_total", removedCount, "section", section);
        }
        if (reasons != null) {
            for (String reason : reasons) {
                if (reason == null || reason.isBlank()) {
                    continue;
                }
                metricsPublisher.incrementWithTags("context_trim_over_budget_total", "reason", reason);
            }
        }
    }

    private boolean isOverSectionBudget(Map<ContextSection, Integer> beforeTokens,
                                        Map<ContextSection, Integer> budgets) {
        if (beforeTokens == null || budgets == null) {
            return false;
        }
        for (Map.Entry<ContextSection, Integer> entry : beforeTokens.entrySet()) {
            ContextSection section = entry.getKey();
            Integer value = entry.getValue();
            Integer budget = budgets.get(section);
            if (budget == null) {
                continue;
            }
            if (value != null && value > budget) {
                return true;
            }
        }
        return false;
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
        tokens.put(ContextSection.EVIDENCE_PACK, estimateEvidencePackTokens(snapshot.getWorkingMemory() != null
                ? snapshot.getWorkingMemory().getEvidencePack() : null));
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
        if (knowledge == null) {
            return 0;
        }
        return estimateDomainKnowledgeTokens(knowledge.getCitations());
    }

    private int estimateDomainKnowledgeTokens(List<Citation> citations) {
        if (citations == null) {
            return 0;
        }
        int total = 0;
        for (Citation citation : citations) {
            total += estimateCitationTokens(citation);
        }
        return total;
    }

    private int estimateLongTermMemoryTokens(LongTermMemory memory) {
        if (memory == null) {
            return 0;
        }
        return estimateLongTermMemoryTokens(memory.getMemoryRefs());
    }

    private int estimateLongTermMemoryTokens(List<MemoryRef> refs) {
        if (refs == null) {
            return 0;
        }
        int total = 0;
        for (MemoryRef ref : refs) {
            total += estimateMemoryRefTokens(ref);
        }
        return total;
    }

    private int estimateEvidencePackTokens(EvidencePack pack) {
        if (pack == null) {
            return 0;
        }
        int total = 0;
        if (pack.getEvidences() != null) {
            for (EvidenceItem item : pack.getEvidences()) {
                total += estimateEvidenceItemTokens(item);
            }
        }
        return total;
    }
    private int estimateToolSummariesTokens(ToolState toolState) {
        if (toolState == null) {
            return 0;
        }
        return estimateToolSummariesTokens(toolState.getAvailableTools());
    }

    private int estimateToolSummariesTokens(List<ToolSummary> tools) {
        if (tools == null) {
            return 0;
        }
        int total = 0;
        for (ToolSummary tool : tools) {
            total += estimateToolSummaryTokens(tool);
        }
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

    private int estimateTokensByChars(int chars) {
        if (chars <= 0) {
            return 0;
        }
        int estimate = (int) Math.ceil(chars / 4.0);
        return Math.max(1, estimate);
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

    private int estimateMemoryRefChars(MemoryRef ref) {
        if (ref == null) {
            return 0;
        }
        int total = 0;
        total += safeLength(ref.getMemoryId());
        total += safeLength(ref.getMemoryType());
        total += safeLength(ref.getSnippet());
        total += safeLength(ref.getSource());
        return total;
    }

    private int estimateCitationTokens(Citation citation) {
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

    private int estimateCitationChars(Citation citation) {
        if (citation == null) {
            return 0;
        }
        int total = 0;
        total += safeLength(citation.getType());
        total += safeLength(citation.getRefId());
        total += safeLength(citation.getLabel());
        total += safeLength(citation.getSource());
        total += safeLength(citation.getTitle());
        total += safeLength(citation.getUri());
        total += safeLength(citation.getSnippet());
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

    private int estimateToolSummaryChars(ToolSummary tool) {
        if (tool == null) {
            return 0;
        }
        int total = 0;
        total += safeLength(tool.getToolName());
        total += safeLength(tool.getDescription());
        if (tool.getTags() != null) {
            for (String tag : tool.getTags()) {
                total += safeLength(tag);
            }
        }
        total += safeLength(tool.getCostLevel());
        total += safeLength(tool.getLatencyLevel());
        total += safeLength(tool.getAuthScope());
        return total;
    }

    private int estimateEvidenceItemTokens(EvidenceItem item) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        total += estimateTokens(item.getType() != null ? item.getType().name() : null);
        total += estimateTokens(item.getEvidenceId());
        total += estimateTokens(item.getStepId());
        total += estimateTokens(item.getSource());
        total += estimateTokens(item.getRef());
        total += estimateTokens(item.getDigest());
        return total;
    }

    private int estimateEvidenceItemChars(EvidenceItem item) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        total += safeLength(item.getType() != null ? item.getType().name() : null);
        total += safeLength(item.getEvidenceId());
        total += safeLength(item.getStepId());
        total += safeLength(item.getSource());
        total += safeLength(item.getRef());
        total += safeLength(item.getDigest());
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

    private int estimateToolCallStateChars(ToolCallState call) {
        if (call == null) {
            return 0;
        }
        int total = 0;
        total += safeLength(call.getToolName());
        total += safeLength(call.getRequestId());
        total += safeLength(call.getErrorCode());
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

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String trimText(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        if (maxChars <= 0) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private String trimTextByTokens(String text, int targetTokens) {
        if (text == null) {
            return null;
        }
        if (targetTokens <= 0) {
            return "";
        }
        int maxChars = targetTokens * 4;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private <T> List<T> mutableCopy(List<T> list) {
        if (list == null) {
            return null;
        }
        return new ArrayList<>(list);
    }

    private void recordRemoved(Map<ContextSection, ContextTrimStats> removedBySection,
                               ContextSection section,
                               int count,
                               int chars,
                               int tokens) {
        if (count <= 0 && chars <= 0 && tokens <= 0) {
            return;
        }
        ContextTrimStats stats = removedBySection.computeIfAbsent(section, key -> new ContextTrimStats());
        stats.addRemoved(count, chars, tokens);
    }
}

