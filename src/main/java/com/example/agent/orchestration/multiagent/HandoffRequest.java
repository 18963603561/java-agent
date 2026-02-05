package com.example.agent.orchestration.multiagent;

import java.util.Map;

/**
 * 智能体交接请求。
 */
public class HandoffRequest {

    private String fromAgent;
    private String toAgent;
    private Map<String, Object> context;

    public HandoffRequest() {
    }

    public String getFromAgent() {
        return fromAgent;
    }

    public void setFromAgent(String fromAgent) {
        this.fromAgent = fromAgent;
    }

    public String getToAgent() {
        return toAgent;
    }

    public void setToAgent(String toAgent) {
        this.toAgent = toAgent;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}
