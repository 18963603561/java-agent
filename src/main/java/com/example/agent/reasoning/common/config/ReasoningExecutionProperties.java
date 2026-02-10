package com.example.agent.reasoning.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推理执行配置。
 *
 * <p>用途：统一管理推理层核心执行参数，避免策略实现散落默认值与魔法数。
 */
@Component
@ConfigurationProperties(prefix = "agent.reasoning")
public class ReasoningExecutionProperties {

    private Cot cot = new Cot();
    private Debate debate = new Debate();
    private ThoughtTree thoughtTree = new ThoughtTree();
    private Orchestrator orchestrator = new Orchestrator();
    private Selection selection = new Selection();

    public Cot getCot() {
        return cot;
    }

    public void setCot(Cot cot) {
        this.cot = cot;
    }

    public Debate getDebate() {
        return debate;
    }

    public void setDebate(Debate debate) {
        this.debate = debate;
    }

    public ThoughtTree getThoughtTree() {
        return thoughtTree;
    }

    public void setThoughtTree(ThoughtTree thoughtTree) {
        this.thoughtTree = thoughtTree;
    }

    public Orchestrator getOrchestrator() {
        return orchestrator;
    }

    public void setOrchestrator(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public Selection getSelection() {
        return selection;
    }

    public void setSelection(Selection selection) {
        this.selection = selection;
    }

    /**
     * COT 配置域。
     */
    public static class Cot {
        private int maxQuestionChars = 500;
        private int maxStepSummaryChars = 200;

        public int getMaxQuestionChars() {
            return maxQuestionChars;
        }

        public void setMaxQuestionChars(int maxQuestionChars) {
            this.maxQuestionChars = maxQuestionChars;
        }

        public int getMaxStepSummaryChars() {
            return maxStepSummaryChars;
        }

        public void setMaxStepSummaryChars(int maxStepSummaryChars) {
            this.maxStepSummaryChars = maxStepSummaryChars;
        }
    }

    /**
     * Debate 配置域。
     */
    public static class Debate {
        private int maxConclusionChars = 600;

        public int getMaxConclusionChars() {
            return maxConclusionChars;
        }

        public void setMaxConclusionChars(int maxConclusionChars) {
            this.maxConclusionChars = maxConclusionChars;
        }
    }

    /**
     * ThoughtTree 配置域。
     */
    public static class ThoughtTree {
        private int maxDepth = 3;
        private int branchingFactor = 3;
        private int explorationBudget = 12;
        private double pruningThreshold = 0.3;
        private boolean backtrackEnabled = true;
        private String evaluationMethod = "scoring";

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public int getBranchingFactor() {
            return branchingFactor;
        }

        public void setBranchingFactor(int branchingFactor) {
            this.branchingFactor = branchingFactor;
        }

        public int getExplorationBudget() {
            return explorationBudget;
        }

        public void setExplorationBudget(int explorationBudget) {
            this.explorationBudget = explorationBudget;
        }

        public double getPruningThreshold() {
            return pruningThreshold;
        }

        public void setPruningThreshold(double pruningThreshold) {
            this.pruningThreshold = pruningThreshold;
        }

        public boolean isBacktrackEnabled() {
            return backtrackEnabled;
        }

        public void setBacktrackEnabled(boolean backtrackEnabled) {
            this.backtrackEnabled = backtrackEnabled;
        }

        public String getEvaluationMethod() {
            return evaluationMethod;
        }

        public void setEvaluationMethod(String evaluationMethod) {
            this.evaluationMethod = evaluationMethod;
        }
    }

    /**
     * Orchestrator 配置域。
     */
    public static class Orchestrator {
        private int executorPoolSize = 3;
        private long parallelTimeoutMillis = 4000L;

        public int getExecutorPoolSize() {
            return executorPoolSize;
        }

        public void setExecutorPoolSize(int executorPoolSize) {
            this.executorPoolSize = executorPoolSize;
        }

        public long getParallelTimeoutMillis() {
            return parallelTimeoutMillis;
        }

        public void setParallelTimeoutMillis(long parallelTimeoutMillis) {
            this.parallelTimeoutMillis = parallelTimeoutMillis;
        }
    }

    /**
     * 策略选择与降级配置域。
     */
    public static class Selection {
        private double thoughtTreeComplexityThreshold = 0.7;
        private List<String> cotFallbackOrder = new ArrayList<>(List.of("cot", "debate", "thought_tree"));
        private List<String> debateFallbackOrder = new ArrayList<>(List.of("debate", "cot", "thought_tree"));
        private List<String> thoughtTreeFallbackOrder = new ArrayList<>(List.of("thought_tree", "cot", "debate"));
        private List<String> defaultFallbackOrder = new ArrayList<>(List.of("cot", "debate", "thought_tree"));

        public double getThoughtTreeComplexityThreshold() {
            return thoughtTreeComplexityThreshold;
        }

        public void setThoughtTreeComplexityThreshold(double thoughtTreeComplexityThreshold) {
            this.thoughtTreeComplexityThreshold = thoughtTreeComplexityThreshold;
        }

        public List<String> getCotFallbackOrder() {
            return cotFallbackOrder;
        }

        public void setCotFallbackOrder(List<String> cotFallbackOrder) {
            this.cotFallbackOrder = cotFallbackOrder;
        }

        public List<String> getDebateFallbackOrder() {
            return debateFallbackOrder;
        }

        public void setDebateFallbackOrder(List<String> debateFallbackOrder) {
            this.debateFallbackOrder = debateFallbackOrder;
        }

        public List<String> getThoughtTreeFallbackOrder() {
            return thoughtTreeFallbackOrder;
        }

        public void setThoughtTreeFallbackOrder(List<String> thoughtTreeFallbackOrder) {
            this.thoughtTreeFallbackOrder = thoughtTreeFallbackOrder;
        }

        public List<String> getDefaultFallbackOrder() {
            return defaultFallbackOrder;
        }

        public void setDefaultFallbackOrder(List<String> defaultFallbackOrder) {
            this.defaultFallbackOrder = defaultFallbackOrder;
        }
    }
}
