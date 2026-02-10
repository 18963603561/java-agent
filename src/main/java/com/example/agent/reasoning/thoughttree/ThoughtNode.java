package com.example.agent.reasoning.thoughttree;

import java.util.List;

/**
 * 思维节点，表示思维树中的单个推理节点。
 */
public class ThoughtNode {

    /**
     * 节点标识。
     */
    private String nodeId;

    /**
     * 节点内容，用于后续扩展与评分。
     */
    private String content;

    /**
     * 节点评分，表示该路径的可行性。
     */
    private double score;

    /**
     * 父节点标识，用于追溯路径。
     */
    private String parentId;

    /**
     * 当前节点深度。
     */
    private int depth;

    /**
     * 是否为终止节点。
     */
    private boolean terminal;

    /**
     * 节点说明信息，用于解释该分支用途。
     */
    private String explanation;

    /**
     * 估算 token 使用量。
     */
    private int tokensUsed;

    /**
     * 子节点集合。
     */
    private List<ThoughtNode> children;

    public ThoughtNode() {
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public List<ThoughtNode> getChildren() {
        return children;
    }

    public void setChildren(List<ThoughtNode> children) {
        this.children = children;
    }
}

