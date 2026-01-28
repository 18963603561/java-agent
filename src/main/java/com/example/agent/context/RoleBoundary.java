package com.example.agent.context;

import java.util.List;

/**
 * 角色与边界信息。
 */
public class RoleBoundary {

    /**
     * 系统策略标识。
     */
    private String systemPolicyId;

    /**
     * 开发者策略标识。
     */
    private String developerPolicyId;

    /**
     * 禁止动作列表。
     */
    private List<String> forbiddenActions;

    /**
     * 数据访问范围。
     */
    private List<String> dataScopes;

    /**
     * 风险等级。
     */
    private String riskLevel;

    /**
     * 是否需要审批。
     */
    private Boolean approvalRequired;

    public String getSystemPolicyId() {
        return systemPolicyId;
    }

    public void setSystemPolicyId(String systemPolicyId) {
        this.systemPolicyId = systemPolicyId;
    }

    public String getDeveloperPolicyId() {
        return developerPolicyId;
    }

    public void setDeveloperPolicyId(String developerPolicyId) {
        this.developerPolicyId = developerPolicyId;
    }

    public List<String> getForbiddenActions() {
        return forbiddenActions;
    }

    public void setForbiddenActions(List<String> forbiddenActions) {
        this.forbiddenActions = forbiddenActions;
    }

    public List<String> getDataScopes() {
        return dataScopes;
    }

    public void setDataScopes(List<String> dataScopes) {
        this.dataScopes = dataScopes;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(Boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }
}