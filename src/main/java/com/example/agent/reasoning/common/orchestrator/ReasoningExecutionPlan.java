package com.example.agent.reasoning.common.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 推理执行计划。
 *
 * <p>用途：定义并行推理的主策略、候选策略与超时限制。
 */
public class ReasoningExecutionPlan {

    private final String primaryStrategy;
    private final List<String> candidateStrategies;
    private final boolean parallelEnabled;
    private final long timeoutMillis;

    private ReasoningExecutionPlan(Builder builder) {
        this.primaryStrategy = normalizeStrategy(builder.primaryStrategy);
        this.candidateStrategies = normalizeCandidates(builder.candidateStrategies, this.primaryStrategy);
        this.parallelEnabled = builder.parallelEnabled;
        this.timeoutMillis = normalizeTimeout(builder.timeoutMillis);
    }

    public String getPrimaryStrategy() {
        return primaryStrategy;
    }

    public List<String> getCandidateStrategies() {
        return candidateStrategies;
    }

    public boolean isParallelEnabled() {
        return parallelEnabled;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 推理执行计划构建器。
     */
    public static class Builder {
        private String primaryStrategy;
        private List<String> candidateStrategies;
        private boolean parallelEnabled;
        private long timeoutMillis = 4000L;

        public Builder primaryStrategy(String primaryStrategy) {
            this.primaryStrategy = primaryStrategy;
            return this;
        }

        public Builder candidateStrategies(List<String> candidateStrategies) {
            this.candidateStrategies = candidateStrategies;
            return this;
        }

        public Builder parallelEnabled(boolean parallelEnabled) {
            this.parallelEnabled = parallelEnabled;
            return this;
        }

        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public ReasoningExecutionPlan build() {
            return new ReasoningExecutionPlan(this);
        }
    }

    private static List<String> normalizeCandidates(List<String> candidates, String primaryStrategy) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (StringUtils.hasText(primaryStrategy)) {
            normalized.add(primaryStrategy);
        }
        if (candidates != null) {
            for (String candidate : candidates) {
                String strategy = normalizeStrategy(candidate);
                if (strategy != null) {
                    normalized.add(strategy);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("cot");
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static String normalizeStrategy(String strategy) {
        if (!StringUtils.hasText(strategy)) {
            return null;
        }
        String normalized = strategy.trim().toLowerCase();
        return switch (normalized) {
            case "chain_of_thought", "cot" -> "cot";
            case "debate" -> "debate";
            case "thought_tree" -> "thought_tree";
            default -> null;
        };
    }

    private static long normalizeTimeout(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            return 4000L;
        }
        return timeoutMillis;
    }
}

