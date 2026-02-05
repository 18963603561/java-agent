package com.example.agent.streaming.payload;

import java.util.List;

/**
 * 上下文快照摘要，用于事件载荷中的统计与边界信息。
 */
public class ContextSnapshotSummary {

    /**
     * 快照包含的上下文分段名称列表。
     */
    private List<String> sections;

    /**
     * 任务标识。
     */
    private String taskId;

    /**
     * 任务输入长度。
     */
    private Integer inputSize;

    /**
     * 约束条件数量。
     */
    private Integer constraintCount;

    /**
     * 是否需要审批。
     */
    private Boolean approvalRequired;

    /**
     * 风险等级标识。
     */
    private String riskLevel;

    /**
     * 工作记忆摘要长度。
     */
    private Integer workingSummarySize;

    /**
     * 关键事实数量。
     */
    private Integer keyFactCount;

    /**
     * 计划步骤数量。
     */
    private Integer planStepCount;

    /**
     * 证据条目数量。
     */
    private Integer evidenceCount;

    /**
     * 领域引用数量。
     */
    private Integer citationCount;

    /**
     * 长期记忆引用数量。
     */
    private Integer memoryCount;

    /**
     * 可用工具数量。
     */
    private Integer toolCount;

    /**
     * 已选工具数量。
     */
    private Integer selectedToolCount;

    /**
     * 最近工具调用数量。
     */
    private Integer recentToolCallCount;

    /**
     * 允许工具数量。
     */
    private Integer allowedToolCount;

    public List<String> getSections() {
        return sections;
    }

    public void setSections(List<String> sections) {
        this.sections = sections;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getInputSize() {
        return inputSize;
    }

    public void setInputSize(Integer inputSize) {
        this.inputSize = inputSize;
    }

    public Integer getConstraintCount() {
        return constraintCount;
    }

    public void setConstraintCount(Integer constraintCount) {
        this.constraintCount = constraintCount;
    }

    public Boolean getApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(Boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getWorkingSummarySize() {
        return workingSummarySize;
    }

    public void setWorkingSummarySize(Integer workingSummarySize) {
        this.workingSummarySize = workingSummarySize;
    }

    public Integer getKeyFactCount() {
        return keyFactCount;
    }

    public void setKeyFactCount(Integer keyFactCount) {
        this.keyFactCount = keyFactCount;
    }

    public Integer getPlanStepCount() {
        return planStepCount;
    }

    public void setPlanStepCount(Integer planStepCount) {
        this.planStepCount = planStepCount;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Integer getCitationCount() {
        return citationCount;
    }

    public void setCitationCount(Integer citationCount) {
        this.citationCount = citationCount;
    }

    public Integer getMemoryCount() {
        return memoryCount;
    }

    public void setMemoryCount(Integer memoryCount) {
        this.memoryCount = memoryCount;
    }

    public Integer getToolCount() {
        return toolCount;
    }

    public void setToolCount(Integer toolCount) {
        this.toolCount = toolCount;
    }

    public Integer getSelectedToolCount() {
        return selectedToolCount;
    }

    public void setSelectedToolCount(Integer selectedToolCount) {
        this.selectedToolCount = selectedToolCount;
    }

    public Integer getRecentToolCallCount() {
        return recentToolCallCount;
    }

    public void setRecentToolCallCount(Integer recentToolCallCount) {
        this.recentToolCallCount = recentToolCallCount;
    }

    public Integer getAllowedToolCount() {
        return allowedToolCount;
    }

    public void setAllowedToolCount(Integer allowedToolCount) {
        this.allowedToolCount = allowedToolCount;
    }
}
