package com.example.agent.reasoning.thoughttree;

import java.util.List;

/**
 * 思维树结果，包含最佳路径、结论与统计信息。
 */
public class ThoughtTreeResult {

    /**
     * 最优路径节点序列。
     */
    private List<ThoughtNode> bestPath;

    /**
     * 最优路径合成的解决方案。
     */
    private String bestSolution;

    /**
     * 总节点数量。
     */
    private int totalThoughts;

    /**
     * 实际树深度。
     */
    private int treeDepth;

    /**
     * 估算的 token 使用总量。
     */
    private int totalTokens;

    /**
     * 结果置信度，范围 0~1。
     */
    private double confidence;

    /**
     * 思维树根节点。
     */
    private ThoughtNode root;

    public List<ThoughtNode> getBestPath() {
        return bestPath;
    }

    public void setBestPath(List<ThoughtNode> bestPath) {
        this.bestPath = bestPath;
    }

    public String getBestSolution() {
        return bestSolution;
    }

    public void setBestSolution(String bestSolution) {
        this.bestSolution = bestSolution;
    }

    public int getTotalThoughts() {
        return totalThoughts;
    }

    public void setTotalThoughts(int totalThoughts) {
        this.totalThoughts = totalThoughts;
    }

    public int getTreeDepth() {
        return treeDepth;
    }

    public void setTreeDepth(int treeDepth) {
        this.treeDepth = treeDepth;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public ThoughtNode getRoot() {
        return root;
    }

    public void setRoot(ThoughtNode root) {
        this.root = root;
    }
}
