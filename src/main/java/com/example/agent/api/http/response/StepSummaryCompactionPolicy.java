package com.example.agent.api.http.response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 步骤摘要裁剪策略。
 * <p>用途：在 compact 模式下裁剪 steps[*].summary 的冗余字段，降低返回噪声。
 */
@Component
public class StepSummaryCompactionPolicy {

    /**
     * 执行步骤摘要裁剪。
     *
     * @param payload 结果负载
     * @return 裁剪结果与统计信息
     */
    public StepSummaryCompactionResult compact(Map<String, Object> payload) {
        if (payload == null) {
            return new StepSummaryCompactionResult(null, 0, 0);
        }
        Object stepsObj = payload.get("steps");
        if (!(stepsObj instanceof List<?> steps) || steps.isEmpty()) {
            return new StepSummaryCompactionResult(payload, 0, 0);
        }

        Map<String, Object> payloadCopy = new LinkedHashMap<>(payload);
        List<Object> compactedSteps = new ArrayList<>();
        int compactedSummaryCount = 0;
        int removedSummaryCount = 0;

        // 复杂循环：遍历每个步骤并按摘要规则裁剪，保证步骤间处理行为一致。
        for (Object stepObj : steps) {
            if (!(stepObj instanceof Map<?, ?> rawStepMap)) {
                compactedSteps.add(stepObj);
                continue;
            }
            Map<String, Object> stepMap = toStringKeyMap(rawStepMap);
            SummaryCompactionDecision decision = compactSummary(stepMap.get("summary"));
            if (decision.isChanged()) {
                compactedSummaryCount++;
            }
            // 核心流程分支：摘要不可用时移除节点，可用时只保留裁剪后的摘要。
            if (decision.isRemoved()) {
                stepMap.remove("summary");
                removedSummaryCount++;
            } else if (decision.summary() != null) {
                stepMap.put("summary", decision.summary());
            }
            compactedSteps.add(stepMap);
        }
        payloadCopy.put("steps", compactedSteps);
        return new StepSummaryCompactionResult(payloadCopy, compactedSummaryCount, removedSummaryCount);
    }

    private SummaryCompactionDecision compactSummary(Object summaryObj) {
        if (!(summaryObj instanceof Map<?, ?> rawSummaryMap)) {
            return SummaryCompactionDecision.noChange(null);
        }
        Map<String, Object> summaryMap = toStringKeyMap(rawSummaryMap);
        if (hasText(summaryMap.get("text"))) {
            return SummaryCompactionDecision.noChange(summaryMap);
        }

        List<String> highlights = resolveHighlights(summaryMap.get("highlights"));
        // 业务条件判断：无 text 时仅保留 highlights；若 highlights 为空则移除整个 summary。
        if (highlights.isEmpty()) {
            return SummaryCompactionDecision.removed();
        }
        Map<String, Object> compactedSummary = new LinkedHashMap<>();
        compactedSummary.put("highlights", highlights);
        return SummaryCompactionDecision.changedSummary(compactedSummary);
    }

    private List<String> resolveHighlights(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> highlights = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (StringUtils.hasText(text)) {
                highlights.add(text);
            }
        }
        return highlights;
    }

    private boolean hasText(Object value) {
        if (value == null) {
            return false;
        }
        return StringUtils.hasText(String.valueOf(value));
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    /**
     * 步骤摘要裁剪统计结果。
     */
    public static final class StepSummaryCompactionResult {

        private final Map<String, Object> payload;
        private final int compactedSummaryCount;
        private final int removedSummaryCount;

        StepSummaryCompactionResult(Map<String, Object> payload,
                                    int compactedSummaryCount,
                                    int removedSummaryCount) {
            this.payload = payload;
            this.compactedSummaryCount = compactedSummaryCount;
            this.removedSummaryCount = removedSummaryCount;
        }

        public Map<String, Object> payload() {
            return payload;
        }

        public int compactedSummaryCount() {
            return compactedSummaryCount;
        }

        public int removedSummaryCount() {
            return removedSummaryCount;
        }
    }

    private record SummaryCompactionDecision(Map<String, Object> summary, boolean changedFlag, boolean removedFlag) {

        private static SummaryCompactionDecision noChange(Map<String, Object> summary) {
            return new SummaryCompactionDecision(summary, false, false);
        }

        private static SummaryCompactionDecision changedSummary(Map<String, Object> summary) {
            return new SummaryCompactionDecision(summary, true, false);
        }

        private static SummaryCompactionDecision removed() {
            return new SummaryCompactionDecision(null, true, true);
        }

        private boolean isChanged() {
            return changedFlag;
        }

        private boolean isRemoved() {
            return removedFlag;
        }
    }
}
