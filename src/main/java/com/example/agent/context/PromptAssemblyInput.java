package com.example.agent.context;

import com.example.agent.budget.ContextBudgetAllocation;
import java.util.List;
import java.util.Map;

/**
 * 提示词装配输入，承载三段式文本与预算信息。
 */
public class PromptAssemblyInput {

    /**
     * 系统提示词内容。
     */
    private String systemText;

    /**
     * 开发者提示词内容。
     */
    private String developerText;

    /**
     * 用户提示词内容。
     */
    private String userText;

    /**
     * 被裁剪的段落标识列表。
     */
    private List<String> truncatedSections;

    /**
     * 预算使用的 token 估算。
     */
    private Map<String, Integer> budgetUsedTokens;

    /**
     * 预算使用的字符数估算。
     */
    private Map<String, Integer> budgetUsedChars;

    /**
     * 策略版本号，可为空。
     */
    private String policyVersion;

    /**
     * 预算分配结果，可为空。
     */
    private ContextBudgetAllocation budgetAllocation;

    /**
     * 租户标识，可为空。
     */
    private String tenantId;

    /**
     * 工作流标识，可为空。
     */
    private String workflowId;

    public String getSystemText() {
        return systemText;
    }

    public void setSystemText(String systemText) {
        this.systemText = systemText;
    }

    public String getDeveloperText() {
        return developerText;
    }

    public void setDeveloperText(String developerText) {
        this.developerText = developerText;
    }

    public String getUserText() {
        return userText;
    }

    public void setUserText(String userText) {
        this.userText = userText;
    }

    public List<String> getTruncatedSections() {
        return truncatedSections;
    }

    public void setTruncatedSections(List<String> truncatedSections) {
        this.truncatedSections = truncatedSections;
    }

    public Map<String, Integer> getBudgetUsedTokens() {
        return budgetUsedTokens;
    }

    public void setBudgetUsedTokens(Map<String, Integer> budgetUsedTokens) {
        this.budgetUsedTokens = budgetUsedTokens;
    }

    public Map<String, Integer> getBudgetUsedChars() {
        return budgetUsedChars;
    }

    public void setBudgetUsedChars(Map<String, Integer> budgetUsedChars) {
        this.budgetUsedChars = budgetUsedChars;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public ContextBudgetAllocation getBudgetAllocation() {
        return budgetAllocation;
    }

    public void setBudgetAllocation(ContextBudgetAllocation budgetAllocation) {
        this.budgetAllocation = budgetAllocation;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
}
