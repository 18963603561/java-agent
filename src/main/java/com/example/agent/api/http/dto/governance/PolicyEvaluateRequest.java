package com.example.agent.api.http.dto.governance;

import java.util.Map;

/**
 * 策略评估接口请求。
 */
public class PolicyEvaluateRequest {

    private String policyId;
    private String action;
    private String resource;
    private Map<String, Object> input;

    public PolicyEvaluateRequest() {
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

