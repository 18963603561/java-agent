package com.example.agent.reasoning.thoughttree;

/**
 * 思维树配置，用于控制扩展深度、分支数量与评估阈值。
 */
public class ThoughtTreeConfig {

    /**
     * 最大扩展深度，超过后视为终止节点。
     */
    private int maxDepth = 3;

    /**
     * 每个节点的分支数量，建议 2~4。
     */
    private int branchingFactor = 3;

    /**
     * 剪枝阈值，低于阈值的分支会被剔除。
     */
    private double pruningThreshold = 0.3;

    /**
     * 总探索预算，限制最多生成的思维节点数量。
     */
    private int explorationBudget = 12;

    /**
     * 是否启用回溯以尝试更优路径。
     */
    private boolean backtrackEnabled = true;

    /**
     * 评估方法标识，用于兼容未来评分策略。
     */
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

    public double getPruningThreshold() {
        return pruningThreshold;
    }

    public void setPruningThreshold(double pruningThreshold) {
        this.pruningThreshold = pruningThreshold;
    }

    public int getExplorationBudget() {
        return explorationBudget;
    }

    public void setExplorationBudget(int explorationBudget) {
        this.explorationBudget = explorationBudget;
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
