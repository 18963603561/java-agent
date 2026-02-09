package com.example.agent.governance.policy.domain;

import java.util.Map;

/**
 * 策略评估领域命令。
 */
public class PolicyEvaluationCommand {

    /**
     * 策略标识。
     */
    private String policyId;

    /**
     * 业务动作。
     */
    private String action;

    /**
     * 业务资源。
     */
    private String resource;

    /**
     * 评估输入。
     */
    private Map<String, Object> input;

    public PolicyEvaluationCommand() {
    }

    public PolicyEvaluationCommand(String policyId, String action, String resource, Map<String, Object> input) {
        this.policyId = policyId;
        this.action = action;
        this.resource = resource;
        this.input = input;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }
}

