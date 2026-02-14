package com.example.agent.api.http.response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 结果负载瘦身处理器。
 * <p>用途：在 compact 模式下统一执行去重、步骤摘要裁剪与递归去空。
 */
@Component
public class ResultPayloadCompactor {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(ResultPayloadCompactor.class);

    /**
     * 最终输出去重策略。
     */
    private final FinalOutputDedupPolicy finalOutputDedupPolicy;

    /**
     * 步骤摘要裁剪策略。
     */
    private final StepSummaryCompactionPolicy stepSummaryCompactionPolicy;

    public ResultPayloadCompactor(FinalOutputDedupPolicy finalOutputDedupPolicy,
                                  StepSummaryCompactionPolicy stepSummaryCompactionPolicy) {
        this.finalOutputDedupPolicy = finalOutputDedupPolicy;
        this.stepSummaryCompactionPolicy = stepSummaryCompactionPolicy;
    }

    /**
     * 执行结果负载瘦身。
     * <p>固定顺序：去重 -> 步骤摘要裁剪 -> 递归去空。
     *
     * @param payload 原始结果负载
     * @return 瘦身后的结果负载
     */
    public Map<String, Object> compact(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        FinalOutputDedupPolicy.FinalOutputDedupResult dedupResult = finalOutputDedupPolicy.deduplicateWithStats(payload);
        StepSummaryCompactionPolicy.StepSummaryCompactionResult summaryResult =
                stepSummaryCompactionPolicy.compact(dedupResult.payload());

        CompactStats compactStats = new CompactStats();
        Object compacted = compactValue(summaryResult.payload(), compactStats);
        if (!(compacted instanceof Map<?, ?> map)) {
            log.debug("结果瘦身完成, dedupRemoved={}, compactedStepSummary={}, removedStepSummary={}, prunedNodeCount={}, hasResult=false",
                    dedupResult.totalRemovedCount(),
                    summaryResult.compactedSummaryCount(),
                    summaryResult.removedSummaryCount(),
                    compactStats.prunedNodeCount());
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typedMap = (Map<String, Object>) map;
        log.debug("结果瘦身完成, dedupRemoved={}, compactedStepSummary={}, removedStepSummary={}, prunedNodeCount={}, hasResult=true",
                dedupResult.totalRemovedCount(),
                summaryResult.compactedSummaryCount(),
                summaryResult.removedSummaryCount(),
                compactStats.prunedNodeCount());
        return typedMap;
    }

    /**
     * 递归压缩对象。
     * <p>处理规则：移除 null、空数组、空对象；保留业务值。
     */
    private Object compactValue(Object value, CompactStats stats) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> compactedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    stats.addPrunedNode();
                    continue;
                }
                Object compactedValue = compactValue(entry.getValue(), stats);
                if (isRemovable(compactedValue)) {
                    stats.addPrunedNode();
                    continue;
                }
                compactedMap.put(String.valueOf(entry.getKey()), compactedValue);
            }
            return compactedMap.isEmpty() ? null : compactedMap;
        }
        if (value instanceof List<?> list) {
            List<Object> compactedList = new ArrayList<>();
            for (Object item : list) {
                Object compactedItem = compactValue(item, stats);
                if (isRemovable(compactedItem)) {
                    stats.addPrunedNode();
                    continue;
                }
                compactedList.add(compactedItem);
            }
            return compactedList.isEmpty() ? null : compactedList;
        }
        return value;
    }

    /**
     * 判断值是否应被移除。
     */
    private boolean isRemovable(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private static final class CompactStats {

        private int prunedNodeCount;

        private void addPrunedNode() {
            this.prunedNodeCount++;
        }

        private int prunedNodeCount() {
            return prunedNodeCount;
        }
    }
}
