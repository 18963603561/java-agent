package com.example.agent.governance.evaluation;

import java.util.List;

/**
 * 能力边界评估输入，描述任务与上下文摘要。
 */
public class CapabilityEvaluationInput {

    /**
     * 任务描述或问题文本。
     */
    private String taskDescription;

    /**
     * 规划摘要，用于补充上下文。
     */
    private String planSummary;

    /**
     * 评估场景。
     */
    private String scene;

    /**
     * 工具清单摘要。
     */
    private String toolSummary;

    /**
     * 预算阈值。
     */
    private int budgetThresholdTokens;

    /**
     * 历史失败类型。
     */
    private List<String> failureTypes;

    /**
     * 复杂度评分。
     */
    private Double complexityScore;

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getPlanSummary() {
        return planSummary;
    }

    public void setPlanSummary(String planSummary) {
        this.planSummary = planSummary;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getToolSummary() {
        return toolSummary;
    }

    public void setToolSummary(String toolSummary) {
        this.toolSummary = toolSummary;
    }

    public int getBudgetThresholdTokens() {
        return budgetThresholdTokens;
    }

    public void setBudgetThresholdTokens(int budgetThresholdTokens) {
        this.budgetThresholdTokens = budgetThresholdTokens;
    }

    public List<String> getFailureTypes() {
        return failureTypes;
    }

    public void setFailureTypes(List<String> failureTypes) {
        this.failureTypes = failureTypes;
    }

    public Double getComplexityScore() {
        return complexityScore;
    }

    public void setComplexityScore(Double complexityScore) {
        this.complexityScore = complexityScore;
    }
}
