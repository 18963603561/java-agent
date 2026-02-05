package com.example.agent.orchestration.multiagent;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能体档案配置集合。
 */
@Component
@ConfigurationProperties(prefix = "agent.profiles")
public class AgentProfileProperties {

    private List<AgentProfile> items = new ArrayList<>();

    public List<AgentProfile> getItems() {
        return items;
    }

    public void setItems(List<AgentProfile> items) {
        this.items = items;
    }
}
