package com.example.agent.context;

import java.util.List;

/**
 * 工作记忆内容。
 */
public class WorkingMemory {

    /**
     * 摘要信息。
     */
    private String summary;

    /**
     * 关键事实列表。
     */
    private List<String> keyFacts;

    /**
     * 计划步骤列表。
     */
    private List<String> planSteps;

    /**
     * 下一步建议。
     */
    private String nextStep;

    /**
     * 近期工具调用记录。
     */
    private List<ToolCallState> recentToolCalls;

    /**
     * 证据包。
     */
    private EvidencePack evidencePack;

    /**
     * 结构化摘要版本，空表示未使用结构化摘要。
     */
    private String summaryVersion;

    /**
     * 摘要字符数，用于事件统计与预算参考。
     */
    private Integer summaryChars;

    /**
     * 工作记忆条目数，用于事件统计与审计。
     */
    private Integer workingMemoryItems;

    /**
     * 是否使用结构化摘要填充工作记忆。
     */
    private Boolean usedStructuredSummary;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public void setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
    }

    public List<String> getPlanSteps() {
        return planSteps;
    }

    public void setPlanSteps(List<String> planSteps) {
        this.planSteps = planSteps;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }

    public List<ToolCallState> getRecentToolCalls() {
        return recentToolCalls;
    }

    public void setRecentToolCalls(List<ToolCallState> recentToolCalls) {
        this.recentToolCalls = recentToolCalls;
    }

    public EvidencePack getEvidencePack() {
        return evidencePack;
    }

    public void setEvidencePack(EvidencePack evidencePack) {
        this.evidencePack = evidencePack;
    }

    public String getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(String summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public Integer getSummaryChars() {
        return summaryChars;
    }

    public void setSummaryChars(Integer summaryChars) {
        this.summaryChars = summaryChars;
    }

    public Integer getWorkingMemoryItems() {
        return workingMemoryItems;
    }

    public void setWorkingMemoryItems(Integer workingMemoryItems) {
        this.workingMemoryItems = workingMemoryItems;
    }

    public Boolean getUsedStructuredSummary() {
        return usedStructuredSummary;
    }

    public void setUsedStructuredSummary(Boolean usedStructuredSummary) {
        this.usedStructuredSummary = usedStructuredSummary;
    }
}
