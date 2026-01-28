package com.example.agent.runtime;

import java.util.Map;

/**
 * 步骤执行请求，描述步骤输入与类型。
 */
public class StepRequest {

    private String stepType;
    private Map<String, Object> input;
    /**
     * 是否需要审批，空值表示未显式指定。
     */
    private Boolean requiresApproval;
    /**
     * 审批来源，用于标识 user/step/evaluation 等。
     */
    private String approvalSource;

    public StepRequest() {
    }

    public StepRequest(String stepType, Map<String, Object> input) {
        this.stepType = stepType;
        this.input = input;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getApprovalSource() {
        return approvalSource;
    }

    public void setApprovalSource(String approvalSource) {
        this.approvalSource = approvalSource;
    }
}
