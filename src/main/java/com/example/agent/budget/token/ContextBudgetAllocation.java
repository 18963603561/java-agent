package com.example.agent.budget.token;

import java.util.Map;
import com.example.agent.budget.trim.ContextSection;

/**
 * 上下文预算分配结果。
 */
public class ContextBudgetAllocation {

    /**
     * 预算版本号，默认 v1。
     */
    private String version = "v1";

    /**
     * 总令牌预算。
     */
    private Integer totalTokens;

    /**
     * 预留令牌预算。
     */
    private Integer reservedTokens;

    /**
     * 系统与开发者预算令牌数，由系统与开发者分段汇总。
     */
    private Integer systemAndDeveloperBudget;

    /**
     * 任务意图预算令牌数，对应任务意图分段。
     */
    private Integer taskIntentBudget;

    /**
     * 工作记忆预算令牌数，对应工作记忆分段。
     */
    private Integer workingMemoryBudget;

    /**
     * 召回记忆预算令牌数，由领域知识与长期记忆分段汇总。
     */
    private Integer recalledMemoriesBudget;

    /**
     * 证据包预算令牌数，对应证据包分段。
     */
    private Integer evidencePackBudget;

    /**
     * 工具摘要预算令牌数，对应工具摘要分段。
     */
    private Integer toolSummariesBudget;

    /**
     * 工具模式预算令牌数，对应工具模式分段。
     */
    private Integer toolSchemaBudget;

    /**
     * 余量预算令牌数，对应余量分段。
     */
    private Integer slackBudget;

    /**
     * 分段预算。
     */
    private Map<ContextSection, Integer> sectionTokens;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getReservedTokens() {
        return reservedTokens;
    }

    public void setReservedTokens(Integer reservedTokens) {
        this.reservedTokens = reservedTokens;
    }

    public Integer getSystemAndDeveloperBudget() {
        return systemAndDeveloperBudget;
    }

    public void setSystemAndDeveloperBudget(Integer systemAndDeveloperBudget) {
        this.systemAndDeveloperBudget = systemAndDeveloperBudget;
    }

    public Integer getTaskIntentBudget() {
        return taskIntentBudget;
    }

    public void setTaskIntentBudget(Integer taskIntentBudget) {
        this.taskIntentBudget = taskIntentBudget;
    }

    public Integer getWorkingMemoryBudget() {
        return workingMemoryBudget;
    }

    public void setWorkingMemoryBudget(Integer workingMemoryBudget) {
        this.workingMemoryBudget = workingMemoryBudget;
    }

    public Integer getRecalledMemoriesBudget() {
        return recalledMemoriesBudget;
    }

    public void setRecalledMemoriesBudget(Integer recalledMemoriesBudget) {
        this.recalledMemoriesBudget = recalledMemoriesBudget;
    }

    public Integer getEvidencePackBudget() {
        return evidencePackBudget;
    }

    public void setEvidencePackBudget(Integer evidencePackBudget) {
        this.evidencePackBudget = evidencePackBudget;
    }

    public Integer getToolSummariesBudget() {
        return toolSummariesBudget;
    }

    public void setToolSummariesBudget(Integer toolSummariesBudget) {
        this.toolSummariesBudget = toolSummariesBudget;
    }

    public Integer getToolSchemaBudget() {
        return toolSchemaBudget;
    }

    public void setToolSchemaBudget(Integer toolSchemaBudget) {
        this.toolSchemaBudget = toolSchemaBudget;
    }

    public Integer getSlackBudget() {
        return slackBudget;
    }

    public void setSlackBudget(Integer slackBudget) {
        this.slackBudget = slackBudget;
    }

    public Map<ContextSection, Integer> getSectionTokens() {
        return sectionTokens;
    }

    public void setSectionTokens(Map<ContextSection, Integer> sectionTokens) {
        this.sectionTokens = sectionTokens;
        refreshDerivedBudgets(sectionTokens);
    }

    /**
     * 根据分段预算刷新派生预算字段。
     *
     * @param sectionTokens 分段预算
     */
    public void refreshDerivedBudgets(Map<ContextSection, Integer> sectionTokens) {
        if (sectionTokens == null || sectionTokens.isEmpty()) {
            systemAndDeveloperBudget = 0;
            taskIntentBudget = 0;
            workingMemoryBudget = 0;
            recalledMemoriesBudget = 0;
            evidencePackBudget = 0;
            toolSummariesBudget = 0;
            toolSchemaBudget = 0;
            slackBudget = 0;
            return;
        }
        int systemPolicy = resolveSectionTokens(sectionTokens, ContextSection.SYSTEM_POLICY);
        int developerPolicy = resolveSectionTokens(sectionTokens, ContextSection.DEVELOPER_POLICY);
        systemAndDeveloperBudget = systemPolicy + developerPolicy;
        taskIntentBudget = resolveSectionTokens(sectionTokens, ContextSection.USER_INPUT);
        workingMemoryBudget = resolveSectionTokens(sectionTokens, ContextSection.WORKING_MEMORY);
        recalledMemoriesBudget = resolveSectionTokens(sectionTokens, ContextSection.DOMAIN_KNOWLEDGE)
                + resolveSectionTokens(sectionTokens, ContextSection.LONG_TERM_MEMORY);
        evidencePackBudget = resolveSectionTokens(sectionTokens, ContextSection.EVIDENCE_PACK);
        toolSummariesBudget = resolveSectionTokens(sectionTokens, ContextSection.TOOL_SUMMARY);
        toolSchemaBudget = resolveSectionTokens(sectionTokens, ContextSection.TOOL_SCHEMA);
        slackBudget = resolveSectionTokens(sectionTokens, ContextSection.SLACK);
    }

    private int resolveSectionTokens(Map<ContextSection, Integer> sectionTokens, ContextSection section) {
        if (sectionTokens == null || section == null) {
            return 0;
        }
        Integer value = sectionTokens.get(section);
        return value == null ? 0 : value;
    }
}
