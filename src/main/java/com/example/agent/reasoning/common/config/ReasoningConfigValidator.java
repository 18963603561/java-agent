package com.example.agent.reasoning.common.config;

import com.example.agent.reasoning.thoughttree.ThoughtTreeConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理配置校验器。
 *
 * <p>用途：统一校验并规整推理参数，避免非法配置直接进入策略执行。
 */
@Component
public class ReasoningConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ReasoningConfigValidator.class);

    /**
     * 校验并规整 COT 问题长度。
     *
     * @param maxQuestionChars 最大问题长度
     * @return 规整结果
     */
    public int normalizeCotMaxQuestionChars(int maxQuestionChars) {
        if (maxQuestionChars <= 0) {
            log.warn("推理配置非法，使用默认值, field=maxQuestionChars, value={}", maxQuestionChars);
            return 500;
        }
        return maxQuestionChars;
    }

    /**
     * 校验并规整 COT 步骤摘要长度。
     *
     * @param maxStepSummaryChars 最大步骤摘要长度
     * @return 规整结果
     */
    public int normalizeCotStepSummaryChars(int maxStepSummaryChars) {
        if (maxStepSummaryChars <= 0) {
            log.warn("推理配置非法，使用默认值, field=maxStepSummaryChars, value={}", maxStepSummaryChars);
            return 200;
        }
        return maxStepSummaryChars;
    }

    /**
     * 校验并规整 Debate 结论长度。
     *
     * @param maxConclusionChars 最大结论长度
     * @return 规整结果
     */
    public int normalizeDebateMaxConclusionChars(int maxConclusionChars) {
        if (maxConclusionChars <= 0) {
            log.warn("推理配置非法，使用默认值, field=maxConclusionChars, value={}", maxConclusionChars);
            return 600;
        }
        return maxConclusionChars;
    }

    /**
     * 校验并规整推理执行线程池大小。
     *
     * @param poolSize 线程池大小
     * @return 规整结果
     */
    public int normalizeExecutorPoolSize(int poolSize) {
        if (poolSize <= 0) {
            log.warn("推理配置非法，使用默认值, field=executorPoolSize, value={}", poolSize);
            return 3;
        }
        if (poolSize > 16) {
            log.warn("推理配置超阈值，执行截断, field=executorPoolSize, value={}", poolSize);
            return 16;
        }
        return poolSize;
    }

    /**
     * 校验并规整并行执行超时时间。
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return 规整结果
     */
    public long normalizeParallelTimeoutMillis(long timeoutMillis) {
        if (timeoutMillis <= 0L) {
            log.warn("推理配置非法，使用默认值, field=parallelTimeoutMillis, value={}", timeoutMillis);
            return 4000L;
        }
        if (timeoutMillis > 60_000L) {
            log.warn("推理配置超阈值，执行截断, field=parallelTimeoutMillis, value={}", timeoutMillis);
            return 60_000L;
        }
        return timeoutMillis;
    }

    /**
     * 校验并规整复杂度阈值。
     *
     * @param threshold 复杂度阈值
     * @return 规整结果
     */
    public double normalizeComplexityThreshold(double threshold) {
        if (threshold <= 0 || threshold >= 1) {
            log.warn("推理配置非法，使用默认值, field=thoughtTreeComplexityThreshold, value={}", threshold);
            return 0.7;
        }
        return threshold;
    }

    /**
     * 校验并规整降级策略顺序。
     *
     * @param configuredOrder 配置顺序
     * @param fallbackOrder 默认顺序
     * @param fieldName 字段名
     * @return 规整顺序
     */
    public List<String> normalizeFallbackOrder(List<String> configuredOrder,
                                               List<String> fallbackOrder,
                                               String fieldName) {
        List<String> baseFallback = fallbackOrder == null || fallbackOrder.isEmpty()
                ? List.of("cot", "debate", "thought_tree")
                : fallbackOrder;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (configuredOrder != null) {
            for (String strategy : configuredOrder) {
                String normalizedStrategy = normalizeStrategy(strategy);
                if (normalizedStrategy != null) {
                    normalized.add(normalizedStrategy);
                }
            }
        }
        if (normalized.isEmpty()) {
            log.warn("推理配置非法，使用默认值, field={}, value={}", fieldName, configuredOrder);
            for (String strategy : baseFallback) {
                String normalizedStrategy = normalizeStrategy(strategy);
                if (normalizedStrategy != null) {
                    normalized.add(normalizedStrategy);
                }
            }
        }
        for (String strategy : baseFallback) {
            String normalizedStrategy = normalizeStrategy(strategy);
            if (normalizedStrategy != null) {
                normalized.add(normalizedStrategy);
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 校验并规整思维树配置。
     *
     * @param config 原始配置
     * @return 规整配置
     */
    public ThoughtTreeConfig normalizeThoughtTreeConfig(ThoughtTreeConfig config) {
        ThoughtTreeConfig safe = config == null ? new ThoughtTreeConfig() : config;
        if (safe.getMaxDepth() <= 0) {
            log.warn("推理配置非法，使用默认值, field=maxDepth, value={}", safe.getMaxDepth());
            safe.setMaxDepth(3);
        }
        if (safe.getBranchingFactor() <= 0) {
            log.warn("推理配置非法，使用默认值, field=branchingFactor, value={}", safe.getBranchingFactor());
            safe.setBranchingFactor(3);
        }
        if (safe.getBranchingFactor() > 4) {
            log.warn("推理配置超阈值，执行截断, field=branchingFactor, value={}", safe.getBranchingFactor());
            safe.setBranchingFactor(4);
        }
        if (safe.getExplorationBudget() <= 0) {
            log.warn("推理配置非法，使用默认值, field=explorationBudget, value={}", safe.getExplorationBudget());
            safe.setExplorationBudget(12);
        }
        if (safe.getPruningThreshold() <= 0) {
            log.warn("推理配置非法，使用默认值, field=pruningThreshold, value={}", safe.getPruningThreshold());
            safe.setPruningThreshold(0.3);
        }
        if (!StringUtils.hasText(safe.getEvaluationMethod())) {
            log.warn("推理配置非法，使用默认值, field=evaluationMethod, value={}", safe.getEvaluationMethod());
            safe.setEvaluationMethod("scoring");
        }
        return safe;
    }

    private String normalizeStrategy(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "chain_of_thought", "cot" -> "cot";
            case "debate" -> "debate";
            case "thought_tree" -> "thought_tree";
            default -> null;
        };
    }
}
