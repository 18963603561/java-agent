package com.example.agent.api.http.response;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 最终输出去重策略。
 * <p>用途：在 compact 模式下消除对外响应中的重复字段，降低冗余。
 */
@Component
public class FinalOutputDedupPolicy {

    /**
     * 对结果负载执行去重策略。
     *
     * @param payload 原始结果负载
     * @return 去重后的结果负载副本
     */
    public Map<String, Object> deduplicate(Map<String, Object> payload) {
        return deduplicateWithStats(payload).payload();
    }

    /**
     * 对结果负载执行去重策略，并返回去重统计信息。
     *
     * @param payload 原始结果负载
     * @return 去重结果与统计
     */
    public FinalOutputDedupResult deduplicateWithStats(Map<String, Object> payload) {
        if (payload == null) {
            return new FinalOutputDedupResult(null, false, false, false);
        }
        Map<String, Object> deduplicated = new LinkedHashMap<>(payload);
        boolean removedPlanSummary = deduplicatePlanSummary(deduplicated);
        FinalAnswerDedupResult finalAnswerDedupResult = deduplicateFinalAnswer(deduplicated);
        return new FinalOutputDedupResult(
                deduplicated,
                removedPlanSummary,
                finalAnswerDedupResult.removedSummaryText(),
                finalAnswerDedupResult.removedSummaryNode()
        );
    }

    /**
     * 去重 planSummary：当顶层与 finalOutput.meta 中内容一致时，仅保留一处。
     */
    private boolean deduplicatePlanSummary(Map<String, Object> payload) {
        String rootPlanSummary = toText(payload.get("planSummary"));
        if (!StringUtils.hasText(rootPlanSummary)) {
            return false;
        }
        Map<String, Object> finalOutput = asMap(payload.get("finalOutput"));
        if (finalOutput == null) {
            return false;
        }
        Map<String, Object> meta = asMap(finalOutput.get("meta"));
        if (meta == null) {
            return false;
        }
        String nestedPlanSummary = toText(meta.get("planSummary"));
        if (StringUtils.hasText(nestedPlanSummary) && rootPlanSummary.equals(nestedPlanSummary)) {
            payload.remove("planSummary");
            return true;
        }
        return false;
    }

    /**
     * 去重答案文本：当 finalOutput.result.answer 与 finalOutput.summary.text 完全一致时，
     * 仅保留主路径 answer。
     */
    private FinalAnswerDedupResult deduplicateFinalAnswer(Map<String, Object> payload) {
        Map<String, Object> finalOutput = asMap(payload.get("finalOutput"));
        if (finalOutput == null) {
            return FinalAnswerDedupResult.none();
        }
        Map<String, Object> result = asMap(finalOutput.get("result"));
        Map<String, Object> summary = asMap(finalOutput.get("summary"));
        if (result == null || summary == null) {
            return FinalAnswerDedupResult.none();
        }
        String answer = toText(result.get("answer"));
        String summaryText = toText(summary.get("text"));
        if (!StringUtils.hasText(answer) || !StringUtils.hasText(summaryText) || !answer.equals(summaryText)) {
            return FinalAnswerDedupResult.none();
        }

        Map<String, Object> finalOutputCopy = new LinkedHashMap<>(finalOutput);
        Map<String, Object> summaryCopy = new LinkedHashMap<>(summary);
        summaryCopy.remove("text");
        if (isEmptySummary(summaryCopy)) {
            finalOutputCopy.remove("summary");
            payload.put("finalOutput", finalOutputCopy);
            return FinalAnswerDedupResult.summaryNodeRemoved();
        } else {
            finalOutputCopy.put("summary", summaryCopy);
            payload.put("finalOutput", finalOutputCopy);
            return FinalAnswerDedupResult.summaryTextRemoved();
        }
    }

    /**
     * 判断摘要是否仅剩空值信息，若为空可整体移除。
     */
    private boolean isEmptySummary(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            return true;
        }
        for (Object value : summary.values()) {
            if (value == null) {
                continue;
            }
            if (value instanceof CharSequence text && StringUtils.hasText(text)) {
                return false;
            }
            if (value instanceof Collection<?> collection && !collection.isEmpty()) {
                return false;
            }
            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                return false;
            }
            if (!(value instanceof CharSequence) && !(value instanceof Collection<?>) && !(value instanceof Map<?, ?>)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return (Map<String, Object>) map;
    }

    private String toText(Object value) {
        return value instanceof String text ? text : null;
    }

    /**
     * 最终输出去重结果。
     */
    public static final class FinalOutputDedupResult {

        private final Map<String, Object> payload;
        private final boolean removedPlanSummary;
        private final boolean removedSummaryText;
        private final boolean removedSummaryNode;

        private FinalOutputDedupResult(Map<String, Object> payload,
                                       boolean removedPlanSummary,
                                       boolean removedSummaryText,
                                       boolean removedSummaryNode) {
            this.payload = payload;
            this.removedPlanSummary = removedPlanSummary;
            this.removedSummaryText = removedSummaryText;
            this.removedSummaryNode = removedSummaryNode;
        }

        public Map<String, Object> payload() {
            return payload;
        }

        public boolean removedPlanSummary() {
            return removedPlanSummary;
        }

        public boolean removedSummaryText() {
            return removedSummaryText;
        }

        public boolean removedSummaryNode() {
            return removedSummaryNode;
        }

        public int totalRemovedCount() {
            int total = 0;
            if (removedPlanSummary) {
                total++;
            }
            if (removedSummaryText) {
                total++;
            }
            if (removedSummaryNode) {
                total++;
            }
            return total;
        }
    }

    private record FinalAnswerDedupResult(boolean removedSummaryText, boolean removedSummaryNode) {

        private static FinalAnswerDedupResult none() {
            return new FinalAnswerDedupResult(false, false);
        }

        private static FinalAnswerDedupResult summaryTextRemoved() {
            return new FinalAnswerDedupResult(true, false);
        }

        private static FinalAnswerDedupResult summaryNodeRemoved() {
            return new FinalAnswerDedupResult(false, true);
        }
    }
}
