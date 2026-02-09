package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.core.ContextTrimSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.capabilities.context.model.TaskIntent;
import com.example.agent.capabilities.context.model.ToolCallState;
import com.example.agent.capabilities.context.model.WorkingMemory;
import java.util.List;
import java.util.function.Consumer;

/**
 * 工作记忆分组裁剪处理器。
 */
public class WorkingMemoryTrimHandler extends AbstractContextTrimHandler {

    @Override
    public ContextTrimSection section() {
        return ContextTrimSection.WORKING_MEMORY;
    }

    @Override
    public void trimBySectionBudget(TrimContext context) {
        Integer budget = resolveBudget(context.getBudgets(), ContextSection.WORKING_MEMORY);
        int current = context.getCurrentTokens().getOrDefault(ContextSection.WORKING_MEMORY, 0);
        if (budget != null && current > budget) {
            trimWorkingMemory(context, budget);
            context.refreshTokens();
        }
    }

    @Override
    public int trimByTotalBudget(TrimContext context, int excess) {
        if (excess <= 0) {
            return 0;
        }
        int current = context.getCurrentTokens().getOrDefault(ContextSection.WORKING_MEMORY, 0);
        if (current <= 0) {
            return excess;
        }
        int target = Math.max(0, current - excess);
        int after = trimWorkingMemory(context, target);
        context.refreshTokens();
        return Math.max(0, excess - Math.max(0, current - after));
    }

    private int trimWorkingMemory(TrimContext context,
                                  int targetTokens) {
        WorkingMemory memory = context.getSnapshot().getWorkingMemory();
        if (memory == null) {
            return 0;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        int currentTokens = estimator.estimateWorkingMemoryTokens(memory);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<String> keyFacts = mutableCopy(memory.getKeyFacts());
        if (keyFacts != null) {
            while (!keyFacts.isEmpty() && currentTokens > targetTokens) {
                String removed = keyFacts.remove(0);
                recordRemoved(context, ContextSection.WORKING_MEMORY, 1,
                        estimator.safeLength(removed), estimator.estimateTokens(removed));
                currentTokens = estimator.estimateWorkingMemoryTokens(memoryWith(memory, keyFacts, memory.getPlanSteps(),
                        memory.getNextStep(), memory.getRecentToolCalls()));
            }
            memory.setKeyFacts(keyFacts.isEmpty() ? null : keyFacts);
        }
        List<String> planSteps = mutableCopy(memory.getPlanSteps());
        if (planSteps != null && currentTokens > targetTokens) {
            while (!planSteps.isEmpty() && currentTokens > targetTokens) {
                String removed = planSteps.remove(0);
                recordRemoved(context, ContextSection.WORKING_MEMORY, 1,
                        estimator.safeLength(removed), estimator.estimateTokens(removed));
                currentTokens = estimator.estimateWorkingMemoryTokens(memoryWith(memory, memory.getKeyFacts(), planSteps,
                        memory.getNextStep(), memory.getRecentToolCalls()));
            }
            memory.setPlanSteps(planSteps.isEmpty() ? null : planSteps);
        }
        List<ToolCallState> recentToolCalls = mutableCopy(memory.getRecentToolCalls());
        if (recentToolCalls != null && currentTokens > targetTokens) {
            while (!recentToolCalls.isEmpty() && currentTokens > targetTokens) {
                ToolCallState removed = recentToolCalls.remove(0);
                int removedChars = estimator.estimateToolCallStateChars(removed);
                int removedTokens = estimator.estimateToolCallStateTokens(removed);
                recordRemoved(context, ContextSection.WORKING_MEMORY, 1, removedChars, removedTokens);
                currentTokens = estimator.estimateWorkingMemoryTokens(memoryWith(memory, memory.getKeyFacts(),
                        memory.getPlanSteps(), memory.getNextStep(), recentToolCalls));
            }
            memory.setRecentToolCalls(recentToolCalls.isEmpty() ? null : recentToolCalls);
        }
        if (currentTokens > targetTokens) {
            String summary = memory.getSummary();
            if (summary != null) {
                int summaryTokens = estimator.estimateTokens(summary);
                int targetSummaryTokens = Math.max(0, summaryTokens - (currentTokens - targetTokens));
                String trimmed = trimTextByTokens(summary, targetSummaryTokens);
                if (trimmed != null && trimmed.length() < summary.length()) {
                    recordRemoved(context, ContextSection.WORKING_MEMORY, 1,
                            summary.length() - trimmed.length(),
                            estimator.estimateTokensByChars(summary.length() - trimmed.length()));
                    memory.setSummary(trimmed.isBlank() ? null : trimmed);
                    currentTokens = estimator.estimateWorkingMemoryTokens(memory);
                }
            }
        }
        if (currentTokens > targetTokens) {
            String nextStep = memory.getNextStep();
            if (nextStep != null) {
                int nextTokens = estimator.estimateTokens(nextStep);
                int targetNextTokens = Math.max(0, nextTokens - (currentTokens - targetTokens));
                String trimmed = trimTextByTokens(nextStep, targetNextTokens);
                if (trimmed != null && trimmed.length() < nextStep.length()) {
                    recordRemoved(context, ContextSection.WORKING_MEMORY, 1,
                            nextStep.length() - trimmed.length(),
                            estimator.estimateTokensByChars(nextStep.length() - trimmed.length()));
                    memory.setNextStep(trimmed.isBlank() ? null : trimmed);
                    currentTokens = estimator.estimateWorkingMemoryTokens(memory);
                }
            }
        }
        memory.setSummaryChars(memory.getSummary() != null ? memory.getSummary().length() : 0);
        memory.setWorkingMemoryItems(memory.getKeyFacts() != null ? memory.getKeyFacts().size() : 0);
        return estimator.estimateWorkingMemoryTokens(memory);
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

    /**
     * 按目标令牌裁剪文本字段。
     */
    public int trimTextField(TrimContext context,
                             String value,
                             int targetTokens,
                             int currentTokens,
                             Consumer<String> setter,
                             ContextSection section) {
        if (value == null || currentTokens <= targetTokens) {
            return currentTokens;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        int textTokens = estimator.estimateTokens(value);
        int targetTextTokens = Math.max(0, textTokens - (currentTokens - targetTokens));
        String trimmed = trimTextByTokens(value, targetTextTokens);
        if (trimmed != null && trimmed.length() < value.length()) {
            recordRemoved(context, section, 1,
                    value.length() - trimmed.length(),
                    estimator.estimateTokensByChars(value.length() - trimmed.length()));
            setter.accept(trimmed.isBlank() ? null : trimmed);
            currentTokens = currentTokens - textTokens + estimator.estimateTokens(trimmed);
        }
        return currentTokens;
    }

    /**
     * 估算任务意图令牌（用于复用）。
     */
    public int estimateTaskIntentTokens(ContextTokenEstimator estimator, TaskIntent intent) {
        return estimator.estimateTaskIntentTokens(intent);
    }
}
